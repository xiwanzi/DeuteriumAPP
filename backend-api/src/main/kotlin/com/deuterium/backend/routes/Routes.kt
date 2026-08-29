package com.deuterium.backend.routes

import com.deuterium.backend.ApplicationServices
import com.deuterium.backend.bridge.AppChatRequest
import com.deuterium.backend.bridge.WalletDebitRequest
import com.deuterium.backend.bridge.WalletTransferRequest
import com.deuterium.backend.chat.AppStatePayload
import com.deuterium.backend.chat.AppWsEnvelope
import com.deuterium.backend.chat.SendResultPayload
import com.deuterium.backend.chat.WsErrorPayload
import com.deuterium.backend.model.AppUpdateCheckData
import com.deuterium.backend.model.AiChatStreamRequest
import com.deuterium.backend.model.AiConversationResetData
import com.deuterium.backend.model.AiMeData
import com.deuterium.backend.model.AiMessagesData
import com.deuterium.backend.model.AiPlansData
import com.deuterium.backend.model.AiPurchaseData
import com.deuterium.backend.model.AiPurchaseRequest
import com.deuterium.backend.model.AuthData
import com.deuterium.backend.model.ChatMessagesData
import com.deuterium.backend.model.ChatSendPayload
import com.deuterium.backend.model.CreateTransferRequest
import com.deuterium.backend.model.CurrentUser
import com.deuterium.backend.model.LoginRequest
import com.deuterium.backend.model.LogoutData
import com.deuterium.backend.model.LiveHealthData
import com.deuterium.backend.model.OnlinePlayer
import com.deuterium.backend.model.OnlinePlayersData
import com.deuterium.backend.model.Page
import com.deuterium.backend.model.PasswordResetCodeRequest
import com.deuterium.backend.model.PasswordResetData
import com.deuterium.backend.model.PasswordResetRequest
import com.deuterium.backend.model.PlayerDirectoryData
import com.deuterium.backend.model.PlayerFollowData
import com.deuterium.backend.model.PlayerFollowRequest
import com.deuterium.backend.model.PresenceData
import com.deuterium.backend.model.RecipientSearchData
import com.deuterium.backend.model.RegisterRequest
import com.deuterium.backend.model.RegistrationCodeRequest
import com.deuterium.backend.model.ReadyHealthData
import com.deuterium.backend.model.TransferData
import com.deuterium.backend.model.UserProfileData
import com.deuterium.backend.model.VerificationTokenData
import com.deuterium.backend.model.WalletBalance
import com.deuterium.backend.model.WalletBalanceData
import com.deuterium.backend.model.WalletRecordsData
import com.deuterium.backend.model.iso
import com.deuterium.backend.model.money
import com.deuterium.backend.oidc.installOidcRoutes
import com.deuterium.backend.repository.AiPlanRecord
import com.deuterium.backend.repository.AiQqGroupRecord
import com.deuterium.backend.repository.AiPromptRecord
import com.deuterium.backend.repository.AiKnowledgeRecord
import com.deuterium.backend.repository.AiAdminMessageRecord
import com.deuterium.backend.repository.AiAuditRecord
import com.deuterium.backend.repository.AiMemoryRecord
import com.deuterium.backend.repository.AiModelConfigRecord
import com.deuterium.backend.repository.AiPurchaseRecord
import com.deuterium.backend.util.Ids
import com.deuterium.backend.util.Secrets
import com.deuterium.backend.util.Validation
import com.deuterium.backend.web.ApiException
import com.deuterium.backend.web.PluginBridgeTimeout
import com.deuterium.backend.web.PluginBridgeUnavailable
import com.deuterium.backend.web.bearerToken
import com.deuterium.backend.web.currentUser
import com.deuterium.backend.web.dbQuery
import com.deuterium.backend.web.ok
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Routing
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.encodeToString
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val PresenceRefreshStaleMillis = 15_000L
private const val PresenceRefreshMinIntervalMillis = 5_000L
private val presenceRefreshMutex = Mutex()
private var lastPresenceRefreshStartedAt: Instant? = null
private val oneBotDedupeMutex = Mutex()
private val oneBotSeenMessages = linkedMapOf<String, Instant>()
private val oneBotSendMutex = Mutex()
private val qqGroupRateMutex = Mutex()
private val qqGroupRateWindows = mutableMapOf<String, QqGroupRateWindow>()
private val qqGroupInFlight = mutableSetOf<String>()
private val aiPurchaseLocksMutex = Mutex()
private val aiPurchaseLocks = mutableMapOf<String, Mutex>()
private const val QqPublicAssistantName = "客服小祥"

private data class ResolvedQqGroupConfig(
    val groupId: String,
    val enabled: Boolean,
    val triggerPattern: String,
    val quotaPerWindow: Int,
    val windowMinutes: Int,
    val mutedUntil: Instant?,
)

private data class QqGroupRateWindow(
    var startedAt: Instant,
    var used: Int,
)

fun Routing.installRoutes(services: ApplicationServices) {
    healthRoutes(services)
    pluginBridgeRoutes(services)
    route("/api/v1") {
        appRoutes(services)
        accountRoutes(services)
        walletRoutes(services)
        chatRoutes(services)
        aiRoutes(services)
    }
}

fun Routing.installPublicRoutes(services: ApplicationServices) {
    healthRoutes(services)
    installOidcRoutes(services)
    route("/api/v1") {
        appRoutes(services)
        accountRoutes(services)
        walletRoutes(services)
        chatRoutes(services)
        aiRoutes(services)
    }
}

fun Routing.installBridgeRoutes(services: ApplicationServices) {
    pluginBridgeRoutes(services)
    oneBotRoutes(services)
    aiAdminRoutes(services)
}

private fun Routing.healthRoutes(services: ApplicationServices) {
    get("/health/live") {
        call.ok(LiveHealthData(alive = true))
    }
    get("/health/ready") {
        call.ok(ReadyHealthData(ready = services.bridge.isAvailable()))
    }
}

private fun Routing.pluginBridgeRoutes(services: ApplicationServices) {
    webSocket("/bridge/plugin/ws") {
        val header = call.request.headers[HttpHeaders.Authorization].orEmpty()
        val token = header.removePrefix("Bearer").trim()
        if (token != services.config.pluginBridge.token) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid bridge token"))
            return@webSocket
        }
        services.bridge.accept(this)
    }
}

private fun Route.appRoutes(services: ApplicationServices) {
    get("/app/update-check") {
        val versionCode = call.request.queryParameters["versionCode"]?.toIntOrNull() ?: 0
        val latest = versionCode >= services.config.app.latestVersionCode
        call.ok(
            AppUpdateCheckData(
                latest = latest,
                message = if (latest) "当前已是最新版本" else "版本已过时，请更新最新版本",
                latestVersionCode = services.config.app.latestVersionCode,
                latestVersionName = services.config.app.latestVersionName
            )
        )
    }
}

