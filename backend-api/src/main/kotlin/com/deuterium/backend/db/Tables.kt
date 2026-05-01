package com.deuterium.backend.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Users : Table("app_users") {
    val id = varchar("id", 40)
    val serverUuid = varchar("server_uuid", 80).uniqueIndex()
    val currentGameId = varchar("current_game_id", 32)
    val qq = varchar("qq", 20).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val status = varchar("status", 20)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Sessions : Table("sessions") {
    val id = varchar("id", 40)
    val userId = varchar("user_id", 40).references(Users.id)
    val tokenHash = varchar("token_hash", 128).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object VerificationRequests : Table("verification_requests") {
    val id = varchar("id", 40)
    val tokenHash = varchar("token_hash", 128).uniqueIndex()
    val purpose = varchar("purpose", 32)
    val serverUuid = varchar("server_uuid", 80)
    val gameId = varchar("game_id", 32)
    val qq = varchar("qq", 20).nullable()
    val codeHash = varchar("code_hash", 128)
    val expiresAt = timestamp("expires_at")
    val resendAvailableAt = timestamp("resend_available_at")
    val attempts = integer("attempts")
    val maxAttempts = integer("max_attempts")
    val consumedAt = timestamp("consumed_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object LoginFailures : Table("login_failures") {
    val failureKey = varchar("failure_key", 160)
    val attempts = integer("attempts")
    val lockedUntil = timestamp("locked_until").nullable()
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(failureKey)
}

object PlayerRefs : Table("player_refs") {
    val playerRef = varchar("player_ref", 64)
    val serverUuid = varchar("server_uuid", 80)
    val currentGameId = varchar("current_game_id", 32)
    val qq = varchar("qq", 20).nullable()
    val registered = bool("registered")
    val online = bool("online")
    val sourceValue = varchar("source", 32)
    val confirmedAt = timestamp("confirmed_at")
    val expiresAt = timestamp("expires_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(playerRef)
}

object WalletBalances : Table("wallet_balances") {
    val userId = varchar("user_id", 40).references(Users.id)
    val amount = decimal("amount", 18, 2)
    val currency = varchar("currency", 16)
    val fresh = bool("fresh")
    val refreshedAt = timestamp("refreshed_at").nullable()
    override val primaryKey = PrimaryKey(userId)
}

object Transfers : Table("transfers") {
    val id = varchar("id", 40)
    val clientRequestId = varchar("client_request_id", 128)
    val userId = varchar("user_id", 40).references(Users.id)
    val requestFingerprint = varchar("request_fingerprint", 128)
    val fromServerUuid = varchar("from_server_uuid", 80)
    val toServerUuid = varchar("to_server_uuid", 80)
    val recipientGameId = varchar("recipient_game_id", 32)
    val recipientQq = varchar("recipient_qq", 20).nullable()
    val amount = decimal("amount", 18, 2)
    val currency = varchar("currency", 16)
    val note = varchar("note", 80).nullable()
    val status = varchar("status", 20)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object WalletRecords : Table("wallet_records") {
    val id = varchar("id", 40)
    val userId = varchar("user_id", 40).references(Users.id)
    val direction = varchar("direction", 16)
    val otherServerUuid = varchar("other_server_uuid", 80)
    val otherGameId = varchar("other_game_id", 32)
    val otherQq = varchar("other_qq", 20).nullable()
    val amount = decimal("amount", 18, 2)
    val currency = varchar("currency", 16)
    val status = varchar("status", 20)
    val note = varchar("note", 80).nullable()
    val occurredAt = timestamp("occurred_at")
    override val primaryKey = PrimaryKey(id)
}

object ChatMessages : Table("chat_messages") {
    val id = varchar("id", 40)
    val senderServerUuid = varchar("sender_server_uuid", 80)
    val senderGameId = varchar("sender_game_id", 32)
    val content = varchar("content", 256)
    val kind = varchar("kind", 32)
    val sentAt = timestamp("sent_at")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ServerEvents : Table("server_events") {
    val id = varchar("id", 40)
    val eventType = varchar("event_type", 32)
    val content = varchar("content", 256)
    val occurredAt = timestamp("occurred_at")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PresenceSnapshots : Table("presence_snapshots") {
    val id = integer("id")
    val onlineCount = integer("online_count")
    val playersJson = text("players_json")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object AppPresence : Table("app_presence") {
    val userId = varchar("user_id", 40).references(Users.id)
    val foreground = bool("foreground")
    val lastForegroundAt = timestamp("last_foreground_at").nullable()
    val lastSeenAt = timestamp("last_seen_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object PlayerFollows : Table("player_follows") {
    val userId = varchar("user_id", 40).references(Users.id)
    val targetServerUuid = varchar("target_server_uuid", 80)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(userId, targetServerUuid)
}

