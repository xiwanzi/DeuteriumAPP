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

object OidcClients : Table("oidc_clients") {
    val clientId = varchar("client_id", 80)
    val clientSecretHash = varchar("client_secret_hash", 128)
    val redirectUri = varchar("redirect_uri", 512)
    val enabled = bool("enabled")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(clientId)
}

object OidcAuthorizationCodes : Table("oidc_authorization_codes") {
    val id = varchar("id", 40)
    val codeHash = varchar("code_hash", 128).uniqueIndex()
    val userId = varchar("user_id", 40).references(Users.id)
    val clientId = varchar("client_id", 80).references(OidcClients.clientId)
    val redirectUri = varchar("redirect_uri", 512)
    val scope = varchar("scope", 255)
    val nonce = varchar("nonce", 255).nullable()
    val expiresAt = timestamp("expires_at")
    val consumedAt = timestamp("consumed_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object OidcAccessTokens : Table("oidc_access_tokens") {
    val id = varchar("id", 40)
    val tokenHash = varchar("token_hash", 128).uniqueIndex()
    val userId = varchar("user_id", 40).references(Users.id)
    val clientId = varchar("client_id", 80).references(OidcClients.clientId)
    val scope = varchar("scope", 255)
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object OidcWebSessions : Table("oidc_web_sessions") {
    val id = varchar("id", 40)
    val userId = varchar("user_id", 40).references(Users.id)
    val sessionHash = varchar("session_hash", 128).uniqueIndex()
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

object AiPromptVersions : Table("ai_prompt_versions") {
    val id = varchar("id", 40)
    val title = varchar("title", 80)
    val content = text("content")
    val active = bool("active")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object AiModelConfigs : Table("ai_model_configs") {
    val id = varchar("id", 40)
    val provider = varchar("provider", 40)
    val baseUrl = varchar("base_url", 512)
    val model = varchar("model", 80)
    val temperature = decimal("temperature", 4, 2)
    val maxTokens = integer("max_tokens")
    val thinkingEnabled = bool("thinking_enabled")
    val active = bool("active")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object AiSettings : Table("ai_settings") {
    val key = varchar("setting_key", 80)
    val value = text("setting_value")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(key)
}

object AiPlans : Table("ai_plans") {
    val id = varchar("id", 40)
    val code = varchar("code", 40).uniqueIndex()
    val name = varchar("name", 80)
    val description = varchar("description", 255)
    val price = decimal("price", 18, 2)
    val currency = varchar("currency", 16)
    val quotaPerWindow = integer("quota_per_window")
    val quotaWindowHours = integer("quota_window_hours")
    val durationDays = integer("duration_days")
    val modelTier = varchar("model_tier", 32)
    val active = bool("active")
    val sortOrder = integer("sort_order")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object AiEntitlements : Table("ai_entitlements") {
    val userId = varchar("user_id", 40).references(Users.id)
    val planId = varchar("plan_id", 40).nullable()
    val planCode = varchar("plan_code", 40)
    val quotaPerWindow = integer("quota_per_window")
    val quotaWindowHours = integer("quota_window_hours")
    val modelTier = varchar("model_tier", 32)
    val expiresAt = timestamp("expires_at").nullable()
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object AiQuotaUsage : Table("ai_quota_usage") {
    val userId = varchar("user_id", 40).references(Users.id)
    val windowStartedAt = timestamp("window_started_at")
    val used = integer("used")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(userId, windowStartedAt)
}

object AiPurchases : Table("ai_purchases") {
    val id = varchar("id", 40)
    val clientRequestId = varchar("client_request_id", 128)
    val userId = varchar("user_id", 40).references(Users.id)
    val planId = varchar("plan_id", 40)
    val amount = decimal("amount", 18, 2)
    val currency = varchar("currency", 16)
    val status = varchar("status", 20)
    val failureCode = varchar("failure_code", 64).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
    init {
        uniqueIndex(userId, clientRequestId)
    }
}

object AiConversations : Table("ai_conversations") {
    val id = varchar("id", 40)
    val userId = varchar("user_id", 40).references(Users.id)
    val status = varchar("status", 20)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object AiMessages : Table("ai_messages") {
    val id = varchar("id", 40)
    val conversationId = varchar("conversation_id", 40).references(AiConversations.id)
    val userId = varchar("user_id", 40).references(Users.id)
    val clientMessageId = varchar("client_message_id", 128).nullable()
    val role = varchar("role", 20)
    val content = text("content")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
    init {
        uniqueIndex(userId, clientMessageId)
    }
}

object AiRequestExchanges : Table("ai_request_exchanges") {
    val id = varchar("id", 40)
    val userId = varchar("user_id", 40).references(Users.id)
    val clientMessageId = varchar("client_message_id", 128)
    val conversationId = varchar("conversation_id", 40).nullable()
    val userMessageId = varchar("user_message_id", 40).nullable()
    val assistantMessageId = varchar("assistant_message_id", 40).nullable()
    val status = varchar("status", 20)
    val errorCode = varchar("error_code", 80).nullable()
    val errorMessage = text("error_message").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
    init {
        uniqueIndex(userId, clientMessageId)
    }
}

object AiMemoryItems : Table("ai_memory_items") {
    val id = varchar("id", 40)
    val userId = varchar("user_id", 40).references(Users.id)
    val content = text("content")
    val kind = varchar("kind", 32)
    val weight = decimal("weight", 5, 2)
    val status = varchar("status", 20)
    val sourceValue = varchar("source", 32)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object AiKnowledgeItems : Table("ai_knowledge_items") {
    val id = varchar("id", 40)
    val category = varchar("category", 80)
    val title = varchar("title", 120)
    val keywords = text("keywords")
    val content = text("content")
    val weight = decimal("weight", 5, 2)
    val active = bool("active")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object AiKnowledgeDocuments : Table("ai_knowledge_documents") {
    val id = varchar("id", 40)
    val sourceUrl = varchar("source_url", 512)
    val category = varchar("category", 80)
    val title = varchar("title", 160)
    val content = text("content")
    val checksum = varchar("checksum", 128)
    val trustLevel = integer("trust_level")
    val active = bool("active")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object AiKnowledgeChunks : Table("ai_knowledge_chunks") {
    val id = varchar("id", 40)
    val documentId = varchar("document_id", 40).references(AiKnowledgeDocuments.id)
    val sourceUrl = varchar("source_url", 512)
    val category = varchar("category", 80)
    val title = varchar("title", 160)
    val headingPath = varchar("heading_path", 255)
    val chunkText = text("chunk_text")
    val keywords = text("keywords")
    val weight = decimal("weight", 5, 2)
    val active = bool("active")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object AiRequestAudits : Table("ai_request_audits") {
    val id = varchar("id", 40)
    val userId = varchar("user_id", 40).nullable()
    val channel = varchar("channel", 32)
    val requestText = text("request_text")
    val responseText = text("response_text").nullable()
    val riskCode = varchar("risk_code", 64).nullable()
    val status = varchar("status", 20)
    val model = varchar("model", 80).nullable()
    val tokenEstimate = integer("token_estimate").nullable()
    val providerStatusCode = integer("provider_status_code").nullable()
    val providerError = text("provider_error").nullable()
    val firstTokenLatencyMs = integer("first_token_latency_ms").nullable()
    val totalLatencyMs = integer("total_latency_ms").nullable()
    val retryCount = integer("retry_count").nullable()
    val emittedDelta = bool("emitted_delta").nullable()
    val knowledgeQuery = text("knowledge_query").nullable()
    val knowledgeSources = text("knowledge_sources").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AiQqGroups : Table("ai_qq_groups") {
    val groupId = varchar("group_id", 40)
    val enabled = bool("enabled")
    val triggerMode = varchar("trigger_mode", 20)
    val triggerPattern = varchar("trigger_pattern", 255)
    val quotaPerWindow = integer("quota_per_window")
    val windowMinutes = integer("window_minutes")
    val mutedUntil = timestamp("muted_until").nullable()
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(groupId)
}

object AiAdminEvents : Table("ai_admin_events") {
    val id = varchar("id", 40)
    val actor = varchar("actor", 80)
    val action = varchar("action", 80)
    val target = varchar("target", 120).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