private fun Route.accountRoutes(services: ApplicationServices) {
    post("/account/registration-code") {
        val body = call.receive<RegistrationCodeRequest>()
        val gameId = Validation.gameId(body.gameId)
        val qq = Validation.qq(body.qq)
        Validation.password(body.password)
        val now = Instant.now()
        val cooldown = dbQuery {
            if (services.accounts.findByQq(qq) != null) {
                throw ApiException("QQ_ALREADY_USED", "QQ 号已被使用。", 409)
            }
            services.verifications.latestActiveCooldown("registration", gameId)
        }
        if (cooldown != null && cooldown.isAfter(now)) {
            throw ApiException("VERIFICATION_COOLDOWN", "验证码发送太频繁，请稍后再试。", 429, ChronoUnit.SECONDS.between(now, cooldown))
        }
        val code = Secrets.sixDigitCode()
        val delivery = mapBridge {
            services.bridge.deliverVerification(Ids.verificationId(), "registration", gameId, code, now.plus(10, ChronoUnit.MINUTES))
        }
        when (delivery.status) {
            "delivered" -> Unit
            "player_offline" -> throw ApiException("PLAYER_NOT_ONLINE", "玩家当前不在线，请先进入 Deuterium VIII 服务器。", 409)
            "player_not_found" -> throw ApiException("PLAYER_NOT_FOUND", "未找到该游戏内 ID。", 404)
            "identity_conflict" -> throw ApiException("PLAYER_IDENTITY_CONFLICT", "玩家身份无法确认，需要管理员处理。", 409)
            else -> throw ApiException("SERVER_UNAVAILABLE", "验证码发送失败，请稍后再试。", 503)
        }
        val serverUuid = delivery.serverUuid ?: throw ApiException("PLAYER_IDENTITY_CONFLICT", "玩家身份无法确认，需要管理员处理。", 409)
        val currentGameId = delivery.currentGameId ?: gameId
        val verificationToken = Ids.token("ver")
        val expiresAt = dbQuery {
            if (services.accounts.findByServerUuid(serverUuid) != null) {
                throw ApiException("UUID_ALREADY_REGISTERED", "该玩家已经注册，请直接登录或重设密码。", 409)
            }
            if (services.accounts.findByQq(qq) != null) {
                throw ApiException("QQ_ALREADY_USED", "QQ 号已被使用。", 409)
            }
            services.verifications.expirePrevious("registration", currentGameId)
            services.verifications.create(
                tokenHash = Secrets.sha256(verificationToken, services.config.security.verificationPepper),
                purpose = "registration",
                serverUuid = serverUuid,
                gameId = currentGameId,
                qq = qq,
                codeHash = Secrets.sha256(code, services.config.security.verificationPepper)
            )
            Instant.now().plus(10, ChronoUnit.MINUTES)
        }
        call.ok(VerificationTokenData(verificationToken, expiresAt.iso(), 60))
    }

    post("/account/register") {
        val body = call.receive<RegisterRequest>()
        val code = Validation.code(body.code)
        val password = Validation.password(body.password)
        val tokenHash = Secrets.sha256(body.verificationToken, services.config.security.verificationPepper)
        val result = dbQuery {
            val verification = services.verifications.findByTokenHash(tokenHash, "registration")
                ?: throw ApiException("VERIFICATION_INVALID", "验证码错误或已失效。", 422)
            validateVerification(services, verification, code)
            if (services.accounts.findByServerUuid(verification.serverUuid) != null) {
                throw ApiException("UUID_ALREADY_REGISTERED", "该玩家已经注册，请直接登录或重设密码。", 409)
            }
            val qq = verification.qq ?: throw ApiException("VERIFICATION_INVALID", "验证码错误或已失效。", 422)
            if (services.accounts.findByQq(qq) != null) {
                throw ApiException("QQ_ALREADY_USED", "QQ 号已被使用。", 409)
            }
            val user = services.accounts.createUser(
                serverUuid = verification.serverUuid,
                gameId = verification.gameId,
                qq = qq,
                passwordHash = services.passwordHasher.hash(password)
            )
            services.verifications.consume(verification.id)
            issueAuth(services, user)
        }
        call.ok(result)
    }

    post("/account/login") {
        val body = call.receive<LoginRequest>()
        val account = body.account.trim()
        Validation.password(body.password)
        val ip = call.request.local.remoteHost
        val failureKey = Secrets.sha256("${account.lowercase()}|$ip")
        val result = dbQuery {
            services.loginFailures.lockedUntil(failureKey)?.let { locked ->
                if (locked.isAfter(Instant.now())) {
                    throw ApiException("LOGIN_LOCKED", "登录失败次数过多，请稍后再试。", 429, ChronoUnit.SECONDS.between(Instant.now(), locked))
                }
            }
            val user = services.accounts.findByAccount(account)
            if (user == null || !services.passwordHasher.verify(user.passwordHash, body.password)) {
                val (_, lockedUntil) = services.loginFailures.recordFailure(failureKey)
                if (lockedUntil != null) {
                    throw ApiException("LOGIN_LOCKED", "登录失败次数过多，请 15 分钟后再试。", 429, 15 * 60)
                }
                throw ApiException("ACCOUNT_PASSWORD_INVALID", "账号或密码错误。", 401)
            }
            services.loginFailures.clear(failureKey)
            issueAuth(services, user)
        }
        call.ok(result)
    }

    post("/account/password-reset-code") {
        val body = call.receive<PasswordResetCodeRequest>()
        val account = body.account.trim()
        val user = dbQuery {
            services.accounts.findByAccount(account)
                ?: throw ApiException("ACCOUNT_PASSWORD_INVALID", "账号或密码错误。", 401)
        }
        val now = Instant.now()
        val cooldown = dbQuery { services.verifications.latestActiveCooldown("password_reset", user.gameId) }
        if (cooldown != null && cooldown.isAfter(now)) {
            throw ApiException("VERIFICATION_COOLDOWN", "验证码发送太频繁，请稍后再试。", 429, ChronoUnit.SECONDS.between(now, cooldown))
        }
        val code = Secrets.sixDigitCode()
        val delivery = mapBridge {
            services.bridge.deliverVerification(Ids.verificationId(), "password_reset", user.gameId, code, now.plus(10, ChronoUnit.MINUTES))
        }
        when (delivery.status) {
            "delivered" -> Unit
            "player_offline" -> throw ApiException("PLAYER_NOT_ONLINE", "玩家当前不在线，请先进入 Deuterium VIII 服务器。", 409)
            else -> throw ApiException("PLUGIN_BRIDGE_UNAVAILABLE", "服务器连接暂不可用，请稍后再试。", 503)
        }
        val verificationToken = Ids.token("ver")
        val expiresAt = dbQuery {
            services.verifications.expirePrevious("password_reset", user.gameId)
            services.verifications.create(
                tokenHash = Secrets.sha256(verificationToken, services.config.security.verificationPepper),
                purpose = "password_reset",
                serverUuid = user.serverUuid,
                gameId = user.gameId,
                qq = null,
                codeHash = Secrets.sha256(code, services.config.security.verificationPepper)
            )
            Instant.now().plus(10, ChronoUnit.MINUTES)
        }
        call.ok(VerificationTokenData(verificationToken, expiresAt.iso(), 60))
    }

    post("/account/password-reset") {
        val body = call.receive<PasswordResetRequest>()
        val code = Validation.code(body.code)
        val password = Validation.password(body.newPassword, "新密码")
        val tokenHash = Secrets.sha256(body.verificationToken, services.config.security.verificationPepper)
        dbQuery {
            val verification = services.verifications.findByTokenHash(tokenHash, "password_reset")
                ?: throw ApiException("VERIFICATION_INVALID", "验证码错误或已失效。", 422)
            validateVerification(services, verification, code)
            val user = services.accounts.findByServerUuid(verification.serverUuid)
                ?: throw ApiException("ACCOUNT_PASSWORD_INVALID", "账号或密码错误。", 401)
            services.accounts.updatePassword(user.userId, services.passwordHasher.hash(password))
            services.accounts.revokeSessions(user.userId)
            services.verifications.consume(verification.id)
        }
        call.ok(PasswordResetData(passwordReset = true))
    }

    post("/account/logout") {
        val token = call.requireToken()
        dbQuery { services.sessions.revokeToken(Secrets.sha256(token, services.config.security.sessionTokenPepper)) }
        call.ok(LogoutData(loggedOut = true))
    }

    get("/account/me") {
        val user = call.requireUser(services)
        val profile = dbQuery { services.accounts.profile(user, services.playerRefs) }
        call.ok(UserProfileData(profile))
    }
}

private fun Route.walletRoutes(services: ApplicationServices) {
    get("/wallet/balance") {
        val user = call.requireUser(services)
        val balance = dbQuery {
            services.wallet.cachedBalance(user.userId) ?: WalletBalance("CREDIT", "0.00", fresh = false, refreshedAt = null)
        }
        call.ok(WalletBalanceData(balance))
    }

    post("/wallet/balance/refresh") {
        val user = call.requireUser(services)
        val result = mapBridge { services.bridge.walletBalance(user.serverUuid) }
        if (result.status != "success" || result.amount == null) {
            throw ApiException("SERVER_UNAVAILABLE", "余额刷新失败，请稍后再试。", 503)
        }
        val balance = dbQuery { services.wallet.upsertBalance(user.userId, result.amount) }
        call.ok(WalletBalanceData(balance))
    }

    get("/wallet/recipients/search") {
        call.requireUser(services)
        val query = call.request.queryParameters["query"]?.trim().orEmpty()
        val type = call.request.queryParameters["type"] ?: "auto"
        if (query.isBlank()) throw ApiException("INVALID_REQUEST", "请输入收款玩家。", 400)
        val candidates = when {
            type == "qq" || (type == "auto" && query.all { it.isDigit() }) -> {
                val user = dbQuery { services.accounts.findByQq(query) }
                if (user != null) {
                    val ref = dbQuery { services.playerRefs.ensurePlayerRef(user.serverUuid, user.gameId, user.qq, true, false, "qq", null) }
                    listOf(services.playerRefs.resolved(ref))
                } else if (type == "qq") {
                    throw ApiException("RECIPIENT_NOT_FOUND", "未找到绑定该 QQ 的玩家。", 404)
                } else {
                    resolveRecipientByGameId(services, query, "search")
                }
            }
            else -> resolveRecipientByGameId(services, query, if (type == "search") "search" else "game_id")
        }
        call.ok(RecipientSearchData(candidates))
    }

    post("/wallet/transfers") {
        val user = call.requireUser(services)
        val body = call.receive<CreateTransferRequest>()
        val amount = Validation.amount(body.amount)
        val note = Validation.note(body.note)
        val fingerprint = Secrets.sha256("${body.clientRequestId}|${body.recipientPlayerRef}|${amount.toPlainString()}|${note.orEmpty()}")
        val existing = dbQuery { services.wallet.findTransferByClientRequest(user.userId, body.clientRequestId) }
        if (existing != null) {
            if (existing.requestFingerprint != fingerprint) {
                throw ApiException("TRANSFER_DUPLICATE", "该转账请求已被使用，且内容不一致。", 409)
            }
            val status = if (existing.status == "processing" || existing.status == "unknown") HttpStatusCode.Accepted else HttpStatusCode.OK
            call.ok(TransferData(dbQuery { services.wallet.toApi(existing) }), status = status)
            return@post
        }
        val recipient = dbQuery {
            val ref = services.playerRefs.get(body.recipientPlayerRef)
                ?: throw ApiException("RECIPIENT_IDENTITY_UNCONFIRMED", "收款玩家身份无法确认，请重新搜索。", 409)
            if (ref.expiresAt != null && ref.expiresAt.isBefore(Instant.now())) {
                throw ApiException("RECIPIENT_IDENTITY_UNCONFIRMED", "收款玩家身份已过期，请重新搜索。", 409)
            }
            ref
        }
        val transferId = Ids.transferId()
        val transfer = dbQuery {
            services.wallet.createTransfer(transferId, body.clientRequestId, user, recipient, amount, note, fingerprint)
        }
        val result = try {
            services.bridge.walletTransfer(
                WalletTransferRequest(
                    transferId = transfer.id,
                    idempotencyKey = body.clientRequestId,
                    fromServerUuid = user.serverUuid,
                    toServerUuid = recipient.serverUuid,
                    amount = amount,
                    currency = "CREDIT",
                    note = note
                )
            )
        } catch (e: PluginBridgeTimeout) {
            null
        } catch (e: PluginBridgeUnavailable) {
            dbQuery { services.wallet.updateTransferStatus(transfer.id, "failed") }
            throw ApiException("PLUGIN_BRIDGE_UNAVAILABLE", "服务器连接暂不可用，请稍后再试。", 503)
        }
        val status = when (result?.status) {
            "success" -> "success"
            "balance_insufficient" -> "failed"
            "recipient_not_found" -> "failed"
            "economy_unavailable" -> "failed"
            "failed" -> "failed"
            "unknown", null -> "unknown"
            else -> "failed"
        }
        val (updated, walletEvents) = dbQuery {
            val changed = services.wallet.updateTransferStatus(transfer, status)
            val events = mutableListOf<Pair<String, com.deuterium.backend.model.WalletRecord>>()
            val expense = services.wallet.createRecord(user.userId, "expense", recipient, amount, status, note, changed.updatedAt)
            if (expense.status == "success") {
                events.add(user.userId to expense)
            }
            val recipientUser = services.accounts.findByServerUuid(recipient.serverUuid)
            if (status == "success" && recipientUser != null) {
                val payerRef = services.playerRefs.ensurePlayerRef(
                    serverUuid = user.serverUuid,
                    gameId = user.gameId,
                    qq = user.qq,
                    registered = true,
                    online = false,
                    source = "transfer",
                    expiresAt = null
                )
                val income = services.wallet.createRecord(
                    userId = recipientUser.userId,
                    direction = "income",
                    other = payerRef,
                    amount = amount,
                    status = "success",
                    note = note,
                    occurredAt = changed.updatedAt
                )
                events.add(recipientUser.userId to income)
            }
            changed to events
        }
        walletEvents.forEach { (userId, record) -> services.chatHub.sendWalletRecord(userId, record) }
        when (result?.status) {
            "balance_insufficient" -> throw ApiException("BALANCE_INSUFFICIENT", "余额不足。", 409)
            "recipient_not_found" -> throw ApiException("RECIPIENT_NOT_FOUND", "未找到收款玩家。", 404)
            "economy_unavailable", "failed" -> throw ApiException("TRANSFER_FAILED", "转账失败，请稍后再试。", 409)
        }
        val responseStatus = if (status == "unknown") HttpStatusCode.Accepted else HttpStatusCode.OK
        call.ok(TransferData(dbQuery { services.wallet.toApi(updated) }), status = responseStatus)
    }

    get("/wallet/transfers/{transferId}") {
        val user = call.requireUser(services)
        val id = call.parameters["transferId"].orEmpty()
        val transfer = dbQuery {
            services.wallet.getTransfer(id, user.userId)
                ?: throw ApiException("NOT_FOUND", "未找到该转账记录。", 404)
        }
        val responseStatus = if (transfer.status == "processing" || transfer.status == "unknown") HttpStatusCode.Accepted else HttpStatusCode.OK
        call.ok(TransferData(dbQuery { services.wallet.toApi(transfer) }), status = responseStatus)
    }

    get("/wallet/records") {
        val user = call.requireUser(services)
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val afterRecordId = call.request.queryParameters["afterRecordId"]?.trim()?.takeIf { it.isNotBlank() }
        val records = dbQuery {
            if (afterRecordId == null) {
                services.wallet.listRecords(user.userId, limit)
            } else {
                services.wallet.listRecordsAfter(user.userId, afterRecordId, limit)
            }
        }
        call.ok(WalletRecordsData(records), page = Page(nextCursor = null))
    }
}

