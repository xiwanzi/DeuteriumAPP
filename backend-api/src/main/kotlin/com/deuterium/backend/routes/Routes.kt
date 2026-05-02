package com.deuterium.backend.routes

import com.deuterium.backend.ApplicationServices
import com.deuterium.backend.bridge.AppChatRequest
import com.deuterium.backend.bridge.WalletTransferRequest
import com.deuterium.backend.chat.AppStatePayload
import com.deuterium.backend.chat.AppWsEnvelope
import com.deuterium.backend.chat.SendResultPayload
import com.deuterium.backend.chat.WsErrorPayload
import com.deuterium.backend.model.AppUpdateCheckData
import com.deuterium.backend.model.AuthData
import com.deuterium.backend.model.ChatMessagesData
import com.deuterium.backend.model.ChatSendPayload
import com.deuterium.backend.model.CreateTransferRequest
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
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Routing
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val PresenceRefreshStaleMillis = 15_000L
private const val PresenceRefreshMinIntervalMillis = 5_000L
private val presenceRefreshMutex = Mutex()
private var lastPresenceRefreshStartedAt: Instant? = null

fun Routing.installRoutes(services: ApplicationServices) {
    healthRoutes(services)
    pluginBridgeRoutes(services)
    route("/api/v1") {
        appRoutes(services)
        accountRoutes(services)
        walletRoutes(services)
        chatRoutes(services)
    }
}

fun Routing.installPublicRoutes(services: ApplicationServices) {
    healthRoutes(services)
    route("/api/v1") {
        appRoutes(services)
        accountRoutes(services)
        walletRoutes(services)
        chatRoutes(services)
    }
}

fun Routing.installBridgeRoutes(services: ApplicationServices) {
    pluginBridgeRoutes(services)
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
