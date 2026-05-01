package com.deuterium.backend.bridge

import com.deuterium.backend.chat.AppChatHub
import com.deuterium.backend.config.PluginBridgeConfig
import com.deuterium.backend.model.OnlinePlayer
import com.deuterium.backend.repository.AccountRepository
import com.deuterium.backend.repository.ChatRepository
import com.deuterium.backend.repository.PlayerRefRepository
import com.deuterium.backend.repository.WalletRepository
import com.deuterium.backend.util.Ids
import com.deuterium.backend.web.PluginBridgeTimeout
import com.deuterium.backend.web.PluginBridgeUnavailable
import com.deuterium.backend.web.dbQuery
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class WebSocketPluginBridge(
    private val config: PluginBridgeConfig,
    private val json: Json,
    private val chatRepository: ChatRepository,
    private val playerRefs: PlayerRefRepository,
    private val accounts: AccountRepository,
    private val wallet: WalletRepository,
    private val chatHub: AppChatHub,
) : PluginBridge {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mutex = Mutex()
    private var session: DefaultWebSocketServerSession? = null
    private var lastSeenAt: Instant? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<BridgeEnvelope>>()
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<BridgeEnvelope>(Channel.UNLIMITED)

    init {
        eventScope.launch {
            for (event in events) {
                runCatching { processEvent(event) }
                    .onFailure { logger.error("Plugin bridge event failed type={}", event.type, it) }
            }
        }
    }

    override fun isAvailable(): Boolean {
        val connected = session != null
        val lastSeen = lastSeenAt ?: return false
        return connected && Instant.now().minusMillis(config.staleAfterMillis).isBefore(lastSeen)
    }

    suspend fun accept(newSession: DefaultWebSocketServerSession) {
        mutex.withLock {
            session?.close()
            session = newSession
            lastSeenAt = Instant.now()
        }
        logger.info("Plugin bridge connected")
        try {
            newSession.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    handleIncoming(frame.readText())
                }
            }
        } finally {
            mutex.withLock {
                if (session == newSession) {
                    session = null
                }
            }
            pending.values.forEach { it.completeExceptionally(PluginBridgeUnavailable()) }
            pending.clear()
            logger.info("Plugin bridge disconnected")
        }
    }

    suspend fun sendPing() {
        val active = mutex.withLock { session }
        if (active != null) {
            val envelope = BridgeEnvelope(
                type = "bridge.ping",
                messageId = Ids.bridgeMessageId(),
                sentAt = Instant.now().toString(),
                payload = buildJsonObject { }
            )
            runCatching { active.send(json.encodeToString(BridgeEnvelope.serializer(), envelope)) }
        }
    }

    override suspend fun deliverVerification(
        verificationId: String,
        purpose: String,
        gameId: String,
        code: String,
        expiresAt: Instant,
    ): VerificationDelivery {
        val payload = buildJsonObject {
            put("verificationId", verificationId)
            put("purpose", purpose)
            put("gameId", gameId)
            put("code", code)
            put("expiresAt", expiresAt.toString())
        }
        val response = request("verification.deliver.request", payload)
        val p = response.payload.jsonObject
        return VerificationDelivery(
            status = p.string("status"),
            serverUuid = p.stringOrNull("serverUuid"),
            currentGameId = p.stringOrNull("currentGameId")
        )
    }

    override suspend fun resolvePlayer(gameId: String, purpose: String): PlayerResolution {
        val response = request("player.resolve.request", buildJsonObject {
            put("gameId", gameId)
            put("purpose", purpose)
        })
        val p = response.payload.jsonObject
        return PlayerResolution(
            status = p.string("status"),
            serverUuid = p.stringOrNull("serverUuid"),
            currentGameId = p.stringOrNull("currentGameId"),
            online = p.booleanOrFalse("online")
        )
    }

    override suspend fun walletBalance(serverUuid: String): WalletBalanceResult {
        val response = request("wallet.balance.request", buildJsonObject {
            put("serverUuid", serverUuid)
        })
        val p = response.payload.jsonObject
        return WalletBalanceResult(
            status = p.string("status"),
            amount = p.stringOrNull("amount")?.toBigDecimal(),
            currency = p.stringOrNull("currency") ?: "CREDIT",
            currentGameId = p.stringOrNull("currentGameId")
        )
    }

    override suspend fun walletTransfer(request: WalletTransferRequest): WalletTransferResult {
        val response = request("wallet.transfer.request", buildJsonObject {
            put("transferId", request.transferId)
            put("idempotencyKey", request.idempotencyKey)
            put("fromServerUuid", request.fromServerUuid)
            put("toServerUuid", request.toServerUuid)
            put("amount", request.amount.setScale(2).toPlainString())
            put("currency", request.currency)
            request.note?.let { put("note", it) }
        })
        val p = response.payload.jsonObject
        return WalletTransferResult(status = p.string("status"), reason = p.stringOrNull("reason"))
    }

    override suspend fun sendAppChat(request: AppChatRequest): AppChatResult {
        val response = request("chat.appMessage.request", buildJsonObject {
            put("appMessageId", request.appMessageId)
            put("senderServerUuid", request.senderServerUuid)
            put("senderGameId", request.senderGameId)
            put("content", request.content)
        })
        return AppChatResult(response.payload.jsonObject.string("status"))
    }

    override suspend fun presenceList(): PresenceListResult {
        val response = request("presence.list.request", buildJsonObject { })
        val p = response.payload.jsonObject
        val players = p["players"]?.jsonArray?.map {
            val item = it.jsonObject
            BridgeOnlinePlayer(
                serverUuid = item.string("serverUuid"),
                currentGameId = item.string("currentGameId"),
                onlineSince = item.stringOrNull("onlineSince")?.let(Instant::parse)
            )
        } ?: emptyList()
        return PresenceListResult(
            status = p.string("status"),
            onlineCount = p["onlineCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: players.size,
            players = players
        )
    }

    private suspend fun request(type: String, payload: JsonElement): BridgeEnvelope {
        val active = mutex.withLock { session } ?: throw PluginBridgeUnavailable()
        if (!isAvailable()) throw PluginBridgeUnavailable()
        val messageId = Ids.bridgeMessageId()
        val envelope = BridgeEnvelope(
            type = type,
            messageId = messageId,
            sentAt = Instant.now().toString(),
            payload = payload
        )
        val deferred = CompletableDeferred<BridgeEnvelope>()
        pending[messageId] = deferred
        try {
            active.send(json.encodeToString(BridgeEnvelope.serializer(), envelope))
            return withTimeout(config.requestTimeoutMillis) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            throw PluginBridgeTimeout()
        } finally {
            pending.remove(messageId)
        }
    }

    private suspend fun handleIncoming(text: String) {
        val envelope = json.decodeFromString(BridgeEnvelope.serializer(), text)
        lastSeenAt = Instant.now()
        if (envelope.replyTo != null) {
            pending.remove(envelope.replyTo)?.complete(envelope)
            return
        }
        when (envelope.type) {
            "bridge.ping" -> sendPong(envelope)
            "bridge.pong" -> Unit
            else -> {
                val queued = events.trySend(envelope)
                if (queued.isFailure) {
                    logger.error("Plugin bridge event queue rejected type={}", envelope.type)
                }
            }
        }
    }

    private suspend fun processEvent(envelope: BridgeEnvelope) {
        when (envelope.type) {
            "chat.serverMessage.event" -> handleServerChat(envelope)
            "server.event" -> handleServerEvent(envelope)
            "presence.snapshot.event" -> handlePresenceSnapshot(envelope)
            "wallet.pay.event" -> handleWalletPayEvent(envelope)
            "wallet.balanceChange.event" -> handleWalletBalanceChangeEvent(envelope)
            else -> logger.warn("Unknown plugin bridge event type={}", envelope.type)
        }
    }

    private suspend fun sendPong(ping: BridgeEnvelope) {
        val active = mutex.withLock { session } ?: return
        active.send(
            json.encodeToString(
                BridgeEnvelope(
                    type = "bridge.pong",
                    messageId = Ids.bridgeMessageId(),
                    replyTo = ping.messageId,
                    sentAt = Instant.now().toString(),
                    payload = buildJsonObject { }
                )
            )
        )
    }

    private suspend fun handleServerChat(envelope: BridgeEnvelope) {
        val p = envelope.payload.jsonObject
        val message = dbQuery {
            chatRepository.insertChatMessage(
                messageId = Ids.messageId(),
                serverUuid = p.string("serverUuid"),
                gameId = p.string("currentGameId"),
                content = p.string("content").take(256),
                sentAt = p.stringOrNull("occurredAt")?.let(Instant::parse) ?: Instant.now()
            )
        }
        chatHub.broadcastMessage(message)
    }

    private suspend fun handleServerEvent(envelope: BridgeEnvelope) {
        val p = envelope.payload.jsonObject
        val event = dbQuery {
            chatRepository.insertServerEvent(
                eventId = Ids.eventId(),
                eventType = p.string("eventType"),
                content = p.string("content").take(256),
                occurredAt = p.stringOrNull("occurredAt")?.let(Instant::parse) ?: Instant.now()
            )
        }
        chatHub.broadcastServerEvent(event)
    }

    private suspend fun handlePresenceSnapshot(envelope: BridgeEnvelope) {
        val p = envelope.payload.jsonObject
        val items = p["players"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        val players = dbQuery {
            val registeredByServerUuid = accounts.findByServerUuids(items.map { it.string("serverUuid") })
            items.map { item ->
                val serverUuid = item.string("serverUuid")
                val registered = registeredByServerUuid[serverUuid]
                val ref = playerRefs.ensurePlayerRef(
                    serverUuid = serverUuid,
                    gameId = registered?.gameId ?: item.string("currentGameId"),
                    qq = registered?.qq,
                    registered = registered != null,
                    online = true,
                    source = "online_list",
                    expiresAt = null
                )
                OnlinePlayer(
                    playerRef = ref.playerRef,
                    gameId = ref.gameId,
                    qq = ref.qq,
                    registered = ref.registered,
                    onlineSince = item.stringOrNull("onlineSince")
                )
            }.also { chatRepository.updatePresence(it, Instant.now()) }
        }
        chatHub.broadcastPresence(players.size, players)
    }

    private suspend fun handleWalletPayEvent(envelope: BridgeEnvelope) {
        val p = envelope.payload.jsonObject
        val payEventId = p.string("payEventId").takeIf { it.isNotBlank() } ?: return
        val amount = p.decimalOrNull("amount") ?: return
        if (amount <= BigDecimal.ZERO) return
        val occurredAt = p.stringOrNull("occurredAt")?.let(Instant::parse) ?: Instant.now()
        val note = p.stringOrNull("note")?.take(80) ?: "服务器内转账"

        val walletEvents = dbQuery {
            val events = mutableListOf<Pair<String, com.deuterium.backend.model.WalletRecord>>()
            val fromServerUuid = p.string("fromServerUuid").takeIf { it.isNotBlank() } ?: return@dbQuery events
            val toServerUuid = p.string("toServerUuid").takeIf { it.isNotBlank() } ?: return@dbQuery events
            val fromGameId = p.string("fromGameId").ifBlank { "unknown" }.take(32)
            val toGameId = p.string("toGameId").ifBlank { "unknown" }.take(32)
            val fromUser = accounts.findByServerUuid(fromServerUuid)
            val toUser = accounts.findByServerUuid(toServerUuid)
            if (fromUser == null && toUser == null) return@dbQuery events

            val fromRef = playerRefs.ensurePlayerRef(
                serverUuid = fromServerUuid,
                gameId = fromUser?.gameId ?: fromGameId,
                qq = fromUser?.qq,
                registered = fromUser != null,
                online = false,
                source = "server_pay",
                expiresAt = null
            )
            val toRef = playerRefs.ensurePlayerRef(
                serverUuid = toServerUuid,
                gameId = toUser?.gameId ?: toGameId,
                qq = toUser?.qq,
                registered = toUser != null,
                online = false,
                source = "server_pay",
                expiresAt = null
            )

            if (fromUser != null) {
                wallet.createRecordIfAbsent(
                    recordId = Ids.walletRecordIdFromExternal(payEventId, "expense"),
                    userId = fromUser.userId,
                    direction = "expense",
                    other = toRef,
                    amount = amount,
                    status = "success",
                    note = note,
                    occurredAt = occurredAt
                )?.let { events.add(fromUser.userId to it) }
                p.decimalOrNull("fromBalanceAfter")?.let { wallet.upsertBalance(fromUser.userId, it) }
            }
            if (toUser != null) {
                wallet.createRecordIfAbsent(
                    recordId = Ids.walletRecordIdFromExternal(payEventId, "income"),
                    userId = toUser.userId,
                    direction = "income",
                    other = fromRef,
                    amount = amount,
                    status = "success",
                    note = note,
                    occurredAt = occurredAt
                )?.let { events.add(toUser.userId to it) }
                p.decimalOrNull("toBalanceAfter")?.let { wallet.upsertBalance(toUser.userId, it) }
            }
            events
        }
        walletEvents.forEach { (userId, record) -> chatHub.sendWalletRecord(userId, record) }
    }

    private suspend fun handleWalletBalanceChangeEvent(envelope: BridgeEnvelope) {
        val p = envelope.payload.jsonObject
        val eventId = p.string("eventId").takeIf { it.isNotBlank() } ?: return
        val userServerUuid = p.string("serverUuid").takeIf { it.isNotBlank() } ?: return
        val direction = p.string("direction").takeIf { it == "income" || it == "expense" } ?: return
        val amount = p.decimalOrNull("amount") ?: return
        if (amount <= BigDecimal.ZERO) return
        val occurredAt = p.stringOrNull("occurredAt")?.let(Instant::parse) ?: Instant.now()
        val source = p.stringOrNull("source")?.ifBlank { "server_economy" }?.take(32) ?: "server_economy"
        val label = p.stringOrNull("note")?.ifBlank { null }?.take(32) ?: walletSourceLabel(source)

        val walletEvents = dbQuery {
            val user = accounts.findByServerUuid(userServerUuid) ?: return@dbQuery emptyList()
            val otherRef = playerRefs.ensurePlayerRef(
                serverUuid = "external:${source.take(60)}",
                gameId = label,
                qq = null,
                registered = false,
                online = false,
                source = source,
                expiresAt = null
            )
            val record = wallet.createRecordIfAbsent(
                recordId = Ids.walletRecordIdFromExternal(eventId, direction),
                userId = user.userId,
                direction = direction,
                other = otherRef,
                amount = amount,
                status = "success",
                note = label,
                occurredAt = occurredAt
            )
            p.decimalOrNull("balanceAfter")?.let { wallet.upsertBalance(user.userId, it) }
            if (record == null) emptyList() else listOf(user.userId to record)
        }
        walletEvents.forEach { (userId, record) -> chatHub.sendWalletRecord(userId, record) }
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.content ?: ""

    private fun kotlinx.serialization.json.JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun kotlinx.serialization.json.JsonObject.decimalOrNull(key: String): BigDecimal? =
        stringOrNull(key)?.let { runCatching { BigDecimal(it) }.getOrNull() }

    private fun kotlinx.serialization.json.JsonObject.booleanOrFalse(key: String): Boolean =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

    private fun walletSourceLabel(source: String): String =
        when (source) {
            "chest_shop" -> "箱子商店"
            "server_pay" -> "服务器内转账"
            else -> "服务器经济变动"
        }
}

@Serializable
data class BridgeEnvelope(
    val type: String,
    val messageId: String,
    val replyTo: String? = null,
    val sentAt: String,
    val payload: JsonElement,
)

