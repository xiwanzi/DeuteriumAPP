package com.deuterium.backend

import com.deuterium.backend.bridge.WebSocketPluginBridge
import com.deuterium.backend.chat.AppChatHub
import com.deuterium.backend.config.AppConfig
import com.deuterium.backend.repository.AccountRepository
import com.deuterium.backend.repository.ChatRepository
import com.deuterium.backend.repository.LoginFailureRepository
import com.deuterium.backend.repository.MaintenanceRepository
import com.deuterium.backend.repository.PlayerRefRepository
import com.deuterium.backend.repository.SessionRepository
import com.deuterium.backend.repository.VerificationRepository
import com.deuterium.backend.repository.WalletRepository
import com.deuterium.backend.util.PasswordHasher
import kotlinx.serialization.json.Json

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
) {
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
            val chatHub = AppChatHub(json)
            val chat = ChatRepository(playerRefs, accounts)
            val bridge = WebSocketPluginBridge(
                config = config.pluginBridge,
                json = json,
                chatRepository = chat,
                playerRefs = playerRefs,
                accounts = accounts,
                wallet = wallet,
                chatHub = chatHub
            )
            return ApplicationServices(
                config = config,
                json = json,
                passwordHasher = PasswordHasher(),
                accounts = accounts,
                sessions = SessionRepository(config.security.sessionDays),
                verifications = VerificationRepository(),
                loginFailures = LoginFailureRepository(),
                maintenance = MaintenanceRepository(config.security.sessionDays, config.chat.historyRetentionDays),
                playerRefs = playerRefs,
                wallet = wallet,
                chat = chat,
                chatHub = chatHub,
                bridge = bridge
            )
        }
    }
}