private fun Route.chatRoutes(services: ApplicationServices) {
    get("/chat/messages") {
        call.requireUser(services)
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 100
        val messages = dbQuery { services.chat.listMessages(limit) }
        call.ok(ChatMessagesData(messages), page = Page(nextCursor = null))
    }

    get("/chat/presence") {
        call.requireUser(services)
        val snapshot = dbQuery { services.chat.presenceSnapshot() }
        if (snapshot != null) {
            if (snapshot.isStale()) {
                call.application.launch { refreshPresenceThrottled(services, force = false) }
            }
            call.ok(PresenceData(snapshot.onlineCount, available = services.bridge.isAvailable(), updatedAt = snapshot.updatedAt.iso()))
            return@get
        }
        val players = refreshPresenceThrottled(services, force = true)
        val refreshed = dbQuery { services.chat.presenceSnapshot() }
        call.ok(PresenceData(refreshed?.onlineCount ?: players.size, available = services.bridge.isAvailable(), updatedAt = refreshed?.updatedAt?.iso()))
    }

    get("/chat/online-players") {
        call.requireUser(services)
        val snapshot = dbQuery { services.chat.presenceSnapshot() }
        if (snapshot != null) {
            if (snapshot.isStale()) {
                call.application.launch { refreshPresenceThrottled(services, force = false) }
            }
            call.ok(OnlinePlayersData(snapshot.players))
            return@get
        }
        val players = refreshPresenceThrottled(services, force = true)
        call.ok(OnlinePlayersData(players))
    }

    get("/chat/player-directory") {
        val user = call.requireUser(services)
        val players = cachedPresencePlayers(services)
        val appConnections = services.chatHub.activeUserStates()
        val directory = dbQuery { services.chat.listPlayerDirectory(user, players, appConnections) }
        call.ok(PlayerDirectoryData(directory))
    }

    get("/chat/follows") {
        val user = call.requireUser(services)
        val players = cachedPresencePlayers(services)
        val appConnections = services.chatHub.activeUserStates()
        val followed = dbQuery { services.chat.listFollowedPlayers(user, players, appConnections) }
        call.ok(com.deuterium.backend.model.FollowedPlayersData(followed))
    }

    post("/chat/follows") {
        val user = call.requireUser(services)
        val body = call.receive<PlayerFollowRequest>()
        val players = cachedPresencePlayers(services)
        val appConnections = services.chatHub.activeUserStates()
        val target = dbQuery {
            services.chat.followPlayer(user.userId, body.playerRef)
                ?: throw ApiException("PLAYER_NOT_FOUND", "未找到该玩家。", 404)
        }
        val item = dbQuery { services.chat.playerDirectoryItem(user, target, players, appConnections) }
        call.ok(PlayerFollowData(followed = true, player = item.copy(followed = true)))
    }

    delete("/chat/follows/{playerRef}") {
        val user = call.requireUser(services)
        val playerRef = call.parameters["playerRef"].orEmpty()
        val players = cachedPresencePlayers(services)
        val appConnections = services.chatHub.activeUserStates()
        val target = dbQuery {
            services.chat.unfollowPlayer(user.userId, playerRef)
                ?: throw ApiException("PLAYER_NOT_FOUND", "未找到该玩家。", 404)
        }
        val item = dbQuery { services.chat.playerDirectoryItem(user, target, players, appConnections) }
        call.ok(PlayerFollowData(followed = false, player = item.copy(followed = false)))
    }

    webSocket("/chat/ws") {
        val token = call.bearerToken()
        val currentUser = dbQuery {
            token?.let { services.sessions.authenticate(Secrets.sha256(it, services.config.security.sessionTokenPepper)) }
        }
        if (currentUser == null) {
            close()
            return@webSocket
        }
        val includeEvents = call.request.queryParameters["includeServerEvents"] == "true"
        services.chatHub.add(this, currentUser.userId, includeEvents)
        dbQuery { services.chat.updateAppPresence(currentUser.userId, foreground = false) }
        try {
            incoming.consumeEach { frame ->
                if (frame !is Frame.Text) return@consumeEach
                val envelope = services.json.decodeFromString(AppWsEnvelope.serializer(), frame.readText())
                if (envelope.type == "app.state") {
                    val payload = services.json.decodeFromJsonElement<AppStatePayload>(envelope.payload)
                    services.chatHub.updateForeground(this, payload.foreground)
                    dbQuery { services.chat.updateAppPresence(currentUser.userId, payload.foreground) }
                    return@consumeEach
                }
                if (envelope.type != "chat.send") return@consumeEach
                val foreground = services.chatHub.anyForeground(currentUser.userId)
                dbQuery { services.chat.updateAppPresence(currentUser.userId, foreground) }
                val payload = services.json.decodeFromJsonElement<ChatSendPayload>(envelope.payload)
                val content = try {
                    Validation.chatContent(payload.content)
                } catch (e: ApiException) {
                    services.chatHub.sendError(this, envelope.requestId, e.code, e.message)
                    return@consumeEach
                }
                val mentionedUserIds = dbQuery {
                    val refs = services.playerRefs.findByPlayerRefs(payload.mentionedPlayerRefs.distinct().take(20))
                    val users = services.accounts.findByServerUuids(refs.values.map { it.serverUuid })
                    users.values
                        .map { it.userId }
                        .filter { it != currentUser.userId }
                        .distinct()
                }
                val messageId = Ids.messageId()
                val result = try {
                    services.bridge.sendAppChat(AppChatRequest(messageId, currentUser.serverUuid, currentUser.gameId, content))
                } catch (e: RuntimeException) {
                    null
                }
                if (result?.status == "sent") {
                    val message = dbQuery {
                        services.chat.insertChatMessage(messageId, currentUser.serverUuid, currentUser.gameId, content, Instant.now())
                    }
                    services.chatHub.broadcastMessage(message)
                    services.chatHub.sendMention(mentionedUserIds, message)
                    services.chatHub.sendResult(this, envelope.requestId, SendResultPayload(payload.clientMessageId, "accepted", messageId))
                    launch { services.maybeReplyToPublicChatAi(message) }
                } else {
                    services.chatHub.sendResult(
                        this,
                        envelope.requestId,
                        SendResultPayload(
                            clientMessageId = payload.clientMessageId,
                            status = "failed",
                            error = WsErrorPayload("CHAT_SEND_FAILED", "发送失败，服务器连接暂不可用。")
                        )
                    )
                }
            }
        } finally {
            services.chatHub.remove(this)
            val foreground = services.chatHub.anyForeground(currentUser.userId)
            dbQuery { services.chat.updateAppPresence(currentUser.userId, foreground) }
        }
    }
}

