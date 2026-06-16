package com.deuterium.backend

import com.deuterium.backend.bridge.WebSocketPluginBridge
import com.deuterium.backend.bridge.AppChatRequest
import com.deuterium.backend.ai.AiService
import com.deuterium.backend.chat.AppChatHub
import com.deuterium.backend.config.AppConfig
import com.deuterium.backend.model.ChatMessage
import com.deuterium.backend.oidc.OidcJwtSigner
import com.deuterium.backend.oidc.OidcService
import com.deuterium.backend.oidc.OidcSigningKeyStore
import com.deuterium.backend.repository.AccountRepository
import com.deuterium.backend.repository.AiRepository
import com.deuterium.backend.repository.ChatRepository
import com.deuterium.backend.repository.LoginFailureRepository
import com.deuterium.backend.repository.MaintenanceRepository
import com.deuterium.backend.repository.OidcRepository
import com.deuterium.backend.repository.PlayerRefRepository
import com.deuterium.backend.repository.SessionRepository
import com.deuterium.backend.repository.VerificationRepository
import com.deuterium.backend.repository.WalletRepository
import com.deuterium.backend.util.Ids
import com.deuterium.backend.util.PasswordHasher
import com.deuterium.backend.web.ApiException
import com.deuterium.backend.web.dbQuery
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

private const val PublicChatAiName = "客服小祥"
private const val PublicChatAiServerUuid = "virtual:ai-customer-service"
private val PublicChatAiTriggerRegex = Regex("""[@＠]\s*客服小祥""")

class ApplicationServices(
    val config: AppConfig,
    val json: Json,
    val passwordHasher: PasswordHasher,
    val accounts: AccountRepository,
    val sessions: SessionRepository,
    val verifications: VerificationRepository,
    val loginFailures: LoginFailureRepository,
    val maintenance: MaintenanceRepository,
    val playerRefs: PlayerRefRepository,
    val wallet: WalletRepository,
    val chat: ChatRepository,
    val chatHub: AppChatHub,
    val bridge: WebSocketPluginBridge,
    val aiRepository: AiRepository,
    val ai: AiService,
    val oidc: OidcService?,
) {
    private val publicChatGlobalLimiter = InMemoryWindowLimiter(maxEvents = 8, window = Duration.ofMinutes(3))
    private val publicChatSenderLimiter = InMemoryWindowLimiter(maxEvents = 3, window = Duration.ofMinutes(3))

    suspend fun maybeReplyToPublicChatAi(message: ChatMessage) {
        if (message.sender.gameId.equals(PublicChatAiName, ignoreCase = true)) return
        val prompt = publicChatAiPrompt(message.content) ?: return
        val now = Instant.now()
        if (!publicChatGlobalLimiter.allow("global", now) || !publicChatSenderLimiter.allow(message.sender.gameId, now)) {
            dbQuery {
                aiRepository.audit(
                    userId = null,
                    channel = "public_chat",
                    requestText = "sender=${message.sender.gameId}\n$prompt",
                    responseText = null,
                    riskCode = "public_chat_rate_limited",
                    status = "blocked",
                    model = null,
                    tokenEstimate = null
                )
            }
            return
        }
        val reply = try {
            ai.sendPublicChatMessage(message.sender.gameId, prompt)
        } catch (e: ApiException) {
            dbQuery {
                aiRepository.audit(
                    userId = null,
                    channel = "public_chat",
                    requestText = "sender=${message.sender.gameId}\n$prompt",
                    responseText = null,
                    riskCode = e.code,
                    status = "blocked",
                    model = null,
                    tokenEstimate = null
                )
            }
            return
        }
        val messageId = Ids.messageId()
        val result = runCatching {
            bridge.sendAppChat(
                AppChatRequest(
                    appMessageId = messageId,
                    senderServerUuid = PublicChatAiServerUuid,
                    senderGameId = PublicChatAiName,
                    content = reply.take(256),
                    allowVirtualSender = true
                )
            )
        }.getOrNull()
        if (result?.status != "sent") return
        val stored = dbQuery {
            chat.insertChatMessage(
                messageId = messageId,
                serverUuid = PublicChatAiServerUuid,
                gameId = PublicChatAiName,
                content = reply.take(256),
                sentAt = Instant.now()
            )
        }
        chatHub.broadcastMessage(stored)
    }

    companion object {
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        fun create(config: AppConfig): ApplicationServices {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            }
            val accounts = AccountRepository()
            val playerRefs = PlayerRefRepository(accounts)
            val wallet = WalletRepository(playerRefs)
            val aiRepository = AiRepository(config.ai.quotaWindowHours)
            val chatHub = AppChatHub(json)
            val chat = ChatRepository(playerRefs, accounts)
            val passwordHasher = PasswordHasher()
            val bridge = WebSocketPluginBridge(
                config = config.pluginBridge,
                json = json,
                chatRepository = chat,
                playerRefs = playerRefs,
                accounts = accounts,
                wallet = wallet,
                chatHub = chatHub
            )
            val aiService = AiService(config.ai, aiRepository, json)
            val loginFailures = LoginFailureRepository()
            val oidcService = if (config.oidc.enabled) {
                val signingKey = OidcSigningKeyStore(Path.of(config.oidc.signingKeyPath), json).loadOrCreate()
                OidcService(
                    config = config,
                    passwordHasher = passwordHasher,
                    accounts = accounts,
                    loginFailures = loginFailures,
                    oidcRepository = OidcRepository(),
                    jwtSigner = OidcJwtSigner(signingKey, json)
                )
            } else {
                null
            }
            val services = ApplicationServices(
                config = config,
                json = json,
                passwordHasher = passwordHasher,
                accounts = accounts,
                sessions = SessionRepository(config.security.sessionDays),
                verifications = VerificationRepository(),
                loginFailures = loginFailures,
                maintenance = MaintenanceRepository(config.security.sessionDays, config.chat.historyRetentionDays),
                playerRefs = playerRefs,
                wallet = wallet,
                chat = chat,
                chatHub = chatHub,
                bridge = bridge,
                aiRepository = aiRepository,
                ai = aiService,
                oidc = oidcService
            )
            bridge.onServerChatMessage = { message -> services.maybeReplyToPublicChatAi(message) }
            return services
        }
    }
}

internal fun publicChatAiPrompt(content: String): String? {
    val match = PublicChatAiTriggerRegex.find(content) ?: return null
    val prompt = content.removeRange(match.range).trim()
    return prompt.ifBlank { "我在，有什么可以帮你？" }
}

private class InMemoryWindowLimiter(
    private val maxEvents: Int,
    private val window: Duration,
) {
    private val eventsByKey = mutableMapOf<String, MutableList<Instant>>()

    @Synchronized
    fun allow(key: String, now: Instant): Boolean {
        val cutoff = now.minus(window)
        val events = eventsByKey.getOrPut(key) { mutableListOf() }
        events.removeIf { it.isBefore(cutoff) }
        if (events.size >= maxEvents) return false
        events += now
        return true
    }
}
