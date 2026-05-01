package com.deuterium.backend.chat

import com.deuterium.backend.model.ChatMessage
import com.deuterium.backend.model.OnlinePlayer
import com.deuterium.backend.model.ServerEvent
import com.deuterium.backend.model.WalletRecord
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

class AppChatHub(private val json: Json) {
    private companion object {
        const val SendTimeoutMillis = 1_500L
    }

    private val mutex = Mutex()
    private val sessions = mutableMapOf<DefaultWebSocketServerSession, ChatClient>()

    suspend fun add(session: DefaultWebSocketServerSession, userId: String, includeServerEvents: Boolean) {
        mutex.withLock {
            sessions[session] = ChatClient(session, userId, includeServerEvents, foreground = false)
        }
    }

    suspend fun remove(session: DefaultWebSocketServerSession) {
        mutex.withLock {
            sessions.remove(session)
        }
    }

    suspend fun updateForeground(session: DefaultWebSocketServerSession, foreground: Boolean) {
        mutex.withLock {
            val client = sessions[session] ?: return@withLock
            sessions[session] = client.copy(foreground = foreground)
        }
    }

    suspend fun anyForeground(userId: String): Boolean =
        mutex.withLock {
            sessions.values.any { it.userId == userId && it.foreground }
        }

    suspend fun activeUserStates(): Map<String, Boolean> =
        mutex.withLock {
            sessions.values
                .groupBy { it.userId }
                .mapValues { (_, clients) -> clients.any { it.foreground } }
        }

    suspend fun broadcastMessage(message: ChatMessage) {
        broadcast(
            type = "chat.message",
            payload = mapOf("message" to message),
            includeEventsOnly = false
        )
    }

    suspend fun broadcastServerEvent(event: ServerEvent) {
        broadcast(
            type = "server.event",
            payload = mapOf("event" to event),
            includeEventsOnly = true
        )
    }

    suspend fun broadcastPresence(onlineCount: Int, players: List<OnlinePlayer>? = null) {
        val payload = if (players == null) {
            PresencePayload(onlineCount = onlineCount, players = null)
        } else {
            PresencePayload(onlineCount = onlineCount, players = players)
        }
        val envelope = AppWsEnvelope(
            type = "presence.update",
            sentAt = Instant.now().toString(),
            payload = json.encodeToJsonElement(PresencePayload.serializer(), payload)
        )
        sendToAll(envelope, includeEventsOnly = false)
    }

    suspend fun sendWalletRecord(userId: String, record: WalletRecord) {
        val envelope = AppWsEnvelope(
            type = "wallet.record.event",
            sentAt = Instant.now().toString(),
            payload = json.encodeToJsonElement(WalletRecordPayload.serializer(), WalletRecordPayload(record))
        )
        sendToUser(userId, envelope)
    }

    suspend fun sendMention(userIds: Collection<String>, message: ChatMessage) {
        val targets = userIds.filter { it.isNotBlank() }.toSet()
        if (targets.isEmpty()) return
        val envelope = AppWsEnvelope(
            type = "chat.mention.event",
            sentAt = Instant.now().toString(),
            payload = json.encodeToJsonElement(ChatMessagePayload.serializer(), ChatMessagePayload(message))
        )
        sendToUsers(targets, envelope)
    }

    suspend fun sendResult(session: DefaultWebSocketServerSession, requestId: String?, payload: SendResultPayload) {
        session.send(
            json.encodeToString(
                AppWsEnvelope(
                    type = "chat.send.result",
                    requestId = requestId,
                    sentAt = Instant.now().toString(),
                    payload = json.encodeToJsonElement(SendResultPayload.serializer(), payload)
                )
            )
        )
    }

    suspend fun sendError(session: DefaultWebSocketServerSession, requestId: String?, code: String, message: String) {
        session.send(
            json.encodeToString(
                AppWsEnvelope(
                    type = "error",
                    requestId = requestId,
                    sentAt = Instant.now().toString(),
                    payload = json.encodeToJsonElement(WsErrorPayload.serializer(), WsErrorPayload(code, message))
                )
            )
        )
    }

    private suspend fun broadcast(type: String, payload: Any, includeEventsOnly: Boolean) {
        val payloadElement = when (payload) {
            is Map<*, *> -> {
                when (val value = payload.values.first()) {
                    is ChatMessage -> json.encodeToJsonElement(ChatMessagePayload.serializer(), ChatMessagePayload(value))
                    is ServerEvent -> json.encodeToJsonElement(ServerEventPayload.serializer(), ServerEventPayload(value))
                    else -> error("Unsupported payload")
                }
            }
            else -> error("Unsupported payload")
        }
        sendToAll(
            AppWsEnvelope(
                type = type,
                sentAt = Instant.now().toString(),
                payload = payloadElement
            ),
            includeEventsOnly = includeEventsOnly
        )
    }

    private suspend fun sendToAll(envelope: AppWsEnvelope, includeEventsOnly: Boolean) {
        val snapshot = mutex.withLock { sessions.values.toList() }
        val body = json.encodeToString(envelope)
        val failed = coroutineScope {
            snapshot
                .filter { !includeEventsOnly || it.includeServerEvents }
                .map { client -> async { if (sendFrame(client.session, body)) null else client.session } }
                .awaitAll()
                .filterNotNull()
        }
        removeFailed(failed)
    }

    private suspend fun sendToUser(userId: String, envelope: AppWsEnvelope) {
        val snapshot = mutex.withLock { sessions.values.filter { it.userId == userId } }
        val body = json.encodeToString(envelope)
        val failed = coroutineScope {
            snapshot
                .map { client -> async { if (sendFrame(client.session, body)) null else client.session } }
                .awaitAll()
                .filterNotNull()
        }
        removeFailed(failed)
    }

    private suspend fun sendToUsers(userIds: Set<String>, envelope: AppWsEnvelope) {
        val snapshot = mutex.withLock { sessions.values.filter { it.userId in userIds } }
        val body = json.encodeToString(envelope)
        val failed = coroutineScope {
            snapshot
                .map { client -> async { if (sendFrame(client.session, body)) null else client.session } }
                .awaitAll()
                .filterNotNull()
        }
        removeFailed(failed)
    }

    private suspend fun sendFrame(session: DefaultWebSocketServerSession, body: String): Boolean =
        runCatching {
            withTimeout(SendTimeoutMillis) {
                session.send(Frame.Text(body))
            }
        }.isSuccess

    private suspend fun removeFailed(failed: List<DefaultWebSocketServerSession>) {
        if (failed.isEmpty()) return
        mutex.withLock {
            failed.forEach { sessions.remove(it) }
        }
    }
}

private data class ChatClient(
    val session: DefaultWebSocketServerSession,
    val userId: String,
    val includeServerEvents: Boolean,
    val foreground: Boolean,
)

@Serializable
data class AppWsEnvelope(
    val type: String,
    val requestId: String? = null,
    val sentAt: String,
    val payload: kotlinx.serialization.json.JsonElement,
)

@Serializable
data class ChatMessagePayload(val message: ChatMessage)

@Serializable
data class ServerEventPayload(val event: ServerEvent)

@Serializable
data class PresencePayload(val onlineCount: Int, val players: List<OnlinePlayer>? = null)

@Serializable
data class AppStatePayload(val foreground: Boolean)

@Serializable
data class WalletRecordPayload(val record: WalletRecord)

@Serializable
data class SendResultPayload(
    val clientMessageId: String,
    val status: String,
    val messageId: String? = null,
    val error: WsErrorPayload? = null,
)

@Serializable
data class WsErrorPayload(val code: String, val message: String)