private fun Route.aiRoutes(services: ApplicationServices) {
    get("/ai/me") {
        val user = call.requireUser(services)
        val data = dbQuery {
            val plan = services.aiRepository.currentPlan(user.userId)
            val quota = services.aiRepository.quota(user.userId, plan)
            val conversation = services.aiRepository.currentConversation(user.userId)
            AiMeData(
                assistantName = services.aiRepository.assistantName(),
                plan = plan.toApi(),
                quota = quota,
                conversation = conversation.toApi()
            )
        }
        call.ok(data)
    }

    get("/ai/plans") {
        call.requireUser(services)
        val plans = dbQuery {
            services.aiRepository.listPlans()
                .map { it.toApi() }
        }
        call.ok(AiPlansData(plans))
    }

    get("/ai/messages") {
        val user = call.requireUser(services)
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
        val messages = dbQuery {
            val conversation = services.aiRepository.currentConversation(user.userId)
            services.aiRepository.listMessages(conversation.id, limit)
        }
        call.ok(AiMessagesData(messages))
    }

    post("/ai/conversation/reset") {
        val user = call.requireUser(services)
        call.ok(AiConversationResetData(services.ai.resetConversation(user)))
    }

    post("/ai/chat/stream") {
        val user = call.requireUser(services)
        val body = call.receive<AiChatStreamRequest>()
        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
        call.response.headers.append("X-Accel-Buffering", "no")
        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            coroutineScope {
                val writerMutex = Mutex()
                val heartbeat = launch {
                    while (true) {
                        delay(services.config.ai.streamHeartbeatMillis)
                        writerMutex.withLock {
                            writeSseComment("keep-alive")
                            flush()
                        }
                    }
                }
                try {
                    val result = services.ai.streamAppMessage(
                        user = user,
                        clientMessageId = body.clientMessageId,
                        rawContent = body.content,
                        onStarted = { started ->
                            writerMutex.withLock {
                                writeSse(
                                    "meta",
                                    services.json.encodeToString(
                                        buildJsonObject {
                                            put("conversationId", started.conversationId)
                                            put("userMessageId", started.userMessage?.messageId)
                                            put("assistantMessageId", started.assistantMessageId)
                                            put("quota", services.json.encodeToJsonElement(started.quota))
                                        }
                                    )
                                )
                                flush()
                            }
                        },
                        onStatus = { status ->
                            writerMutex.withLock {
                                writeSse("status", services.json.encodeToString(buildJsonObject { put("status", status) }))
                                flush()
                            }
                        },
                        onSources = { sources ->
                            writerMutex.withLock {
                                writeSse("sources", services.json.encodeToString(sources))
                                flush()
                            }
                        },
                        onDelta = { chunk ->
                            writerMutex.withLock {
                                writeSse("delta", services.json.encodeToString(buildJsonObject { put("content", chunk) }))
                                flush()
                            }
                        }
                    )
                    writerMutex.withLock {
                        writeSse(
                            "done",
                            services.json.encodeToString(
                                buildJsonObject {
                                    result.assistantMessage?.let { put("message", services.json.encodeToJsonElement(it)) }
                                    put("quota", services.json.encodeToJsonElement(result.quota))
                                }
                            )
                        )
                        flush()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    val writeFailure = writerMutex.withLock {
                        runCatching {
                            writeSse(
                                "error",
                                services.json.encodeToString(
                                    buildJsonObject {
                                        put("error", buildJsonObject {
                                            put("code", e.code)
                                            put("message", e.message)
                                            e.retryAfterSeconds?.let { put("retryAfterSeconds", it) }
                                        })
                                    }
                                )
                            )
                            flush()
                        }.exceptionOrNull()
                    }
                    if (writeFailure is CancellationException) throw writeFailure
                } catch (e: Exception) {
                    val writeFailure = writerMutex.withLock {
                        runCatching {
                            writeSse(
                                "error",
                                services.json.encodeToString(
                                    buildJsonObject {
                                        put("error", buildJsonObject {
                                            put("code", "AI_PROVIDER_UNAVAILABLE")
                                            put("message", "AI 回复生成失败，请稍后再试。")
                                        })
                                    }
                                )
                            )
                            flush()
                        }.exceptionOrNull()
                    }
                    if (writeFailure is CancellationException) throw writeFailure
                } finally {
                    heartbeat.cancelAndJoin()
                }
            }
        }
    }

    post("/ai/purchases") {
        val user = call.requireUser(services)
        val body = call.receive<AiPurchaseRequest>()
        if (body.clientRequestId.isBlank()) throw ApiException("INVALID_REQUEST", "clientRequestId 不能为空。", 400)
        val plan = dbQuery {
            services.aiRepository.planById(body.planId)
                ?: throw ApiException("AI_PLAN_NOT_FOUND", "未找到该 AI 套餐。", 404)
        }
        withAiPurchaseLock(user.userId, plan.id) {
            val existing = dbQuery { services.aiRepository.purchaseByClientRequest(user.userId, body.clientRequestId) }
            if (existing != null) {
                if (existing.planId != plan.id) throw ApiException("AI_PURCHASE_DUPLICATE", "该购买请求已被使用，且套餐不一致。", 409)
                if (existing.status == "processing" || existing.status == "unknown") {
                    call.completeAiPurchase(services, user, existing, plan)
                    return@withAiPurchaseLock
                }
                val quota = dbQuery { services.aiRepository.quota(user.userId, services.aiRepository.currentPlan(user.userId)) }
                call.ok(AiPurchaseData(existing.toApi(plan), quota), status = existing.aiPurchaseHttpStatus())
                return@withAiPurchaseLock
            }
            val active = dbQuery { services.aiRepository.activePurchaseByPlan(user.userId, plan.id) }
            if (active != null) {
                val quota = dbQuery { services.aiRepository.quota(user.userId, services.aiRepository.currentPlan(user.userId)) }
                call.ok(AiPurchaseData(active.toApi(plan), quota), status = HttpStatusCode.Accepted)
                return@withAiPurchaseLock
            }
            val purchase = dbQuery { services.aiRepository.createPurchase(user.userId, body.clientRequestId, plan) }
            if (plan.price <= java.math.BigDecimal.ZERO) {
                val (updated, quota) = dbQuery {
                    services.aiRepository.grantPlan(user.userId, plan)
                    val updatedPurchase = services.aiRepository.updatePurchaseStatus(purchase.id, "success")
                    val quota = services.aiRepository.quota(user.userId, services.aiRepository.currentPlan(user.userId))
                    updatedPurchase to quota
                }
                call.ok(AiPurchaseData(updated.toApi(plan), quota))
                return@withAiPurchaseLock
            }
            call.completeAiPurchase(services, user, purchase, plan)
        }
    }

    get("/ai/purchases/{purchaseId}") {
        val user = call.requireUser(services)
        val purchaseId = call.parameters["purchaseId"].orEmpty()
        val (purchase, plan, quota) = dbQuery {
            val purchase = services.aiRepository.purchaseById(user.userId, purchaseId)
                ?: throw ApiException("NOT_FOUND", "未找到该购买记录。", 404)
            val plan = services.aiRepository.planById(purchase.planId, includeInactive = true) ?: services.aiRepository.freePlan()
            val quota = services.aiRepository.quota(user.userId, services.aiRepository.currentPlan(user.userId))
            Triple(purchase, plan, quota)
        }
        val status = if (purchase.status == "processing" || purchase.status == "unknown") HttpStatusCode.Accepted else HttpStatusCode.OK
        call.ok(AiPurchaseData(purchase.toApi(plan), quota), status = status)
    }
}

private suspend fun ApplicationCall.completeAiPurchase(
    services: ApplicationServices,
    user: CurrentUser,
    purchase: AiPurchaseRecord,
    plan: AiPlanRecord,
) {
    val debit = try {
        services.bridge.walletDebit(
            WalletDebitRequest(
                debitId = purchase.id,
                idempotencyKey = purchase.id,
                serverUuid = user.serverUuid,
                amount = plan.price,
                currency = plan.currency,
                note = "购买 ${plan.name}"
            )
        )
    } catch (e: PluginBridgeTimeout) {
        null
    } catch (e: PluginBridgeUnavailable) {
        if (purchase.status == "unknown") {
            val updated = dbQuery { services.aiRepository.updatePurchaseStatus(purchase.id, "unknown", "AI_PURCHASE_RESULT_UNKNOWN") }
            ok(AiPurchaseData(updated.toApi(plan)), status = HttpStatusCode.Accepted)
            return
        }
        dbQuery { services.aiRepository.updatePurchaseStatus(purchase.id, "failed", "PLUGIN_BRIDGE_UNAVAILABLE") }
        throw ApiException("PLUGIN_BRIDGE_UNAVAILABLE", "服务器连接暂不可用，请稍后再试。", 503)
    }

    if (purchase.status == "unknown" && debit?.status != "success") {
        val updated = dbQuery { services.aiRepository.updatePurchaseStatus(purchase.id, "unknown", "AI_PURCHASE_RESULT_UNKNOWN") }
        ok(AiPurchaseData(updated.toApi(plan)), status = HttpStatusCode.Accepted)
        return
    }

    when (debit?.status) {
        "success" -> Unit
        "balance_insufficient" -> {
            dbQuery { services.aiRepository.updatePurchaseStatus(purchase.id, "failed", "BALANCE_INSUFFICIENT") }
            throw ApiException("BALANCE_INSUFFICIENT", "余额不足。", 409)
        }
        null, "unknown" -> {
            val updated = dbQuery { services.aiRepository.updatePurchaseStatus(purchase.id, "unknown", "AI_PURCHASE_RESULT_UNKNOWN") }
            ok(AiPurchaseData(updated.toApi(plan)), status = HttpStatusCode.Accepted)
            return
        }
        else -> {
            dbQuery { services.aiRepository.updatePurchaseStatus(purchase.id, "failed", debit.status) }
            throw ApiException("AI_PURCHASE_FAILED", "AI 套餐购买失败，请稍后再试。", 409)
        }
    }

    val (updated, quota, walletRecord) = dbQuery {
        services.aiRepository.grantPlan(user.userId, plan)
        val updatedPurchase = services.aiRepository.updatePurchaseStatus(purchase.id, "success")
        debit.balanceAfter?.let { services.wallet.upsertBalance(user.userId, it) }
        val other = services.playerRefs.ensurePlayerRef(
            serverUuid = "external:ai_service",
            gameId = services.aiRepository.assistantName(),
            qq = null,
            registered = false,
            online = false,
            source = "ai_purchase",
            expiresAt = null
        )
        val record = services.wallet.createRecord(user.userId, "expense", other, plan.price, "success", "购买 ${plan.name}")
        Triple(updatedPurchase, services.aiRepository.quota(user.userId, services.aiRepository.currentPlan(user.userId)), record)
    }
    services.chatHub.sendWalletRecord(user.userId, walletRecord)
    ok(AiPurchaseData(updated.toApi(plan), quota))
}

