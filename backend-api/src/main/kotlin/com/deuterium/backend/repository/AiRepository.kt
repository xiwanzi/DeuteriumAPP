package com.deuterium.backend.repository

import com.deuterium.backend.db.AiConversations
import com.deuterium.backend.db.AiEntitlements
import com.deuterium.backend.db.AiKnowledgeItems
import com.deuterium.backend.db.AiKnowledgeChunks
import com.deuterium.backend.db.AiKnowledgeDocuments
import com.deuterium.backend.db.AiMemoryItems
import com.deuterium.backend.db.AiMessages
import com.deuterium.backend.db.AiModelConfigs
import com.deuterium.backend.db.AiPlans
import com.deuterium.backend.db.AiPromptVersions
import com.deuterium.backend.db.AiPurchases
import com.deuterium.backend.db.AiQqGroups
import com.deuterium.backend.db.AiQuotaUsage
import com.deuterium.backend.db.AiRequestAudits
import com.deuterium.backend.db.AiRequestExchanges
import com.deuterium.backend.db.AiSettings
import com.deuterium.backend.db.Users
import com.deuterium.backend.model.AiConversationState
import com.deuterium.backend.model.AiMessage
import com.deuterium.backend.model.AiPlan
import com.deuterium.backend.model.AiPurchase
import com.deuterium.backend.model.AiQuota
import com.deuterium.backend.model.iso
import com.deuterium.backend.model.money
import com.deuterium.backend.util.Ids
import com.deuterium.backend.util.Secrets
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class AiRepository(
    private val quotaWindowHours: Long,
) {
    fun listPlans(includeInactive: Boolean = false): List<AiPlanRecord> {
        val query = AiPlans.selectAll()
        val rows = if (includeInactive) query else query.where { AiPlans.active eq true }
        return rows
            .orderBy(AiPlans.sortOrder to SortOrder.ASC)
            .map { it.toAiPlanRecord() }
    }

    fun freePlan(): AiPlanRecord =
        AiPlans.selectAll().where { AiPlans.code eq "free" }.single().toAiPlanRecord()

    fun planById(planId: String, includeInactive: Boolean = false): AiPlanRecord? =
        AiPlans.selectAll().where {
            if (includeInactive) {
                AiPlans.id eq planId
            } else {
                (AiPlans.id eq planId) and (AiPlans.active eq true)
            }
        }
            .singleOrNull()
            ?.toAiPlanRecord()

    fun currentPlan(userId: String, now: Instant = Instant.now()): AiPlanRecord {
        val entitlement = AiEntitlements.selectAll()
            .where { AiEntitlements.userId eq userId }
            .singleOrNull()
        if (entitlement == null) return freePlan()
        val expiresAt = entitlement[AiEntitlements.expiresAt]
        if (expiresAt != null && expiresAt.isBefore(now)) return freePlan()
        return AiPlanRecord(
            id = entitlement[AiEntitlements.planId] ?: "plan_free",
            code = entitlement[AiEntitlements.planCode],
            name = entitlement[AiEntitlements.planCode].replaceFirstChar { it.uppercase() },
            description = "当前生效套餐",
            price = BigDecimal.ZERO,
            currency = "CREDIT",
            quotaPerWindow = entitlement[AiEntitlements.quotaPerWindow],
            quotaWindowHours = entitlement[AiEntitlements.quotaWindowHours],
            durationDays = 0,
            modelTier = entitlement[AiEntitlements.modelTier],
            active = true
        )
    }

    fun quota(userId: String, plan: AiPlanRecord, now: Instant = Instant.now()): AiQuota {
        val windowStart = quotaWindowStart(now, plan.quotaWindowHours)
        val used = AiQuotaUsage.selectAll()
            .where { (AiQuotaUsage.userId eq userId) and (AiQuotaUsage.windowStartedAt eq windowStart) }
            .singleOrNull()
            ?.get(AiQuotaUsage.used) ?: 0
        val limit = plan.quotaPerWindow
        return AiQuota(
            used = used,
            limit = limit,
            remaining = (limit - used).coerceAtLeast(0),
            windowHours = plan.quotaWindowHours,
            resetsAt = windowStart.plus(plan.quotaWindowHours.toLong(), ChronoUnit.HOURS).iso()
        )
    }

    fun consumeQuota(userId: String, plan: AiPlanRecord, now: Instant = Instant.now()): AiQuota {
        val before = quota(userId, plan, now)
        if (before.remaining <= 0) return before
        val windowStart = quotaWindowStart(now, plan.quotaWindowHours)
        try {
            AiQuotaUsage.insert {
                it[AiQuotaUsage.userId] = userId
                it[windowStartedAt] = windowStart
                it[used] = 1
                it[updatedAt] = now
            }
        } catch (e: ExposedSQLException) {
            AiQuotaUsage.update({ (AiQuotaUsage.userId eq userId) and (AiQuotaUsage.windowStartedAt eq windowStart) }) {
                with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                    it.update(used, used + 1)
                }
                it[updatedAt] = now
            }
        }
        return quota(userId, plan, now)
    }

    fun currentConversation(userId: String, now: Instant = Instant.now()): AiConversationRecord {
        val existing = AiConversations.selectAll()
            .where { (AiConversations.userId eq userId) and (AiConversations.status eq "active") }
            .orderBy(AiConversations.updatedAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toConversationRecord()
        if (existing != null) return existing
        return createConversation(userId, now)
    }

    fun resetConversation(userId: String, now: Instant = Instant.now()): AiConversationRecord {
        AiConversations.update({ (AiConversations.userId eq userId) and (AiConversations.status eq "active") }) {
            it[status] = "archived"
            it[updatedAt] = now
        }
        return createConversation(userId, now)
    }

    fun listMessages(conversationId: String, limit: Int): List<AiMessage> =
        AiMessages.selectAll()
            .where { AiMessages.conversationId eq conversationId }
            .orderBy(AiMessages.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toAiMessage() }
            .asReversed()

    fun appendMessage(
        conversationId: String,
        userId: String,
        role: String,
        content: String,
        now: Instant = Instant.now(),
        messageId: String = Ids.aiMessageId(),
        clientMessageId: String? = null,
    ): AiMessage {
        val id = messageId
        AiMessages.insert {
            it[AiMessages.id] = id
            it[AiMessages.conversationId] = conversationId
            it[AiMessages.userId] = userId
            it[AiMessages.clientMessageId] = clientMessageId
            it[AiMessages.role] = role
            it[AiMessages.content] = content
            it[createdAt] = now
        }
        AiConversations.update({ AiConversations.id eq conversationId }) {
            it[updatedAt] = now
        }
        return AiMessage(id, conversationId, role, content, now.iso())
    }

    fun beginRequestExchange(userId: String, clientMessageId: String, now: Instant = Instant.now()): AiExchangeBeginRecord {
        val normalized = clientMessageId.trim()
        if (normalized.isBlank()) {
            return AiExchangeBeginRecord(
                AiRequestExchangeRecord(
                    id = "",
                    userId = userId,
                    clientMessageId = normalized,
                    conversationId = null,
                    userMessageId = null,
                    assistantMessageId = null,
                    status = "new",
                    errorCode = null,
                    errorMessage = null,
                    createdAt = now,
                    updatedAt = now
                ),
                created = true
            )
        }
        return try {
            val id = Ids.aiRequestExchangeId()
            AiRequestExchanges.insert {
                it[AiRequestExchanges.id] = id
                it[AiRequestExchanges.userId] = userId
                it[AiRequestExchanges.clientMessageId] = normalized
                it[AiRequestExchanges.status] = "pending"
                it[AiRequestExchanges.createdAt] = now
                it[AiRequestExchanges.updatedAt] = now
            }
            AiExchangeBeginRecord(requestExchangeById(id)!!, created = true)
        } catch (e: ExposedSQLException) {
            val existing = requestExchangeByClientMessage(userId, normalized)
            if (existing != null) {
                AiExchangeBeginRecord(existing, created = false)
            } else {
                throw e
            }
        }
    }

    fun requestExchangeByClientMessage(userId: String, clientMessageId: String): AiRequestExchangeRecord? =
        AiRequestExchanges.selectAll()
            .where {
                (AiRequestExchanges.userId eq userId) and
                    (AiRequestExchanges.clientMessageId eq clientMessageId.trim())
            }
            .singleOrNull()
            ?.toRequestExchangeRecord()

    fun requestExchangeById(id: String): AiRequestExchangeRecord? =
        AiRequestExchanges.selectAll()
            .where { AiRequestExchanges.id eq id }
            .singleOrNull()
            ?.toRequestExchangeRecord()

    fun hasActiveRequestExchange(userId: String, excludeId: String, staleBefore: Instant): Boolean =
        AiRequestExchanges.selectAll()
            .where {
                (AiRequestExchanges.userId eq userId) and
                    (AiRequestExchanges.id neq excludeId) and
                    (AiRequestExchanges.status inList listOf("pending", "streaming")) and
                    (AiRequestExchanges.updatedAt greater staleBefore)
            }
            .limit(1)
            .any()

    fun markStaleRequestExchangesFailed(userId: String, staleBefore: Instant, now: Instant = Instant.now()): Int =
        AiRequestExchanges.update({
            (AiRequestExchanges.userId eq userId) and
                (AiRequestExchanges.status inList listOf("pending", "streaming")) and
                (AiRequestExchanges.updatedAt lessEq staleBefore)
        }) {
            it[AiRequestExchanges.status] = "failed"
            it[AiRequestExchanges.errorCode] = "AI_REQUEST_STALE"
            it[AiRequestExchanges.errorMessage] = "stale processing request"
            it[AiRequestExchanges.updatedAt] = now
        }

    fun touchRequestExchange(id: String, now: Instant = Instant.now()) {
        if (id.isBlank()) return
        AiRequestExchanges.update({
            (AiRequestExchanges.id eq id) and
                (AiRequestExchanges.status inList listOf("pending", "streaming"))
        }) {
            it[AiRequestExchanges.updatedAt] = now
        }
    }

    fun markRequestExchangeStreaming(
        id: String,
        conversationId: String,
        userMessageId: String,
        assistantMessageId: String,
        now: Instant = Instant.now()
    ): Int {
        if (id.isBlank()) return 0
        return AiRequestExchanges.update({
            (AiRequestExchanges.id eq id) and
                (AiRequestExchanges.status inList listOf("pending", "streaming"))
        }) {
            it[AiRequestExchanges.conversationId] = conversationId
            it[AiRequestExchanges.userMessageId] = userMessageId
            it[AiRequestExchanges.assistantMessageId] = assistantMessageId
            it[AiRequestExchanges.status] = "streaming"
            it[AiRequestExchanges.errorCode] = null
            it[AiRequestExchanges.errorMessage] = null
            it[updatedAt] = now
        }
    }

    fun markRequestExchangeCompleted(id: String, assistantMessageId: String, now: Instant = Instant.now()): Int {
        if (id.isBlank()) return 0
        return AiRequestExchanges.update({
            (AiRequestExchanges.id eq id) and
                (AiRequestExchanges.status inList listOf("pending", "streaming"))
        }) {
            it[AiRequestExchanges.assistantMessageId] = assistantMessageId
            it[AiRequestExchanges.status] = "completed"
            it[AiRequestExchanges.errorCode] = null
            it[AiRequestExchanges.errorMessage] = null
            it[updatedAt] = now
        }
    }

    fun markRequestExchangeFailed(id: String, errorCode: String, errorMessage: String, now: Instant = Instant.now()): Int {
        if (id.isBlank()) return 0
        return AiRequestExchanges.update({
            (AiRequestExchanges.id eq id) and
                (AiRequestExchanges.status inList listOf("pending", "streaming"))
        }) {
            it[AiRequestExchanges.status] = "failed"
            it[AiRequestExchanges.errorCode] = errorCode
            it[AiRequestExchanges.errorMessage] = errorMessage.take(1000)
            it[updatedAt] = now
        }
    }

    fun messageById(messageId: String): AiMessage? =
        AiMessages.selectAll()
            .where { AiMessages.id eq messageId }
            .singleOrNull()
            ?.toAiMessage()

    fun completedExchangeByClientMessage(userId: String, clientMessageId: String): AiCompletedExchangeRecord? {
        if (clientMessageId.isBlank()) return null
        val userRow = AiMessages.selectAll()
            .where {
                (AiMessages.userId eq userId) and
                    (AiMessages.clientMessageId eq clientMessageId) and
                    (AiMessages.role eq "user")
            }
            .singleOrNull()
            ?: return null
        val userMessage = userRow.toAiMessage()
        val assistant = AiMessages.selectAll()
            .where {
                (AiMessages.conversationId eq userRow[AiMessages.conversationId]) and
                    (AiMessages.role eq "assistant") and
                    (AiMessages.createdAt greater userRow[AiMessages.createdAt])
            }
            .orderBy(AiMessages.createdAt to SortOrder.ASC)
            .limit(1)
            .singleOrNull()
            ?.toAiMessage()
        return AiCompletedExchangeRecord(userMessage, assistant)
    }

    fun activePromptRecord(): AiPromptRecord =
        AiPromptVersions.selectAll()
            .where { AiPromptVersions.active eq true }
            .orderBy(AiPromptVersions.updatedAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let {
                AiPromptRecord(
                    id = it[AiPromptVersions.id],
                    title = it[AiPromptVersions.title],
                    content = it[AiPromptVersions.content],
                    updatedAt = it[AiPromptVersions.updatedAt]
                )
            }
            ?: AiPromptRecord(
                id = "prompt_default",
                title = "默认提示词",
                content = "你是 xxxAI，Deuterium VIII 服务器的 AI 助手。",
                updatedAt = Instant.EPOCH
            )

    fun activePrompt(): String =
        activePromptRecord().content

    fun overwriteActivePrompt(title: String, content: String, now: Instant = Instant.now()): AiPromptRecord {
        val existing = AiPromptVersions.selectAll()
            .where { AiPromptVersions.active eq true }
            .orderBy(AiPromptVersions.updatedAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
        if (existing == null) {
            AiPromptVersions.insert {
                it[id] = Ids.aiPromptId()
                it[AiPromptVersions.title] = title
                it[AiPromptVersions.content] = content
                it[active] = true
                it[createdAt] = now
                it[updatedAt] = now
            }
        } else {
            AiPromptVersions.update({ AiPromptVersions.id eq existing[AiPromptVersions.id] }) {
                it[AiPromptVersions.title] = title
                it[AiPromptVersions.content] = content
                it[updatedAt] = now
            }
        }
        return activePromptRecord()
    }

    fun assistantName(): String =
        setting("assistant_name", "xxxAI").ifBlank { "xxxAI" }

    fun setting(key: String, default: String): String =
        AiSettings.selectAll()
            .where { AiSettings.key eq key }
            .singleOrNull()
            ?.get(AiSettings.value)
            ?: default

    fun updateSetting(key: String, value: String, now: Instant = Instant.now()) {
        val existing = AiSettings.selectAll().where { AiSettings.key eq key }.singleOrNull()
        if (existing == null) {
            AiSettings.insert {
                it[AiSettings.key] = key
                it[AiSettings.value] = value
                it[updatedAt] = now
            }
        } else {
            AiSettings.update({ AiSettings.key eq key }) {
                it[AiSettings.value] = value
                it[updatedAt] = now
            }
        }
    }

    fun activeModelOrNull(): AiModelConfigRecord? =
        AiModelConfigs.selectAll()
            .where { AiModelConfigs.active eq true }
            .orderBy(AiModelConfigs.updatedAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toModelConfigRecord()

    fun activeModel(): AiModelConfigRecord =
        activeModelOrNull()
            ?: AiModelConfigRecord("deepseek", "https://api.deepseek.com", "deepseek-v4-flash", BigDecimal("0.40"), 900, false)

    fun updateActiveModelConfig(
        provider: String,
        baseUrl: String,
        model: String,
        temperature: BigDecimal,
        maxTokens: Int,
        thinkingEnabled: Boolean,
        now: Instant = Instant.now(),
    ): AiModelConfigRecord {
        val current = AiModelConfigs.selectAll()
            .where { AiModelConfigs.active eq true }
            .orderBy(AiModelConfigs.updatedAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
        if (current == null) {
            AiModelConfigs.insert {
                it[id] = "model_${Ids.requestId()}"
                it[AiModelConfigs.provider] = provider
                it[AiModelConfigs.baseUrl] = baseUrl
                it[AiModelConfigs.model] = model
                it[AiModelConfigs.temperature] = temperature
                it[AiModelConfigs.maxTokens] = maxTokens
                it[AiModelConfigs.thinkingEnabled] = thinkingEnabled
                it[active] = true
                it[createdAt] = now
                it[updatedAt] = now
            }
        } else {
            val id = current[AiModelConfigs.id]
            AiModelConfigs.update({ AiModelConfigs.id eq id }) {
                it[AiModelConfigs.provider] = provider
                it[AiModelConfigs.baseUrl] = baseUrl
                it[AiModelConfigs.model] = model
                it[AiModelConfigs.temperature] = temperature
                it[AiModelConfigs.maxTokens] = maxTokens
                it[AiModelConfigs.thinkingEnabled] = thinkingEnabled
                it[updatedAt] = now
            }
        }
        return activeModel()
    }

    fun relevantMemory(userId: String, limit: Int): List<String> =
        AiMemoryItems.selectAll()
            .where { (AiMemoryItems.userId eq userId) and (AiMemoryItems.status eq "active") }
            .orderBy(AiMemoryItems.weight to SortOrder.DESC, AiMemoryItems.updatedAt to SortOrder.DESC)
            .limit(limit)
            .map { it[AiMemoryItems.content] }

    fun matchingKnowledge(query: String, limit: Int): List<AiKnowledgeRecord> {
        val normalized = query.lowercase()
        val terms = normalized
            .split(Regex("""[\s,，。.!！?？;；:/\\|]+"""))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
        if (terms.isEmpty()) return emptyList()
        return AiKnowledgeItems.selectAll()
            .where { AiKnowledgeItems.active eq true }
            .orderBy(AiKnowledgeItems.weight to SortOrder.DESC, AiKnowledgeItems.updatedAt to SortOrder.DESC)
            .limit(80)
            .map { it.toKnowledgeRecord() }
            .mapNotNull { item ->
                val haystack = "${item.category}\n${item.title}\n${item.keywords}\n${item.content}".lowercase()
                val score = terms.count { haystack.contains(it) } +
                    if (item.keywords.lowercase().splitKeywords().any { normalized.contains(it) }) 2 else 0
                if (score <= 0) null else item to score
            }
            .sortedWith(compareByDescending<Pair<AiKnowledgeRecord, Int>> { it.second }.thenByDescending { it.first.weight })
            .take(limit.coerceIn(0, 10))
            .map { it.first }
    }

    fun searchKnowledgeChunks(query: String, limit: Int): List<AiKnowledgeMatchRecord> {
        val terms = query.extractKnowledgeTerms()
        if (terms.isEmpty() || limit <= 0) return emptyList()
        val normalized = query.lowercase()
        val chunkMatches = AiKnowledgeChunks.selectAll()
            .where { AiKnowledgeChunks.active eq true }
            .orderBy(AiKnowledgeChunks.weight to SortOrder.DESC, AiKnowledgeChunks.updatedAt to SortOrder.DESC)
            .limit(200)
            .map { row ->
                val haystack = listOf(
                    row[AiKnowledgeChunks.category],
                    row[AiKnowledgeChunks.title],
                    row[AiKnowledgeChunks.headingPath],
                    row[AiKnowledgeChunks.keywords],
                    row[AiKnowledgeChunks.chunkText]
                ).joinToString("\n").lowercase()
                val keywordBonus = row[AiKnowledgeChunks.keywords]
                    .splitKeywords()
                    .count { normalized.contains(it) } * 2
                val score = terms.count { haystack.contains(it) } + keywordBonus + row[AiKnowledgeChunks.weight].toInt()
                if (score <= row[AiKnowledgeChunks.weight].toInt()) null else AiKnowledgeMatchRecord(
                    documentId = row[AiKnowledgeChunks.documentId],
                    chunkId = row[AiKnowledgeChunks.id],
                    title = row[AiKnowledgeChunks.title],
                    category = row[AiKnowledgeChunks.category],
                    headingPath = row[AiKnowledgeChunks.headingPath],
                    sourceUrl = row[AiKnowledgeChunks.sourceUrl],
                    text = row[AiKnowledgeChunks.chunkText],
                    score = score
                )
            }
            .filterNotNull()

        val fallbackMatches = if (chunkMatches.isEmpty()) {
            matchingKnowledge(query, limit).mapIndexed { index, item ->
                AiKnowledgeMatchRecord(
                    documentId = "legacy:${item.id}",
                    chunkId = item.id,
                    title = item.title,
                    category = item.category,
                    headingPath = item.title,
                    sourceUrl = item.content.lineSequence()
                        .firstOrNull { it.startsWith("来源：") }
                        ?.removePrefix("来源：")
                        ?.trim()
                        ?: "https://wiki.deuterium.cafe/",
                    text = item.content,
                    score = item.weight.toInt() + limit - index
                )
            }
        } else {
            emptyList()
        }

        return (chunkMatches + fallbackMatches)
            .sortedWith(compareByDescending<AiKnowledgeMatchRecord> { it.score }.thenByDescending { it.text.length })
            .take(limit.coerceIn(1, 12))
    }

    fun listKnowledge(includeInactive: Boolean = true, limit: Int = 80): List<AiKnowledgeRecord> {
        val rows = if (includeInactive) {
            AiKnowledgeItems.selectAll()
        } else {
            AiKnowledgeItems.selectAll().where { AiKnowledgeItems.active eq true }
        }
        return rows
            .orderBy(AiKnowledgeItems.updatedAt to SortOrder.DESC)
            .limit(limit.coerceIn(1, 200))
            .map { it.toKnowledgeRecord() }
    }

    fun upsertKnowledge(
        id: String?,
        category: String,
        title: String,
        keywords: String,
        content: String,
        weight: BigDecimal,
        active: Boolean,
        now: Instant = Instant.now(),
    ): AiKnowledgeRecord {
        val existingId = id?.takeIf { it.isNotBlank() }
        val exists = existingId != null && AiKnowledgeItems.selectAll()
            .where { AiKnowledgeItems.id eq existingId }
            .singleOrNull() != null
        val itemId = if (exists) existingId!! else Ids.aiKnowledgeId()
        if (exists) {
            AiKnowledgeItems.update({ AiKnowledgeItems.id eq itemId }) {
                it[AiKnowledgeItems.category] = category
                it[AiKnowledgeItems.title] = title
                it[AiKnowledgeItems.keywords] = keywords
                it[AiKnowledgeItems.content] = content
                it[AiKnowledgeItems.weight] = weight
                it[AiKnowledgeItems.active] = active
                it[updatedAt] = now
            }
        } else {
            AiKnowledgeItems.insert {
                it[AiKnowledgeItems.id] = itemId
                it[AiKnowledgeItems.category] = category
                it[AiKnowledgeItems.title] = title
                it[AiKnowledgeItems.keywords] = keywords
                it[AiKnowledgeItems.content] = content
                it[AiKnowledgeItems.weight] = weight
                it[AiKnowledgeItems.active] = active
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        val record = AiKnowledgeItems.selectAll().where { AiKnowledgeItems.id eq itemId }.single().toKnowledgeRecord()
        upsertKnowledgeChunkFromItem(record, now)
        return record
    }

    fun deleteKnowledge(id: String) {
        val documentId = "doc_$id"
        AiKnowledgeChunks.deleteWhere { AiKnowledgeChunks.documentId eq documentId }
        AiKnowledgeDocuments.deleteWhere { AiKnowledgeDocuments.id eq documentId }
        AiKnowledgeItems.deleteWhere { AiKnowledgeItems.id eq id }
    }

    private fun upsertKnowledgeChunkFromItem(item: AiKnowledgeRecord, now: Instant) {
        val documentId = "doc_${item.id}"
        val sourceUrl = item.content.lineSequence()
            .firstOrNull { it.startsWith("来源：") }
            ?.removePrefix("来源：")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "https://wiki.deuterium.cafe/"
        val documentExists = AiKnowledgeDocuments.selectAll()
            .where { AiKnowledgeDocuments.id eq documentId }
            .singleOrNull() != null
        if (documentExists) {
            AiKnowledgeDocuments.update({ AiKnowledgeDocuments.id eq documentId }) {
                it[AiKnowledgeDocuments.sourceUrl] = sourceUrl
                it[AiKnowledgeDocuments.category] = item.category
                it[AiKnowledgeDocuments.title] = item.title
                it[AiKnowledgeDocuments.content] = item.content
                it[AiKnowledgeDocuments.checksum] = Secrets.sha256Raw(item.content)
                it[AiKnowledgeDocuments.trustLevel] = 80
                it[AiKnowledgeDocuments.active] = item.active
                it[updatedAt] = now
            }
        } else {
            AiKnowledgeDocuments.insert {
                it[AiKnowledgeDocuments.id] = documentId
                it[AiKnowledgeDocuments.sourceUrl] = sourceUrl
                it[AiKnowledgeDocuments.category] = item.category
                it[AiKnowledgeDocuments.title] = item.title
                it[AiKnowledgeDocuments.content] = item.content
                it[AiKnowledgeDocuments.checksum] = Secrets.sha256Raw(item.content)
                it[AiKnowledgeDocuments.trustLevel] = 80
                it[AiKnowledgeDocuments.active] = item.active
                it[AiKnowledgeDocuments.createdAt] = item.createdAt
                it[AiKnowledgeDocuments.updatedAt] = now
            }
        }

        val chunkId = "chunk_${item.id}_0"
        val chunkExists = AiKnowledgeChunks.selectAll()
            .where { AiKnowledgeChunks.id eq chunkId }
            .singleOrNull() != null
        if (chunkExists) {
            AiKnowledgeChunks.update({ AiKnowledgeChunks.id eq chunkId }) {
                it[AiKnowledgeChunks.documentId] = documentId
                it[AiKnowledgeChunks.sourceUrl] = sourceUrl
                it[AiKnowledgeChunks.category] = item.category
                it[AiKnowledgeChunks.title] = item.title
                it[AiKnowledgeChunks.headingPath] = item.title
                it[AiKnowledgeChunks.chunkText] = item.content
                it[AiKnowledgeChunks.keywords] = item.keywords
                it[AiKnowledgeChunks.weight] = item.weight
                it[AiKnowledgeChunks.active] = item.active
                it[updatedAt] = now
            }
        } else {
            AiKnowledgeChunks.insert {
                it[AiKnowledgeChunks.id] = chunkId
                it[AiKnowledgeChunks.documentId] = documentId
                it[AiKnowledgeChunks.sourceUrl] = sourceUrl
                it[AiKnowledgeChunks.category] = item.category
                it[AiKnowledgeChunks.title] = item.title
                it[AiKnowledgeChunks.headingPath] = item.title
                it[AiKnowledgeChunks.chunkText] = item.content
                it[AiKnowledgeChunks.keywords] = item.keywords
                it[AiKnowledgeChunks.weight] = item.weight
                it[AiKnowledgeChunks.active] = item.active
                it[AiKnowledgeChunks.createdAt] = item.createdAt
                it[AiKnowledgeChunks.updatedAt] = now
            }
        }
    }

    fun listAdminMessages(query: String, limit: Int = 80): List<AiAdminMessageRecord> {
        val rows = AiMessages.selectAll()
            .orderBy(AiMessages.createdAt to SortOrder.DESC)
            .limit(300)
            .map { it }
        val userIds = rows.map { it[AiMessages.userId] }.distinct()
        val users = usersById(userIds)
        val normalized = query.trim().lowercase()
        return rows.map { row ->
            val user = users[row[AiMessages.userId]]
            AiAdminMessageRecord(
                messageId = row[AiMessages.id],
                conversationId = row[AiMessages.conversationId],
                userId = row[AiMessages.userId],
                gameId = user?.gameId.orEmpty(),
                qq = user?.qq.orEmpty(),
                role = row[AiMessages.role],
                content = row[AiMessages.content],
                createdAt = row[AiMessages.createdAt]
            )
        }.filter {
            normalized.isBlank() ||
                it.userId.lowercase().contains(normalized) ||
                it.gameId.lowercase().contains(normalized) ||
                it.qq.lowercase().contains(normalized) ||
                it.conversationId.lowercase().contains(normalized)
        }.take(limit.coerceIn(1, 200))
    }

    fun listAdminAudits(query: String, limit: Int = 80): List<AiAuditRecord> {
        val rows = AiRequestAudits.selectAll()
            .orderBy(AiRequestAudits.createdAt to SortOrder.DESC)
            .limit(300)
            .map { it }
        val users = usersById(rows.mapNotNull { it[AiRequestAudits.userId] }.distinct())
        val normalized = query.trim().lowercase()
        return rows.map { row ->
            val userId = row[AiRequestAudits.userId]
            val user = userId?.let { users[it] }
            AiAuditRecord(
                id = row[AiRequestAudits.id],
                userId = userId,
                gameId = user?.gameId,
                qq = user?.qq,
                channel = row[AiRequestAudits.channel],
                requestText = row[AiRequestAudits.requestText],
                responseText = row[AiRequestAudits.responseText],
                riskCode = row[AiRequestAudits.riskCode],
                status = row[AiRequestAudits.status],
                model = row[AiRequestAudits.model],
                tokenEstimate = row[AiRequestAudits.tokenEstimate],
                providerStatusCode = row[AiRequestAudits.providerStatusCode],
                providerError = row[AiRequestAudits.providerError],
                firstTokenLatencyMs = row[AiRequestAudits.firstTokenLatencyMs],
                totalLatencyMs = row[AiRequestAudits.totalLatencyMs],
                retryCount = row[AiRequestAudits.retryCount],
                emittedDelta = row[AiRequestAudits.emittedDelta],
                knowledgeQuery = row[AiRequestAudits.knowledgeQuery],
                knowledgeSources = row[AiRequestAudits.knowledgeSources],
                createdAt = row[AiRequestAudits.createdAt]
            )
        }.filter {
            normalized.isBlank() ||
                it.userId.orEmpty().lowercase().contains(normalized) ||
                it.gameId.orEmpty().lowercase().contains(normalized) ||
                it.qq.orEmpty().lowercase().contains(normalized) ||
                it.channel.lowercase().contains(normalized) ||
                it.status.lowercase().contains(normalized) ||
                it.riskCode.orEmpty().lowercase().contains(normalized) ||
                it.providerStatusCode?.toString()?.contains(normalized) == true ||
                it.knowledgeSources.orEmpty().lowercase().contains(normalized)
        }.take(limit.coerceIn(1, 200))
    }

    fun listAdminMemory(query: String, limit: Int = 80): List<AiMemoryRecord> {
        val rows = AiMemoryItems.selectAll()
            .orderBy(AiMemoryItems.updatedAt to SortOrder.DESC)
            .limit(300)
            .map { it }
        val users = usersById(rows.map { it[AiMemoryItems.userId] }.distinct())
        val normalized = query.trim().lowercase()
        return rows.map { row ->
            val user = users[row[AiMemoryItems.userId]]
            AiMemoryRecord(
                id = row[AiMemoryItems.id],
                userId = row[AiMemoryItems.userId],
                gameId = user?.gameId.orEmpty(),
                qq = user?.qq.orEmpty(),
                content = row[AiMemoryItems.content],
                kind = row[AiMemoryItems.kind],
                weight = row[AiMemoryItems.weight],
                status = row[AiMemoryItems.status],
                source = row[AiMemoryItems.sourceValue],
                createdAt = row[AiMemoryItems.createdAt],
                updatedAt = row[AiMemoryItems.updatedAt]
            )
        }.filter {
            normalized.isBlank() ||
                it.userId.lowercase().contains(normalized) ||
                it.gameId.lowercase().contains(normalized) ||
                it.qq.lowercase().contains(normalized) ||
                it.content.lowercase().contains(normalized) ||
                it.kind.lowercase().contains(normalized)
        }.take(limit.coerceIn(1, 200))
    }

    fun updateMemoryStatus(id: String, status: String, now: Instant = Instant.now()) {
        AiMemoryItems.update({ AiMemoryItems.id eq id }) {
            it[AiMemoryItems.status] = status
            it[updatedAt] = now
        }
    }

    fun audit(
        userId: String?,
        channel: String,
        requestText: String,
        responseText: String?,
        riskCode: String?,
        status: String,
        model: String?,
        tokenEstimate: Int?,
        providerStatusCode: Int? = null,
        providerError: String? = null,
        firstTokenLatencyMs: Int? = null,
        totalLatencyMs: Int? = null,
        retryCount: Int? = null,
        emittedDelta: Boolean? = null,
        knowledgeQuery: String? = null,
        knowledgeSources: String? = null,
        now: Instant = Instant.now(),
    ) {
        AiRequestAudits.insert {
            it[id] = Ids.aiAuditId()
            it[AiRequestAudits.userId] = userId
            it[AiRequestAudits.channel] = channel
            it[AiRequestAudits.requestText] = requestText.take(4000)
            it[AiRequestAudits.responseText] = responseText?.take(8000)
            it[AiRequestAudits.riskCode] = riskCode
            it[AiRequestAudits.status] = status
            it[AiRequestAudits.model] = model
            it[AiRequestAudits.tokenEstimate] = tokenEstimate
            it[AiRequestAudits.providerStatusCode] = providerStatusCode
            it[AiRequestAudits.providerError] = providerError?.take(2000)
            it[AiRequestAudits.firstTokenLatencyMs] = firstTokenLatencyMs
            it[AiRequestAudits.totalLatencyMs] = totalLatencyMs
            it[AiRequestAudits.retryCount] = retryCount
            it[AiRequestAudits.emittedDelta] = emittedDelta
            it[AiRequestAudits.knowledgeQuery] = knowledgeQuery?.take(1000)
            it[AiRequestAudits.knowledgeSources] = knowledgeSources?.take(4000)
            it[createdAt] = now
        }
    }

    fun createPurchase(userId: String, clientRequestId: String, plan: AiPlanRecord, now: Instant = Instant.now()): AiPurchaseRecord {
        val id = Ids.aiPurchaseId()
        AiPurchases.insert {
            it[AiPurchases.id] = id
            it[AiPurchases.clientRequestId] = clientRequestId
            it[AiPurchases.userId] = userId
            it[planId] = plan.id
            it[amount] = plan.price
            it[currency] = plan.currency
            it[status] = "processing"
            it[failureCode] = null
            it[createdAt] = now
            it[updatedAt] = now
        }
        return AiPurchaseRecord(id, clientRequestId, userId, plan.id, plan.price, plan.currency, "processing", null, now, now)
    }

    fun purchaseByClientRequest(userId: String, clientRequestId: String): AiPurchaseRecord? =
        AiPurchases.selectAll()
            .where { (AiPurchases.userId eq userId) and (AiPurchases.clientRequestId eq clientRequestId) }
            .singleOrNull()
            ?.toPurchaseRecord()

    fun purchaseById(userId: String, purchaseId: String): AiPurchaseRecord? =
        AiPurchases.selectAll()
            .where { (AiPurchases.userId eq userId) and (AiPurchases.id eq purchaseId) }
            .singleOrNull()
            ?.toPurchaseRecord()

    fun activePurchaseByPlan(userId: String, planId: String): AiPurchaseRecord? =
        AiPurchases.selectAll()
            .where {
                (AiPurchases.userId eq userId) and
                    (AiPurchases.planId eq planId) and
                    (AiPurchases.status inList listOf("processing", "unknown"))
            }
            .orderBy(AiPurchases.updatedAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toPurchaseRecord()

    fun updatePurchaseStatus(id: String, status: String, failureCode: String? = null, now: Instant = Instant.now()): AiPurchaseRecord {
        AiPurchases.update({ AiPurchases.id eq id }) {
            it[AiPurchases.status] = status
            it[AiPurchases.failureCode] = failureCode
            it[updatedAt] = now
        }
        return AiPurchases.selectAll().where { AiPurchases.id eq id }.single().toPurchaseRecord()
    }

    fun grantPlan(userId: String, plan: AiPlanRecord, now: Instant = Instant.now()) {
        val expiresAt = now.plus(plan.durationDays.toLong().coerceAtLeast(1), ChronoUnit.DAYS)
        val existing = AiEntitlements.selectAll().where { AiEntitlements.userId eq userId }.singleOrNull()
        if (existing == null) {
            AiEntitlements.insert {
                it[AiEntitlements.userId] = userId
                it[planId] = plan.id
                it[planCode] = plan.code
                it[quotaPerWindow] = plan.quotaPerWindow
                it[quotaWindowHours] = plan.quotaWindowHours
                it[modelTier] = plan.modelTier
                it[AiEntitlements.expiresAt] = expiresAt
                it[updatedAt] = now
            }
        } else {
            AiEntitlements.update({ AiEntitlements.userId eq userId }) {
                it[planId] = plan.id
                it[planCode] = plan.code
                it[quotaPerWindow] = plan.quotaPerWindow
                it[quotaWindowHours] = plan.quotaWindowHours
                it[modelTier] = plan.modelTier
                it[AiEntitlements.expiresAt] = expiresAt
                it[updatedAt] = now
            }
        }
    }

    fun qqGroups(): List<AiQqGroupRecord> =
        AiQqGroups.selectAll().map {
            AiQqGroupRecord(
                groupId = it[AiQqGroups.groupId],
                enabled = it[AiQqGroups.enabled],
                triggerMode = it[AiQqGroups.triggerMode],
                triggerPattern = it[AiQqGroups.triggerPattern],
                quotaPerWindow = it[AiQqGroups.quotaPerWindow],
                windowMinutes = it[AiQqGroups.windowMinutes],
                mutedUntil = it[AiQqGroups.mutedUntil]
            )
        }

    fun updatePlan(
        planId: String,
        name: String,
        description: String,
        price: BigDecimal,
        quotaPerWindow: Int,
        quotaWindowHours: Int,
        durationDays: Int,
        modelTier: String,
        active: Boolean,
        now: Instant = Instant.now(),
    ): AiPlanRecord {
        AiPlans.update({ AiPlans.id eq planId }) {
            it[AiPlans.name] = name
            it[AiPlans.description] = description
            it[AiPlans.price] = price
            it[AiPlans.quotaPerWindow] = quotaPerWindow
            it[AiPlans.quotaWindowHours] = quotaWindowHours
            it[AiPlans.durationDays] = durationDays
            it[AiPlans.modelTier] = modelTier
            it[AiPlans.active] = active
            it[updatedAt] = now
        }
        return AiPlans.selectAll().where { AiPlans.id eq planId }.single().toAiPlanRecord()
    }

    fun upsertQqGroup(
        groupId: String,
        enabled: Boolean,
        triggerPattern: String,
        quotaPerWindow: Int,
        windowMinutes: Int,
        now: Instant = Instant.now(),
    ): AiQqGroupRecord {
        val existing = AiQqGroups.selectAll().where { AiQqGroups.groupId eq groupId }.singleOrNull()
        if (existing == null) {
            AiQqGroups.insert {
                it[AiQqGroups.groupId] = groupId
                it[AiQqGroups.enabled] = enabled
                it[triggerMode] = "regex"
                it[AiQqGroups.triggerPattern] = triggerPattern
                it[AiQqGroups.quotaPerWindow] = quotaPerWindow
                it[AiQqGroups.windowMinutes] = windowMinutes
                it[mutedUntil] = null
                it[updatedAt] = now
            }
        } else {
            AiQqGroups.update({ AiQqGroups.groupId eq groupId }) {
                it[AiQqGroups.enabled] = enabled
                it[triggerMode] = "regex"
                it[AiQqGroups.triggerPattern] = triggerPattern
                it[AiQqGroups.quotaPerWindow] = quotaPerWindow
                it[AiQqGroups.windowMinutes] = windowMinutes
                it[updatedAt] = now
            }
        }
        return AiQqGroups.selectAll().where { AiQqGroups.groupId eq groupId }.single().let {
            AiQqGroupRecord(
                groupId = it[AiQqGroups.groupId],
                enabled = it[AiQqGroups.enabled],
                triggerMode = it[AiQqGroups.triggerMode],
                triggerPattern = it[AiQqGroups.triggerPattern],
                quotaPerWindow = it[AiQqGroups.quotaPerWindow],
                windowMinutes = it[AiQqGroups.windowMinutes],
                mutedUntil = it[AiQqGroups.mutedUntil]
            )
        }
    }

    fun deleteQqGroup(groupId: String) {
        AiQqGroups.deleteWhere { AiQqGroups.groupId eq groupId }
    }

    private fun createConversation(userId: String, now: Instant): AiConversationRecord {
        val id = Ids.aiConversationId()
        AiConversations.insert {
            it[AiConversations.id] = id
            it[AiConversations.userId] = userId
            it[status] = "active"
            it[createdAt] = now
            it[updatedAt] = now
        }
        return AiConversationRecord(id, userId, "active", now, now)
    }

    private fun quotaWindowStart(now: Instant, windowHours: Int): Instant {
        val epochSeconds = now.epochSecond
        val windowSeconds = windowHours.toLong().coerceAtLeast(1) * 60 * 60
        return Instant.ofEpochSecond(epochSeconds - (epochSeconds % windowSeconds))
    }

    private fun usersById(userIds: List<String>): Map<String, AiAdminUserRecord> {
        if (userIds.isEmpty()) return emptyMap()
        return Users.selectAll()
            .where { Users.id inList userIds }
            .associate {
                it[Users.id] to AiAdminUserRecord(
                    userId = it[Users.id],
                    gameId = it[Users.currentGameId],
                    qq = it[Users.qq]
                )
            }
    }
}

data class AiPromptRecord(
    val id: String,
    val title: String,
    val content: String,
    val updatedAt: Instant,
)

data class AiPlanRecord(
    val id: String,
    val code: String,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val currency: String,
    val quotaPerWindow: Int,
    val quotaWindowHours: Int,
    val durationDays: Int,
    val modelTier: String,
    val active: Boolean,
) {
    fun toApi(): AiPlan =
        AiPlan(id, code, name, description, price.money(), currency, quotaPerWindow, quotaWindowHours, durationDays, modelTier, active)
}

data class AiConversationRecord(
    val id: String,
    val userId: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun toApi(): AiConversationState =
        AiConversationState(id, status == "active", createdAt.iso(), updatedAt.iso())
}

data class AiCompletedExchangeRecord(
    val userMessage: AiMessage,
    val assistantMessage: AiMessage?,
)

data class AiExchangeBeginRecord(
    val exchange: AiRequestExchangeRecord,
    val created: Boolean,
)

data class AiRequestExchangeRecord(
    val id: String,
    val userId: String,
    val clientMessageId: String,
    val conversationId: String?,
    val userMessageId: String?,
    val assistantMessageId: String?,
    val status: String,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AiPurchaseRecord(
    val id: String,
    val clientRequestId: String,
    val userId: String,
    val planId: String,
    val amount: BigDecimal,
    val currency: String,
    val status: String,
    val failureCode: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun toApi(plan: AiPlanRecord): AiPurchase =
        AiPurchase(id, clientRequestId, plan.toApi(), amount.money(), currency, status, failureCode, createdAt.iso(), updatedAt.iso())
}

data class AiModelConfigRecord(
    val provider: String,
    val baseUrl: String,
    val model: String,
    val temperature: BigDecimal,
    val maxTokens: Int,
    val thinkingEnabled: Boolean,
)

data class AiQqGroupRecord(
    val groupId: String,
    val enabled: Boolean,
    val triggerMode: String,
    val triggerPattern: String,
    val quotaPerWindow: Int,
    val windowMinutes: Int,
    val mutedUntil: Instant?,
)

data class AiKnowledgeRecord(
    val id: String,
    val category: String,
    val title: String,
    val keywords: String,
    val content: String,
    val weight: BigDecimal,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AiKnowledgeMatchRecord(
    val documentId: String,
    val chunkId: String,
    val title: String,
    val category: String,
    val headingPath: String,
    val sourceUrl: String,
    val text: String,
    val score: Int,
)

data class AiAdminUserRecord(
    val userId: String,
    val gameId: String,
    val qq: String,
)

data class AiAdminMessageRecord(
    val messageId: String,
    val conversationId: String,
    val userId: String,
    val gameId: String,
    val qq: String,
    val role: String,
    val content: String,
    val createdAt: Instant,
)

data class AiAuditRecord(
    val id: String,
    val userId: String?,
    val gameId: String?,
    val qq: String?,
    val channel: String,
    val requestText: String,
    val responseText: String?,
    val riskCode: String?,
    val status: String,
    val model: String?,
    val tokenEstimate: Int?,
    val providerStatusCode: Int?,
    val providerError: String?,
    val firstTokenLatencyMs: Int?,
    val totalLatencyMs: Int?,
    val retryCount: Int?,
    val emittedDelta: Boolean?,
    val knowledgeQuery: String?,
    val knowledgeSources: String?,
    val createdAt: Instant,
)

data class AiMemoryRecord(
    val id: String,
    val userId: String,
    val gameId: String,
    val qq: String,
    val content: String,
    val kind: String,
    val weight: BigDecimal,
    val status: String,
    val source: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

private fun ResultRow.toAiPlanRecord(): AiPlanRecord =
    AiPlanRecord(
        id = this[AiPlans.id],
        code = this[AiPlans.code],
        name = this[AiPlans.name],
        description = this[AiPlans.description],
        price = this[AiPlans.price],
        currency = this[AiPlans.currency],
        quotaPerWindow = this[AiPlans.quotaPerWindow],
        quotaWindowHours = this[AiPlans.quotaWindowHours],
        durationDays = this[AiPlans.durationDays],
        modelTier = this[AiPlans.modelTier],
        active = this[AiPlans.active]
    )

private fun ResultRow.toModelConfigRecord(): AiModelConfigRecord =
    AiModelConfigRecord(
        provider = this[AiModelConfigs.provider],
        baseUrl = this[AiModelConfigs.baseUrl],
        model = this[AiModelConfigs.model],
        temperature = this[AiModelConfigs.temperature],
        maxTokens = this[AiModelConfigs.maxTokens],
        thinkingEnabled = this[AiModelConfigs.thinkingEnabled]
    )

private fun ResultRow.toConversationRecord(): AiConversationRecord =
    AiConversationRecord(
        id = this[AiConversations.id],
        userId = this[AiConversations.userId],
        status = this[AiConversations.status],
        createdAt = this[AiConversations.createdAt],
        updatedAt = this[AiConversations.updatedAt]
    )

private fun ResultRow.toAiMessage(): AiMessage =
    AiMessage(
        messageId = this[AiMessages.id],
        conversationId = this[AiMessages.conversationId],
        role = this[AiMessages.role],
        content = this[AiMessages.content],
        createdAt = this[AiMessages.createdAt].iso()
    )

private fun ResultRow.toRequestExchangeRecord(): AiRequestExchangeRecord =
    AiRequestExchangeRecord(
        id = this[AiRequestExchanges.id],
        userId = this[AiRequestExchanges.userId],
        clientMessageId = this[AiRequestExchanges.clientMessageId],
        conversationId = this[AiRequestExchanges.conversationId],
        userMessageId = this[AiRequestExchanges.userMessageId],
        assistantMessageId = this[AiRequestExchanges.assistantMessageId],
        status = this[AiRequestExchanges.status],
        errorCode = this[AiRequestExchanges.errorCode],
        errorMessage = this[AiRequestExchanges.errorMessage],
        createdAt = this[AiRequestExchanges.createdAt],
        updatedAt = this[AiRequestExchanges.updatedAt]
    )

private fun ResultRow.toPurchaseRecord(): AiPurchaseRecord =
    AiPurchaseRecord(
        id = this[AiPurchases.id],
        clientRequestId = this[AiPurchases.clientRequestId],
        userId = this[AiPurchases.userId],
        planId = this[AiPurchases.planId],
        amount = this[AiPurchases.amount],
        currency = this[AiPurchases.currency],
        status = this[AiPurchases.status],
        failureCode = this[AiPurchases.failureCode],
        createdAt = this[AiPurchases.createdAt],
        updatedAt = this[AiPurchases.updatedAt]
    )

private fun ResultRow.toKnowledgeRecord(): AiKnowledgeRecord =
    AiKnowledgeRecord(
        id = this[AiKnowledgeItems.id],
        category = this[AiKnowledgeItems.category],
        title = this[AiKnowledgeItems.title],
        keywords = this[AiKnowledgeItems.keywords],
        content = this[AiKnowledgeItems.content],
        weight = this[AiKnowledgeItems.weight],
        active = this[AiKnowledgeItems.active],
        createdAt = this[AiKnowledgeItems.createdAt],
        updatedAt = this[AiKnowledgeItems.updatedAt]
    )

private fun String.splitKeywords(): List<String> =
    split(Regex("""[\s,，;；|/]+"""))
        .map { it.trim().lowercase() }
        .filter { it.length >= 2 }

private fun String.extractKnowledgeTerms(): List<String> =
    lowercase()
        .split(Regex("""[\s,，。.!！?？;；:/\\|()（）【】\[\]「」"'`~]+"""))
        .map { it.trim() }
        .filter { it.length >= 2 }
        .distinct()