private fun AiPurchaseRecord.aiPurchaseHttpStatus(): HttpStatusCode =
    if (status == "processing" || status == "unknown") HttpStatusCode.Accepted else HttpStatusCode.OK

private suspend fun <T> withAiPurchaseLock(userId: String, planId: String, block: suspend () -> T): T {
    val key = "$userId:$planId"
    val lock = aiPurchaseLocksMutex.withLock {
        aiPurchaseLocks.getOrPut(key) { Mutex() }
    }
    return lock.withLock { block() }
}

private fun Routing.oneBotRoutes(services: ApplicationServices) {
    webSocket("/bridge/onebot/ws") {
        val header = call.request.headers[HttpHeaders.Authorization].orEmpty()
        val token = header.removePrefix("Bearer").trim()
        if (services.config.oneBot.token.isBlank() || token != services.config.oneBot.token) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid onebot token"))
            return@webSocket
        }
        incoming.consumeEach { frame ->
            if (frame !is Frame.Text) return@consumeEach
            val text = frame.readText()
            launch {
                runCatching { handleOneBotFrame(services, text) }
            }
        }
    }
}

private fun Routing.aiAdminRoutes(services: ApplicationServices) {
    get("/admin/ai") {
        val token = call.request.queryParameters["token"] ?: call.request.headers["X-Admin-Token"].orEmpty()
        if (!isValidAdminToken(services, token)) {
            call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            return@get
        }
        val notice = call.request.queryParameters["notice"].orEmpty()
        val query = call.request.queryParameters["q"].orEmpty().trim()
        val html = dbQuery {
            val assistantName = services.aiRepository.assistantName()
            val prompt = services.aiRepository.activePromptRecord()
            val modelConfig = services.aiRepository.activeModel()
            val plans = services.aiRepository.listPlans(includeInactive = true)
            val qqGroups = services.aiRepository.qqGroups()
            val knowledge = services.aiRepository.listKnowledge(includeInactive = true)
            val messages = services.aiRepository.listAdminMessages(query)
            val audits = services.aiRepository.listAdminAudits(query)
            val memory = services.aiRepository.listAdminMemory(query)
            aiAdminHtml(token, assistantName, prompt, modelConfig, plans, qqGroups, knowledge, messages, audits, memory, query, notice)
        }
        call.respondText(html, ContentType.Text.Html)
    }

    post("/admin/ai/settings") {
        val form = call.receiveParameters()
        val token = form["token"].orEmpty()
        if (!isValidAdminToken(services, token)) {
            call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            return@post
        }
        val assistantName = form["assistantName"].orEmpty().trim().take(40)
        if (assistantName.isBlank()) {
            call.respondRedirect(aiAdminRedirect(token, "AI 名字不能为空"))
            return@post
        }
        dbQuery { services.aiRepository.updateSetting("assistant_name", assistantName) }
        call.respondRedirect(aiAdminRedirect(token, "AI 名字已保存"))
    }

    post("/admin/ai/prompt") {
        val form = call.receiveParameters()
        val token = form["token"].orEmpty()
        if (!isValidAdminToken(services, token)) {
            call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            return@post
        }
        val title = form["title"].orEmpty().trim().take(80).ifBlank { "后台提示词" }
        val content = form["content"].orEmpty().trim().take(20000)
        if (content.isBlank()) {
            call.respondRedirect(aiAdminRedirect(token, "系统提示词不能为空"))
            return@post
        }
        dbQuery { services.aiRepository.overwriteActivePrompt(title, content) }
        call.respondRedirect(aiAdminRedirect(token, "系统提示词已保存"))
    }

    post("/admin/ai/model") {
        val form = call.receiveParameters()
        val token = form["token"].orEmpty()
        if (!isValidAdminToken(services, token)) {
            call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            return@post
        }
        val provider = form["provider"].orEmpty().trim().take(40).ifBlank { "deepseek" }
        val baseUrl = form["baseUrl"].orEmpty().trim().take(512)
        val model = form["model"].orEmpty().trim().take(80)
        if (!isValidHttpBaseUrl(baseUrl)) {
            call.respondRedirect(aiAdminRedirect(token, "模型 Base URL 必须是 http/https 地址"))
            return@post
        }
        if (model.isBlank()) {
            call.respondRedirect(aiAdminRedirect(token, "模型名不能为空"))
            return@post
        }
        dbQuery {
            services.aiRepository.updateActiveModelConfig(
                provider = provider,
                baseUrl = baseUrl.trimEnd('/'),
                model = model,
                temperature = form["temperature"].toDecimalIn(java.math.BigDecimal.ZERO, java.math.BigDecimal("2.00"), java.math.BigDecimal("0.40")),
                maxTokens = form["maxTokens"].toIntIn(64, 8000, 900),
                thinkingEnabled = form["thinkingEnabled"] == "on"
            )
        }
        call.respondRedirect(aiAdminRedirect(token, "模型配置已保存"))
    }

    post("/admin/ai/plans") {
        val form = call.receiveParameters()
        val token = form["token"].orEmpty()
        if (!isValidAdminToken(services, token)) {
            call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            return@post
        }
        val planId = form["planId"].orEmpty()
        val existing = dbQuery { services.aiRepository.planById(planId, includeInactive = true) }
        if (existing == null) {
            call.respondRedirect(aiAdminRedirect(token, "套餐不存在"))
            return@post
        }
        val name = form["name"].orEmpty().trim().take(80).ifBlank { existing.name }
        val description = form["description"].orEmpty().trim().take(255).ifBlank { existing.description }
        val price = form["price"].toMoney(existing.price)
        val quotaPerWindow = form["quotaPerWindow"].toIntIn(1, 10000, existing.quotaPerWindow)
        val quotaWindowHours = form["quotaWindowHours"].toIntIn(1, 720, existing.quotaWindowHours)
        val durationDays = form["durationDays"].toIntIn(0, 3650, existing.durationDays)
        val modelTier = form["modelTier"].orEmpty().trim().take(32).ifBlank { existing.modelTier }
        val active = form["active"] == "on"
        dbQuery {
            services.aiRepository.updatePlan(
                planId = planId,
                name = name,
                description = description,
                price = price,
                quotaPerWindow = quotaPerWindow,
                quotaWindowHours = quotaWindowHours,
                durationDays = durationDays,
                modelTier = modelTier,
                active = active
            )
        }
        call.respondRedirect(aiAdminRedirect(token, "套餐已保存"))
    }

    post("/admin/ai/qq-groups") {
        val form = call.receiveParameters()
        val token = form["token"].orEmpty()
        if (!isValidAdminToken(services, token)) {
            call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            return@post
        }
        val groupId = form["groupId"].orEmpty().trim().take(40)
        if (groupId.isBlank()) {
            call.respondRedirect(aiAdminRedirect(token, "QQ群号不能为空"))
            return@post
        }
        val triggerPattern = form["triggerPattern"].orEmpty().trim().take(255).ifBlank { "^(/ai|!ai)\\s+(.+)$" }
        val quotaPerWindow = form["quotaPerWindow"].toIntIn(1, 10000, 30)
        val windowMinutes = form["windowMinutes"].toIntIn(1, 10080, 10)
        val enabled = form["enabled"] == "on"
        val invalidRegex = runCatching { Regex(triggerPattern) }.exceptionOrNull()
        if (invalidRegex != null) {
            call.respondRedirect(aiAdminRedirect(token, "触发正则无效"))
            return@post
        }
        dbQuery {
            services.aiRepository.upsertQqGroup(
                groupId = groupId,
                enabled = enabled,
                triggerPattern = triggerPattern,
                quotaPerWindow = quotaPerWindow,
                windowMinutes = windowMinutes
            )
        }
        call.respondRedirect(aiAdminRedirect(token, "QQ群白名单已保存"))
    }

    post("/admin/ai/qq-groups/delete") {
        val form = call.receiveParameters()
        val token = form["token"].orEmpty()
        if (!isValidAdminToken(services, token)) {
            call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            return@post
        }
        val groupId = form["groupId"].orEmpty().trim()
        if (groupId.isNotBlank()) {
            dbQuery { services.aiRepository.deleteQqGroup(groupId) }
        }
        call.respondRedirect(aiAdminRedirect(token, "QQ群白名单已删除"))
    }

    post("/admin/ai/knowledge") {
        val form = call.receiveParameters()
        val token = form["token"].orEmpty()
        if (!isValidAdminToken(services, token)) {
            call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            return@post
        }
        val title = form["title"].orEmpty().trim().take(120)
        val content = form["content"].orEmpty().trim().take(20000)
        if (title.isBlank() || content.isBlank()) {
            call.respondRedirect(aiAdminRedirect(token, "知识库标题和内容不能为空"))
            return@post
        }
        dbQuery {
            services.aiRepository.upsertKnowledge(
                id = form["id"].orEmpty().trim().takeIf { it.isNotBlank() },
                category = form["category"].orEmpty().trim().take(80).ifBlank { "默认" },
                title = title,
                keywords = form["keywords"].orEmpty().trim().take(2000),
                content = content,
                weight = form["weight"].toDecimalIn(java.math.BigDecimal("0.10"), java.math.BigDecimal("99.99"), java.math.BigDecimal.ONE),
                active = form["active"] == "on"
            )
        }
        call.respondRedirect(aiAdminRedirect(token, "知识库已保存"))
    }

    post("/admin/ai/knowledge/delete") {
        val form = call.receiveParameters()
        val token = form["token"].orEmpty()
        if (!isValidAdminToken(services, token)) {
            call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            return@post
        }
        val id = form["id"].orEmpty().trim()
        if (id.isNotBlank()) {
            dbQuery { services.aiRepository.deleteKnowledge(id) }
        }
        call.respondRedirect(aiAdminRedirect(token, "知识库已删除"))
    }

    post("/admin/ai/memory/status") {
        val form = call.receiveParameters()
        val token = form["token"].orEmpty()
        if (!isValidAdminToken(services, token)) {
            call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
            return@post
        }
        val id = form["id"].orEmpty().trim()
        val status = form["status"].orEmpty().trim().takeIf { it in setOf("active", "disabled") } ?: "disabled"
        if (id.isNotBlank()) {
            dbQuery { services.aiRepository.updateMemoryStatus(id, status) }
        }
        call.respondRedirect(aiAdminRedirect(token, "记忆状态已更新"))
    }
}

private fun isValidAdminToken(services: ApplicationServices, token: String): Boolean =
    services.config.admin.token.isNotBlank() && token == services.config.admin.token

private fun isValidHttpBaseUrl(value: String): Boolean =
    runCatching {
        val uri = URI.create(value.trim())
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

private fun aiAdminRedirect(token: String, notice: String): String =
    "/admin/ai?token=${urlEncode(token)}&notice=${urlEncode(notice)}"

private fun aiAdminHtml(
    token: String,
    assistantName: String,
    prompt: AiPromptRecord,
    modelConfig: AiModelConfigRecord,
    plans: List<AiPlanRecord>,
    qqGroups: List<AiQqGroupRecord>,
    knowledge: List<AiKnowledgeRecord>,
    messages: List<AiAdminMessageRecord>,
    audits: List<AiAuditRecord>,
    memory: List<AiMemoryRecord>,
    query: String,
    notice: String,
): String {
    val planRows = plans.joinToString("\n") { plan ->
        """
        <form class="row" method="post" action="/admin/ai/plans">
          <input type="hidden" name="token" value="${token.h()}">
          <input type="hidden" name="planId" value="${plan.id.h()}">
          <div><strong>${plan.code.h()}</strong><span>${plan.id.h()}</span></div>
          <label>名称<input name="name" value="${plan.name.h()}" maxlength="80"></label>
          <label>说明<input name="description" value="${plan.description.h()}" maxlength="255"></label>
          <label>价格<input name="price" type="number" min="0" step="0.01" value="${plan.price.money()}"></label>
          <label>额度<input name="quotaPerWindow" type="number" min="1" max="10000" value="${plan.quotaPerWindow}"></label>
          <label>恢复窗口(小时)<input name="quotaWindowHours" type="number" min="1" max="720" value="${plan.quotaWindowHours}"></label>
          <label>套餐时长(天)<input name="durationDays" type="number" min="0" max="3650" value="${plan.durationDays}"></label>
          <label>模型档位<input name="modelTier" value="${plan.modelTier.h()}" maxlength="32"></label>
          <label class="check"><input name="active" type="checkbox" ${if (plan.active) "checked" else ""}>启用</label>
          <button type="submit">保存套餐</button>
        </form>
        """.trimIndent()
    }
    val groupRows = qqGroups.joinToString("\n") { group ->
        """
        <form class="row" method="post" action="/admin/ai/qq-groups">
          <input type="hidden" name="token" value="${token.h()}">
          <label>QQ群号<input name="groupId" value="${group.groupId.h()}" maxlength="40"></label>
          <label>触发正则<input name="triggerPattern" value="${group.triggerPattern.h()}" maxlength="255"></label>
          <label>群额度<input name="quotaPerWindow" type="number" min="1" max="10000" value="${group.quotaPerWindow}"></label>
          <label>窗口(分钟)<input name="windowMinutes" type="number" min="1" max="10080" value="${group.windowMinutes}"></label>
          <label class="check"><input name="enabled" type="checkbox" ${if (group.enabled) "checked" else ""}>启用</label>
          <button type="submit">保存群</button>
        </form>
        <form class="delete" method="post" action="/admin/ai/qq-groups/delete">
          <input type="hidden" name="token" value="${token.h()}">
          <input type="hidden" name="groupId" value="${group.groupId.h()}">
          <button type="submit">删除 ${group.groupId.h()}</button>
        </form>
        """.trimIndent()
    }
    val knowledgeRows = knowledge.joinToString("\n") { item ->
        """
        <form class="knowledge-form" method="post" action="/admin/ai/knowledge">
          <input type="hidden" name="token" value="${token.h()}">
          <input type="hidden" name="id" value="${item.id.h()}">
          <div class="knowledge-grid">
            <label>分类<input name="category" value="${item.category.h()}" maxlength="80"></label>
            <label>标题<input name="title" value="${item.title.h()}" maxlength="120"></label>
            <label>关键词<textarea name="keywords" rows="2">${item.keywords.h()}</textarea></label>
            <label>权重<input name="weight" type="number" min="0.10" max="99.99" step="0.10" value="${item.weight.adminNumber()}"></label>
            <label class="check"><input name="active" type="checkbox" ${if (item.active) "checked" else ""}>启用</label>
          </div>
          <label>内容<textarea name="content" rows="5">${item.content.h()}</textarea></label>
          <button type="submit">保存知识</button>
        </form>
        <form class="delete" method="post" action="/admin/ai/knowledge/delete">
          <input type="hidden" name="token" value="${token.h()}">
          <input type="hidden" name="id" value="${item.id.h()}">
          <button type="submit">删除 ${item.title.h()}</button>
        </form>
        """.trimIndent()
    }
    val messageRows = messages.joinToString("\n") { row ->
        """
        <tr>
          <td>${row.createdAt.iso().h()}</td>
          <td>${row.gameId.h()}<br><span>${row.qq.h()}</span></td>
          <td>${row.role.h()}</td>
          <td><pre>${row.content.h()}</pre></td>
        </tr>
        """.trimIndent()
    }
    val auditRows = audits.joinToString("\n") { row ->
        val providerInfo = listOfNotNull(
            row.model?.let { "model=$it" },
            row.providerStatusCode?.let { "http=$it" },
            row.firstTokenLatencyMs?.let { "first=${it}ms" },
            row.totalLatencyMs?.let { "total=${it}ms" },
            row.retryCount?.let { "retry=$it" },
            row.emittedDelta?.let { "delta=$it" }
        ).joinToString(" · ")
        val responseAndDiagnostics = listOf(
            row.responseText.orEmpty(),
            row.providerError?.let { "Provider: $it" }.orEmpty(),
            row.knowledgeQuery?.let { "Knowledge query: $it" }.orEmpty(),
            row.knowledgeSources?.let { "Knowledge sources:\n$it" }.orEmpty()
        ).filter { it.isNotBlank() }.joinToString("\n\n")
        """
        <tr>
          <td>${row.createdAt.iso().h()}</td>
          <td>${row.gameId.orEmpty().h()}<br><span>${row.channel.h()}</span></td>
          <td>${row.status.h()}<br><span>${row.riskCode.orEmpty().h()}</span><br><span>${providerInfo.h()}</span></td>
          <td><pre>${row.requestText.h()}</pre></td>
          <td><pre>${responseAndDiagnostics.h()}</pre></td>
        </tr>
        """.trimIndent()
    }
    val memoryRows = memory.joinToString("\n") { row ->
        """
        <tr>
          <td>${row.updatedAt.iso().h()}</td>
          <td>${row.gameId.h()}<br><span>${row.qq.h()}</span></td>
          <td>${row.kind.h()}<br><span>${row.weight.adminNumber()}</span></td>
          <td>${row.status.h()}</td>
          <td><pre>${row.content.h()}</pre></td>
          <td>
            <form method="post" action="/admin/ai/memory/status">
              <input type="hidden" name="token" value="${token.h()}">
              <input type="hidden" name="id" value="${row.id.h()}">
              <input type="hidden" name="status" value="${if (row.status == "active") "disabled" else "active"}">
              <button type="submit">${if (row.status == "active") "停用" else "启用"}</button>
            </form>
          </td>
        </tr>
        """.trimIndent()
    }
    return """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>${assistantName.h()} Admin</title>
          <style>
            body{font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;margin:0;background:#f6f7f9;color:#15171a}
            main{max-width:1120px;margin:0 auto;padding:28px 18px 64px}
            h1{font-size:28px;margin:0 0 22px}
            h2{font-size:20px;margin:28px 0 12px}
            section{background:white;border:1px solid #dde1e7;border-radius:10px;padding:18px;margin-bottom:16px}
            label{display:flex;flex-direction:column;gap:6px;font-size:13px;color:#505761}
            input,textarea{font:inherit;padding:9px 10px;border:1px solid #cfd5de;border-radius:8px;background:#fff;min-width:0}
            textarea{resize:vertical}
            button{font:inherit;border:0;border-radius:8px;padding:10px 14px;background:#111827;color:white;cursor:pointer}
            button:hover{background:#293241}
            .notice{background:#ecfdf5;border-color:#a7f3d0;color:#065f46}
            .search{display:flex;gap:10px;align-items:end}
            .prompt textarea{min-height:180px}
            .model-row{display:grid;grid-template-columns:.8fr 1.8fr 1.2fr .55fr .55fr .45fr .55fr;gap:10px;align-items:end}
            .row{display:grid;grid-template-columns:1.1fr 1.1fr 1.8fr .75fr .65fr .85fr .85fr .75fr .45fr .7fr;gap:10px;align-items:end;border-top:1px solid #eef1f5;padding:14px 0}
            .row:first-child{border-top:0;padding-top:0}
            .row span{display:block;color:#747b86;font-size:12px;margin-top:4px}
            .check{align-items:flex-start;gap:8px}
            .check input{width:auto}
            .qq .row{grid-template-columns:1fr 2fr .7fr .7fr .45fr .65fr}
            .knowledge-form{border-top:1px solid #eef1f5;padding:14px 0;display:grid;gap:10px}
            .knowledge-grid{display:grid;grid-template-columns:1fr 1.2fr 1.6fr .45fr .35fr;gap:10px;align-items:end}
            table{width:100%;border-collapse:collapse;font-size:13px}
            th,td{border-top:1px solid #eef1f5;padding:9px;text-align:left;vertical-align:top}
            th{color:#505761;font-weight:600}
            pre{white-space:pre-wrap;word-break:break-word;margin:0;font:12px ui-monospace,SFMono-Regular,Consolas,monospace;max-height:220px;overflow:auto}
            td span{color:#747b86;font-size:12px}
            .delete{margin:-8px 0 12px;text-align:right}
            .delete button{background:#b91c1c}
            .empty{color:#69707a}
            @media(max-width:900px){.row,.model-row,.qq .row,.knowledge-grid,.search{grid-template-columns:1fr;display:grid}.delete{text-align:left}}
          </style>
        </head>
        <body>
          <main>
            <h1>${assistantName.h()} Admin</h1>
            ${if (notice.isNotBlank()) """<section class="notice">${notice.h()}</section>""" else ""}
            <section>
              <h2>AI 名字</h2>
              <form class="row" method="post" action="/admin/ai/settings">
                <input type="hidden" name="token" value="${token.h()}">
                <label>Assistant Name<input name="assistantName" value="${assistantName.h()}" maxlength="40"></label>
                <button type="submit">保存名字</button>
              </form>
            </section>
            <section class="prompt">
              <h2>系统提示词</h2>
              <form method="post" action="/admin/ai/prompt">
                <input type="hidden" name="token" value="${token.h()}">
                <label>标题<input name="title" value="${prompt.title.h()}" maxlength="80"></label>
                <label>内容<textarea name="content">${prompt.content.h()}</textarea></label>
                <button type="submit">保存提示词</button>
              </form>
              <p class="empty">当前版本：${prompt.id.h()}，更新于 ${prompt.updatedAt.iso().h()}。保存会直接覆盖当前启用提示词。</p>
            </section>
            <section>
              <h2>模型配置</h2>
              <form class="model-row" method="post" action="/admin/ai/model">
                <input type="hidden" name="token" value="${token.h()}">
                <label>Provider<input name="provider" value="${modelConfig.provider.h()}" maxlength="40"></label>
                <label>Base URL<input name="baseUrl" value="${modelConfig.baseUrl.h()}" maxlength="512"></label>
                <label>模型名<input name="model" value="${modelConfig.model.h()}" maxlength="80"></label>
                <label>Temperature<input name="temperature" type="number" min="0" max="2" step="0.01" value="${modelConfig.temperature}"></label>
                <label>Max tokens<input name="maxTokens" type="number" min="64" max="8000" value="${modelConfig.maxTokens}"></label>
                <label class="check"><input name="thinkingEnabled" type="checkbox" ${if (modelConfig.thinkingEnabled) "checked" else ""}>思考</label>
                <button type="submit">保存模型</button>
              </form>
              <p class="empty">App 私聊、公共聊天和 QQ 入口都会使用这里的当前激活模型配置。</p>
            </section>
            <section>
              <h2>套餐 Plans</h2>
              $planRows
            </section>
            <section class="qq">
              <h2>QQ群白名单</h2>
              ${groupRows.ifBlank { """<p class="empty">还没有配置 QQ 群。下面新增一个白名单群后，NapCat 才会处理该群消息。</p>""" }}
              <h2>新增 QQ 群</h2>
              <form class="row" method="post" action="/admin/ai/qq-groups">
                <input type="hidden" name="token" value="${token.h()}">
                <label>QQ群号<input name="groupId" maxlength="40" placeholder="例如 123456789"></label>
                <label>触发正则<input name="triggerPattern" value="^(/ai|!ai)\s+(.+)${'$'}" maxlength="255"></label>
                <label>群额度<input name="quotaPerWindow" type="number" min="1" max="10000" value="30"></label>
                <label>窗口(分钟)<input name="windowMinutes" type="number" min="1" max="10080" value="10"></label>
                <label class="check"><input name="enabled" type="checkbox" checked>启用</label>
                <button type="submit">新增/更新</button>
              </form>
            </section>
            <section>
              <h2>文本知识库</h2>
              <p class="empty">服务器知识权威来源：<a href="https://wiki.deuterium.cafe/" target="_blank" rel="noreferrer">wiki.deuterium.cafe</a>。这里维护的是注入 AI 的摘录知识，不能覆盖系统提示词和安全规则。</p>
              ${knowledgeRows.ifBlank { """<p class="empty">还没有知识库条目。</p>""" }}
              <h2>新增知识</h2>
              <form class="knowledge-form" method="post" action="/admin/ai/knowledge">
                <input type="hidden" name="token" value="${token.h()}">
                <div class="knowledge-grid">
                  <label>分类<input name="category" value="默认" maxlength="80"></label>
                  <label>标题<input name="title" maxlength="120"></label>
                  <label>关键词<textarea name="keywords" rows="2" placeholder="用逗号或空格分隔"></textarea></label>
                  <label>权重<input name="weight" type="number" min="0.10" max="99.99" step="0.10" value="1"></label>
                  <label class="check"><input name="active" type="checkbox" checked>启用</label>
                </div>
                <label>内容<textarea name="content" rows="5"></textarea></label>
                <button type="submit">新增知识</button>
              </form>
            </section>
            <section>
              <h2>玩家聊天记录 / 记忆 / 审计</h2>
              <form class="search" method="get" action="/admin/ai">
                <input type="hidden" name="token" value="${token.h()}">
                <label>筛选玩家 ID / QQ / userId / 状态<input name="q" value="${query.h()}" maxlength="80"></label>
                <button type="submit">筛选</button>
              </form>
              <h2>最近 AI 消息</h2>
              <table><thead><tr><th>时间</th><th>玩家</th><th>角色</th><th>内容</th></tr></thead><tbody>${messageRows.ifBlank { """<tr><td colspan="4" class="empty">暂无消息</td></tr>""" }}</tbody></table>
              <h2>AI 记忆</h2>
              <table><thead><tr><th>更新时间</th><th>玩家</th><th>类型/权重</th><th>状态</th><th>内容</th><th>操作</th></tr></thead><tbody>${memoryRows.ifBlank { """<tr><td colspan="6" class="empty">暂无记忆</td></tr>""" }}</tbody></table>
              <h2>请求审计</h2>
              <table><thead><tr><th>时间</th><th>来源</th><th>状态</th><th>请求</th><th>回复/错误</th></tr></thead><tbody>${auditRows.ifBlank { """<tr><td colspan="5" class="empty">暂无审计</td></tr>""" }}</tbody></table>
            </section>
          </main>
        </body>
        </html>
    """.trimIndent()
}

private fun String?.toIntIn(min: Int, max: Int, default: Int): Int =
    this?.toIntOrNull()?.coerceIn(min, max) ?: default.coerceIn(min, max)

private fun String?.toMoney(default: java.math.BigDecimal): java.math.BigDecimal =
    runCatching { java.math.BigDecimal(this?.trim().orEmpty()).coerceAtLeast(java.math.BigDecimal.ZERO) }
        .getOrDefault(default)

private fun String?.toDecimalIn(min: java.math.BigDecimal, max: java.math.BigDecimal, default: java.math.BigDecimal): java.math.BigDecimal =
    runCatching {
        val value = java.math.BigDecimal(this?.trim().orEmpty())
        value.coerceAtLeast(min).coerceAtMost(max)
    }.getOrDefault(default)

private fun java.math.BigDecimal.adminNumber(): String =
    stripTrailingZeros().toPlainString()

private fun String.h(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

private fun urlEncode(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8")

private suspend fun java.io.Writer.writeSse(event: String, data: String) {
    write("event: $event\n")
    data.lines().forEach { line -> write("data: $line\n") }
    write("\n")
}

private suspend fun java.io.Writer.writeSseComment(comment: String) {
    write(": $comment\n\n")
}

private suspend fun DefaultWebSocketServerSession.handleOneBotFrame(services: ApplicationServices, text: String) {
    val root = runCatching { services.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
    if (root["post_type"]?.jsonPrimitive?.contentOrNull != "message") return
    if (root["message_type"]?.jsonPrimitive?.contentOrNull != "group") return
    val groupId = root["group_id"]?.jsonPrimitive?.contentOrNull ?: return
    val messageId = root["message_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
    if (!rememberOneBotMessage(groupId, messageId)) return
    val userId = root["user_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
    if (services.config.oneBot.botSelfId.isNotBlank() && userId == services.config.oneBot.botSelfId) return
    val raw = root["raw_message"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (raw.isBlank()) return
    val groupConfig = dbQuery {
        val configured = services.aiRepository.qqGroups().firstOrNull { it.groupId == groupId }
        if (configured != null) {
            ResolvedQqGroupConfig(
                groupId = groupId,
                enabled = configured.enabled,
                triggerPattern = configured.triggerPattern,
                quotaPerWindow = configured.quotaPerWindow,
                windowMinutes = configured.windowMinutes,
                mutedUntil = configured.mutedUntil
            )
        } else {
            ResolvedQqGroupConfig(
                groupId = groupId,
                enabled = services.config.oneBot.defaultGroupId.isNotBlank() && groupId == services.config.oneBot.defaultGroupId,
                triggerPattern = services.config.oneBot.defaultTriggerPattern,
                quotaPerWindow = 30,
                windowMinutes = 10,
                mutedUntil = null
            )
        }
    }
    if (!groupConfig.enabled) return
    if (groupConfig.mutedUntil?.isAfter(Instant.now()) == true) return
    val match = runCatching { Regex(groupConfig.triggerPattern).find(raw) }.getOrNull() ?: return
    val prompt = (match.groups[2]?.value ?: match.groups[1]?.value ?: raw).trim()
    if (prompt.isBlank()) return
    if (!consumeQqGroupQuota(groupId, groupConfig.quotaPerWindow, groupConfig.windowMinutes)) {
        dbQuery {
            services.aiRepository.audit(
                userId = null,
                channel = "qq_group:$groupId",
                requestText = prompt,
                responseText = null,
                riskCode = "group_rate_limited",
                status = "blocked",
                model = null,
                tokenEstimate = null
            )
        }
        return
    }
    if (!tryEnterQqGroupAi(groupId)) {
        dbQuery {
            services.aiRepository.audit(
                userId = null,
                channel = "qq_group:$groupId",
                requestText = prompt,
                responseText = null,
                riskCode = "group_ai_in_flight",
                status = "blocked",
                model = null,
                tokenEstimate = null
            )
        }
        return
    }
    try {
        val reply = try {
            services.ai.sendQqGroupMessage(groupId, userId, prompt)
        } catch (e: ApiException) {
            dbQuery {
                services.aiRepository.audit(
                    userId = null,
                    channel = "qq_group:$groupId",
                    requestText = prompt,
                    responseText = null,
                    riskCode = e.code,
                    status = "blocked",
                    model = null,
                    tokenEstimate = null
                )
            }
            return
        }
        sendOneBotGroupMessage(services, groupId, "$QqPublicAssistantName：$reply")
    } finally {
        leaveQqGroupAi(groupId)
    }
}

private suspend fun DefaultWebSocketServerSession.sendOneBotGroupMessage(
    services: ApplicationServices,
    groupId: String,
    message: String,
) {
    val echo = buildJsonObject {
        put("action", "send_group_msg")
        put("params", buildJsonObject {
            put("group_id", groupId)
            put("message", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("data", buildJsonObject {
                        put("text", message)
                    })
                })
            })
        })
        put("echo", "ai_${Ids.requestId()}")
    }
    oneBotSendMutex.withLock {
        send(services.json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), echo))
    }
}

private suspend fun rememberOneBotMessage(groupId: String, messageId: String): Boolean {
    if (messageId.isBlank()) return true
    val key = "$groupId:$messageId"
    val now = Instant.now()
    return oneBotDedupeMutex.withLock {
        oneBotSeenMessages.entries.removeIf { (_, seenAt) -> Duration.between(seenAt, now).toMinutes() >= 60 }
        if (oneBotSeenMessages.containsKey(key)) {
            false
        } else {
            oneBotSeenMessages[key] = now
            while (oneBotSeenMessages.size > 2_000) {
                val first = oneBotSeenMessages.entries.iterator()
                if (first.hasNext()) {
                    first.next()
                    first.remove()
                }
            }
            true
        }
    }
}

private suspend fun consumeQqGroupQuota(groupId: String, quotaPerWindow: Int, windowMinutes: Int): Boolean {
    if (quotaPerWindow <= 0 || windowMinutes <= 0) return true
    val now = Instant.now()
    return qqGroupRateMutex.withLock {
        val current = qqGroupRateWindows[groupId]
        if (current == null || Duration.between(current.startedAt, now).toMinutes() >= windowMinutes) {
            qqGroupRateWindows[groupId] = QqGroupRateWindow(now, 1)
            true
        } else if (current.used < quotaPerWindow) {
            current.used += 1
            true
        } else {
            false
        }
    }
}

private suspend fun tryEnterQqGroupAi(groupId: String): Boolean =
    qqGroupRateMutex.withLock {
        if (qqGroupInFlight.contains(groupId)) {
            false
        } else {
            qqGroupInFlight += groupId
            true
        }
    }

private suspend fun leaveQqGroupAi(groupId: String) {
    qqGroupRateMutex.withLock {
        qqGroupInFlight -= groupId
    }
}

private suspend fun resolveRecipientByGameId(services: ApplicationServices, gameId: String, source: String): List<com.deuterium.backend.model.ResolvedPlayerRef> {
    val resolved = mapBridge { services.bridge.resolvePlayer(gameId, "transfer_recipient") }
    when (resolved.status) {
        "resolved" -> Unit
        "player_not_found" -> throw ApiException("RECIPIENT_NOT_FOUND", "未找到收款玩家。", 404)
        "identity_conflict" -> throw ApiException("RECIPIENT_IDENTITY_UNCONFIRMED", "收款玩家身份无法确认。", 409)
        else -> throw ApiException("RECIPIENT_IDENTITY_UNCONFIRMED", "收款玩家身份无法确认。", 409)
    }
    val serverUuid = resolved.serverUuid ?: throw ApiException("RECIPIENT_IDENTITY_UNCONFIRMED", "收款玩家身份无法确认。", 409)
    val currentGameId = resolved.currentGameId ?: gameId
    val ref = dbQuery {
        val registered = services.accounts.findByServerUuid(serverUuid)
        services.playerRefs.ensurePlayerRef(
            serverUuid = serverUuid,
            gameId = registered?.gameId ?: currentGameId,
            qq = registered?.qq,
            registered = registered != null,
            online = resolved.online,
            source = source,
            expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES)
        )
    }
    return listOf(services.playerRefs.resolved(ref))
}

private suspend fun refreshPresence(services: ApplicationServices): List<OnlinePlayer> {
    val result = mapBridge { services.bridge.presenceList() }
    if (result.status != "success") throw ApiException("PLUGIN_BRIDGE_UNAVAILABLE", "服务器连接暂不可用，请稍后再试。", 503)
    val players = dbQuery {
        val registeredByServerUuid = services.accounts.findByServerUuids(result.players.map { it.serverUuid })
        result.players.map { bridgePlayer ->
            val registered = registeredByServerUuid[bridgePlayer.serverUuid]
            val ref = services.playerRefs.ensurePlayerRef(
                serverUuid = bridgePlayer.serverUuid,
                gameId = registered?.gameId ?: bridgePlayer.currentGameId,
                qq = registered?.qq,
                registered = registered != null,
                online = true,
                source = "online_list",
                expiresAt = null
            )
            OnlinePlayer(ref.playerRef, ref.gameId, ref.qq, ref.registered, bridgePlayer.onlineSince?.iso())
        }.also { services.chat.updatePresence(it, Instant.now()) }
    }
    services.chatHub.broadcastPresence(players.size, players)
    return players
}

private suspend fun refreshPresenceThrottled(services: ApplicationServices, force: Boolean): List<OnlinePlayer> {
    if (!services.bridge.isAvailable()) {
        return cachedPresencePlayers(services)
    }
    if (!force && !shouldStartPresenceRefresh()) {
        return cachedPresencePlayers(services)
    }
    val locked = if (force) {
        presenceRefreshMutex.lock()
        true
    } else {
        presenceRefreshMutex.tryLock()
    }
    if (!locked) return cachedPresencePlayers(services)
    return try {
        lastPresenceRefreshStartedAt = Instant.now()
        refreshPresence(services)
    } catch (e: ApiException) {
        cachedPresencePlayers(services)
    } finally {
        presenceRefreshMutex.unlock()
    }
}

private fun shouldStartPresenceRefresh(): Boolean {
    val lastStartedAt = lastPresenceRefreshStartedAt ?: return true
    return Duration.between(lastStartedAt, Instant.now()).toMillis() >= PresenceRefreshMinIntervalMillis
}

private suspend fun cachedPresencePlayers(services: ApplicationServices): List<OnlinePlayer> =
    dbQuery { services.chat.presenceSnapshot()?.players.orEmpty() }

private fun com.deuterium.backend.repository.PresenceSnapshot.isStale(): Boolean =
    Duration.between(updatedAt, Instant.now()).toMillis() >= PresenceRefreshStaleMillis

private suspend fun <T> mapBridge(block: suspend () -> T): T =
    try {
        block()
    } catch (e: PluginBridgeUnavailable) {
        throw ApiException("PLUGIN_BRIDGE_UNAVAILABLE", "服务器连接暂不可用，请稍后再试。", 503)
    } catch (e: PluginBridgeTimeout) {
        throw e
    }

private fun validateVerification(services: ApplicationServices, verification: com.deuterium.backend.model.VerificationRecord, code: String) {
    val now = Instant.now()
    if (verification.consumedAt != null) throw ApiException("VERIFICATION_INVALID", "验证码错误或已失效。", 422)
    if (verification.expiresAt.isBefore(now)) throw ApiException("VERIFICATION_EXPIRED", "验证码已过期，请重新获取。", 422)
    if (verification.attempts >= verification.maxAttempts) throw ApiException("VERIFICATION_ATTEMPTS_EXCEEDED", "验证码尝试次数已耗尽，请重新获取。", 422)
    if (verification.codeHash != Secrets.sha256(code, services.config.security.verificationPepper)) {
        services.verifications.incrementAttempts(verification.id)
        if (verification.attempts + 1 >= verification.maxAttempts) {
            services.verifications.consume(verification.id)
            throw ApiException("VERIFICATION_ATTEMPTS_EXCEEDED", "验证码尝试次数已耗尽，请重新获取。", 422)
        }
        throw ApiException("VERIFICATION_INVALID", "验证码错误。", 422)
    }
}

private fun issueAuth(services: ApplicationServices, user: com.deuterium.backend.model.RegisteredUserRecord): AuthData {
    val token = Ids.token("session")
    val tokenHash = Secrets.sha256(token, services.config.security.sessionTokenPepper)
    services.sessions.create(user, tokenHash)
    val current = com.deuterium.backend.model.CurrentUser(user.userId, user.serverUuid, user.gameId, user.qq)
    return AuthData(token, services.accounts.profile(current, services.playerRefs))
}

private suspend fun ApplicationCall.requireUser(services: ApplicationServices): com.deuterium.backend.model.CurrentUser {
    val token = requireToken()
    return dbQuery {
        services.sessions.authenticate(Secrets.sha256(token, services.config.security.sessionTokenPepper))
            ?: throw ApiException("UNAUTHORIZED", "请先登录。", 401)
    }
}

private fun ApplicationCall.requireToken(): String =
    bearerToken() ?: throw ApiException("UNAUTHORIZED", "请先登录。", 401)
