package com.deuterium.backend.repository

import com.deuterium.backend.db.ChatMessages
import com.deuterium.backend.db.AppPresence
import com.deuterium.backend.db.LoginFailures
import com.deuterium.backend.db.PlayerRefs
import com.deuterium.backend.db.PlayerFollows
import com.deuterium.backend.db.PresenceSnapshots
import com.deuterium.backend.db.ServerEvents
import com.deuterium.backend.db.Sessions
import com.deuterium.backend.db.Transfers
import com.deuterium.backend.db.Users
import com.deuterium.backend.db.VerificationRequests
import com.deuterium.backend.db.WalletBalances
import com.deuterium.backend.db.WalletRecords
import com.deuterium.backend.model.ChatMessage
import com.deuterium.backend.model.CurrentUser
import com.deuterium.backend.model.OnlinePlayer
import com.deuterium.backend.model.PlayerDirectoryItem
import com.deuterium.backend.model.PlayerRefRecord
import com.deuterium.backend.model.PlayerSummary
import com.deuterium.backend.model.RegisteredUserRecord
import com.deuterium.backend.model.ResolvedPlayerRef
import com.deuterium.backend.model.ServerEvent
import com.deuterium.backend.model.Transfer
import com.deuterium.backend.model.TransferRecord
import com.deuterium.backend.model.UserProfile
import com.deuterium.backend.model.VerificationRecord
import com.deuterium.backend.model.WalletBalance
import com.deuterium.backend.model.WalletRecord
import com.deuterium.backend.model.iso
import com.deuterium.backend.model.money
import com.deuterium.backend.util.Ids
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

class AccountRepository {
    fun findByQq(qq: String): RegisteredUserRecord? =
        Users.selectAll().where { Users.qq eq qq }.singleOrNull()?.toRegisteredUser()

    fun findByServerUuid(serverUuid: String): RegisteredUserRecord? =
        Users.selectAll().where { Users.serverUuid eq serverUuid }.singleOrNull()?.toRegisteredUser()

    fun findByGameId(gameId: String): RegisteredUserRecord? =
        Users.selectAll().where { Users.currentGameId eq gameId }
            .singleOrNull()
            ?.toRegisteredUser()

    fun findByAccount(account: String): RegisteredUserRecord? =
        Users.selectAll().where {
            (Users.qq eq account) or (Users.currentGameId eq account)
        }.singleOrNull()?.toRegisteredUser()

    fun listActiveUsers(): List<RegisteredUserRecord> =
        Users.selectAll()
            .where { Users.status eq "active" }
            .orderBy(Users.currentGameId to SortOrder.ASC)
            .map { it.toRegisteredUser() }

    fun findByServerUuids(serverUuids: Collection<String>): Map<String, RegisteredUserRecord> {
        val uniqueServerUuids = serverUuids.filter { it.isNotBlank() }.distinct()
        if (uniqueServerUuids.isEmpty()) return emptyMap()
        return Users.selectAll()
            .where { Users.serverUuid inList uniqueServerUuids }
            .associate { it[Users.serverUuid] to it.toRegisteredUser() }
    }

    fun createUser(serverUuid: String, gameId: String, qq: String, passwordHash: String): RegisteredUserRecord {
        val now = Instant.now()
        val id = Ids.userId()
        Users.insert {
            it[Users.id] = id
            it[Users.serverUuid] = serverUuid
            it[Users.currentGameId] = gameId
            it[Users.qq] = qq
            it[Users.passwordHash] = passwordHash
            it[Users.status] = "active"
            it[createdAt] = now
            it[updatedAt] = now
        }
        return RegisteredUserRecord(id, serverUuid, gameId, qq, passwordHash)
    }

    fun updatePassword(userId: String, passwordHash: String) {
        Users.update({ Users.id eq userId }) {
            it[Users.passwordHash] = passwordHash
            it[updatedAt] = Instant.now()
        }
    }

    fun revokeSessions(userId: String) {
        Sessions.update({ Sessions.userId eq userId }) {
            it[revokedAt] = Instant.now()
        }
    }

    fun profile(user: CurrentUser, playerRefs: PlayerRefRepository): UserProfile =
        UserProfile(
            userId = user.userId,
            playerRef = playerRefs.ensurePlayerRef(
                serverUuid = user.serverUuid,
                gameId = user.gameId,
                qq = user.qq,
                registered = true,
                online = false,
                source = "account",
                expiresAt = null
            ).playerRef,
            gameId = user.gameId,
            qq = user.qq
        )

    private fun ResultRow.toRegisteredUser(): RegisteredUserRecord =
        RegisteredUserRecord(
            userId = this[Users.id],
            serverUuid = this[Users.serverUuid],
            gameId = this[Users.currentGameId],
            qq = this[Users.qq],
            passwordHash = this[Users.passwordHash]
        )
}

class SessionRepository(private val sessionDays: Long) {
    fun create(user: RegisteredUserRecord, tokenHash: String): String {
        Sessions.insert {
            it[id] = Ids.sessionId()
            it[userId] = user.userId
            it[Sessions.tokenHash] = tokenHash
            it[expiresAt] = Instant.now().plus(sessionDays, ChronoUnit.DAYS)
            it[revokedAt] = null
            it[createdAt] = Instant.now()
        }
        return tokenHash
    }

    fun authenticate(tokenHash: String): CurrentUser? {
        val now = Instant.now()
        val row = Sessions
            .innerJoin(Users)
            .selectAll()
            .where {
                (Sessions.tokenHash eq tokenHash) and
                    Sessions.revokedAt.isNull() and
                    (Sessions.expiresAt greater now) and
                    (Users.status eq "active")
            }
            .singleOrNull()
            ?: return null
        return CurrentUser(
            userId = row[Users.id],
            serverUuid = row[Users.serverUuid],
            gameId = row[Users.currentGameId],
            qq = row[Users.qq]
        )
    }

    fun revokeToken(tokenHash: String) {
        Sessions.update({ Sessions.tokenHash eq tokenHash }) {
            it[revokedAt] = Instant.now()
        }
    }
}

class VerificationRepository {
    fun latestActiveCooldown(purpose: String, gameId: String): Instant? {
        val now = Instant.now()
        return VerificationRequests
            .selectAll()
            .where {
                (VerificationRequests.purpose eq purpose) and
                    (VerificationRequests.gameId eq gameId) and
                    VerificationRequests.consumedAt.isNull() and
                    (VerificationRequests.resendAvailableAt greater now)
            }
            .orderBy(VerificationRequests.createdAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(VerificationRequests.resendAvailableAt)
    }

    fun create(
        tokenHash: String,
        purpose: String,
        serverUuid: String,
        gameId: String,
        qq: String?,
        codeHash: String,
    ): String {
        val now = Instant.now()
        val id = Ids.verificationId()
        VerificationRequests.insert {
            it[VerificationRequests.id] = id
            it[VerificationRequests.tokenHash] = tokenHash
            it[VerificationRequests.purpose] = purpose
            it[VerificationRequests.serverUuid] = serverUuid
            it[VerificationRequests.gameId] = gameId
            it[VerificationRequests.qq] = qq
            it[VerificationRequests.codeHash] = codeHash
            it[expiresAt] = now.plus(10, ChronoUnit.MINUTES)
            it[resendAvailableAt] = now.plus(60, ChronoUnit.SECONDS)
            it[attempts] = 0
            it[maxAttempts] = 5
            it[consumedAt] = null
            it[createdAt] = now
        }
        return id
    }

    fun findByTokenHash(tokenHash: String, purpose: String): VerificationRecord? =
        VerificationRequests
            .selectAll()
            .where { (VerificationRequests.tokenHash eq tokenHash) and (VerificationRequests.purpose eq purpose) }
            .singleOrNull()
            ?.toVerificationRecord()

    fun incrementAttempts(id: String) {
        VerificationRequests.update({ VerificationRequests.id eq id }) {
            with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                it.update(attempts, attempts + 1)
            }
        }
    }

    fun consume(id: String) {
        VerificationRequests.update({ VerificationRequests.id eq id }) {
            it[consumedAt] = Instant.now()
        }
    }

    fun expirePrevious(purpose: String, gameId: String) {
        VerificationRequests.update({
            (VerificationRequests.purpose eq purpose) and
                (VerificationRequests.gameId eq gameId) and
                VerificationRequests.consumedAt.isNull()
        }) {
            it[consumedAt] = Instant.now()
        }
    }

    private fun ResultRow.toVerificationRecord(): VerificationRecord =
        VerificationRecord(
            id = this[VerificationRequests.id],
            tokenHash = this[VerificationRequests.tokenHash],
            purpose = this[VerificationRequests.purpose],
            serverUuid = this[VerificationRequests.serverUuid],
            gameId = this[VerificationRequests.gameId],
            qq = this[VerificationRequests.qq],
            codeHash = this[VerificationRequests.codeHash],
            expiresAt = this[VerificationRequests.expiresAt],
            attempts = this[VerificationRequests.attempts],
            maxAttempts = this[VerificationRequests.maxAttempts],
            consumedAt = this[VerificationRequests.consumedAt]
        )
}

class LoginFailureRepository {
    fun lockedUntil(key: String): Instant? =
        LoginFailures.selectAll().where { LoginFailures.failureKey eq key }.singleOrNull()?.get(LoginFailures.lockedUntil)

    fun recordFailure(key: String): Pair<Int, Instant?> {
        val now = Instant.now()
        upsertLoginFailure(key, now, now.plus(15, ChronoUnit.MINUTES))
        val row = LoginFailures.selectAll().where { LoginFailures.failureKey eq key }.single()
        return row[LoginFailures.attempts] to row[LoginFailures.lockedUntil]
    }

    private fun upsertLoginFailure(key: String, updatedAt: Instant, lockedUntil: Instant) {
        val table = LoginFailures.sqlName()
        val failureKey = LoginFailures.failureKey.sqlName()
        val attempts = LoginFailures.attempts.sqlName()
        val locked = LoginFailures.lockedUntil.sqlName()
        val updated = LoginFailures.updatedAt.sqlName()
        TransactionManager.current().exec(
            """
            INSERT INTO $table ($failureKey, $attempts, $locked, $updated)
            VALUES (?, 1, NULL, ?)
            ON DUPLICATE KEY UPDATE
              $locked = CASE WHEN $attempts + 1 >= 5 THEN ? ELSE NULL END,
              $attempts = $attempts + 1,
              $updated = ?
            """.trimIndent(),
            listOf(
                LoginFailures.failureKey.columnType to key,
                LoginFailures.updatedAt.columnType to updatedAt,
                LoginFailures.lockedUntil.columnType to lockedUntil,
                LoginFailures.updatedAt.columnType to updatedAt
            ),
            StatementType.UPDATE
        )
    }

    fun clear(key: String) {
        LoginFailures.deleteWhere { failureKey eq key }
    }
}

class PlayerRefRepository(private val accounts: AccountRepository) {
    companion object {
        private const val PlayerRefLockStripeCount = 64
        private val playerRefLocks = Array(PlayerRefLockStripeCount) { Any() }

        private val playerRefPriority: Comparator<PlayerRefRecord> =
            compareByDescending<PlayerRefRecord> { it.registered }
                .thenByDescending { it.expiresAt == null }
                .thenByDescending { it.confirmedAt }

        fun stableMissingPlayerRef(serverUuid: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(serverUuid.toByteArray(Charsets.UTF_8))
            return "player_cached_" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest).take(32)
        }
    }

    fun ensurePlayerRef(
        serverUuid: String,
        gameId: String,
        qq: String?,
        registered: Boolean,
        online: Boolean,
        source: String,
        expiresAt: Instant?,
    ): PlayerRefRecord {
        val lockKey = "$serverUuid|$registered"
        val lock = playerRefLocks[Math.floorMod(lockKey.hashCode(), playerRefLocks.size)]
        return synchronized(lock) {
            ensurePlayerRefLocked(serverUuid, gameId, qq, registered, online, source, expiresAt)
        }
    }

    private fun ensurePlayerRefLocked(
        serverUuid: String,
        gameId: String,
        qq: String?,
        registered: Boolean,
        online: Boolean,
        source: String,
        expiresAt: Instant?,
    ): PlayerRefRecord {
        val now = Instant.now()
        val existing = PlayerRefs.selectAll()
            .where {
                (PlayerRefs.serverUuid eq serverUuid) and
                    (PlayerRefs.registered eq registered) and
                    ((PlayerRefs.expiresAt.isNull()) or (PlayerRefs.expiresAt greater now))
            }
            .firstOrNull()
        if (existing != null) {
            PlayerRefs.update({ PlayerRefs.playerRef eq existing[PlayerRefs.playerRef] }) {
                it[currentGameId] = gameId
                it[PlayerRefs.qq] = qq
                it[PlayerRefs.online] = online
                it[PlayerRefs.sourceValue] = source
                it[confirmedAt] = now
                it[PlayerRefs.expiresAt] = expiresAt
            }
            return PlayerRefRecord(
                playerRef = existing[PlayerRefs.playerRef],
                serverUuid = existing[PlayerRefs.serverUuid],
                gameId = gameId,
                qq = qq,
                registered = registered,
                online = online,
                source = source,
                confirmedAt = now,
                expiresAt = expiresAt
            )
        }
        val ref = Ids.playerRef()
        PlayerRefs.insert {
            it[playerRef] = ref
            it[PlayerRefs.serverUuid] = serverUuid
            it[currentGameId] = gameId
            it[PlayerRefs.qq] = qq
            it[PlayerRefs.registered] = registered
            it[PlayerRefs.online] = online
            it[PlayerRefs.sourceValue] = source
            it[confirmedAt] = now
            it[PlayerRefs.expiresAt] = expiresAt
            it[createdAt] = now
        }
        return PlayerRefRecord(ref, serverUuid, gameId, qq, registered, online, source, now, expiresAt)
    }

    fun get(playerRef: String): PlayerRefRecord? =
        PlayerRefs.selectAll().where { PlayerRefs.playerRef eq playerRef }.singleOrNull()?.toPlayerRefRecord()

    fun findByPlayerRefs(playerRefs: Collection<String>): Map<String, PlayerRefRecord> {
        val uniquePlayerRefs = playerRefs.filter { it.isNotBlank() }.distinct()
        if (uniquePlayerRefs.isEmpty()) return emptyMap()
        return PlayerRefs.selectAll()
            .where { PlayerRefs.playerRef inList uniquePlayerRefs }
            .associate { it[PlayerRefs.playerRef] to it.toPlayerRefRecord() }
    }

    fun findActiveByServerUuids(serverUuids: Collection<String>): Map<String, PlayerRefRecord> {
        val uniqueServerUuids = serverUuids.filter { it.isNotBlank() }.distinct()
        if (uniqueServerUuids.isEmpty()) return emptyMap()
        val now = Instant.now()
        return PlayerRefs.selectAll()
            .where {
                (PlayerRefs.serverUuid inList uniqueServerUuids) and
                    ((PlayerRefs.expiresAt.isNull()) or (PlayerRefs.expiresAt greater now))
            }
            .fold(mutableMapOf<String, PlayerRefRecord>()) { bestByServerUuid, row ->
                val candidate = row.toPlayerRefRecord()
                val current = bestByServerUuid[candidate.serverUuid]
                if (current == null || playerRefPriority.compare(candidate, current) < 0) {
                    bestByServerUuid[candidate.serverUuid] = candidate
                }
                bestByServerUuid
            }
    }

    fun summary(record: PlayerRefRecord): PlayerSummary =
        PlayerSummary(
            playerRef = record.playerRef,
            gameId = record.gameId,
            qq = record.qq,
            online = record.online,
            registered = record.registered,
            source = record.source
        )

    fun resolved(record: PlayerRefRecord): ResolvedPlayerRef =
        ResolvedPlayerRef(
            playerRef = record.playerRef,
            gameId = record.gameId,
            qq = record.qq,
            online = record.online,
            registered = record.registered,
            source = record.source,
            confirmedAt = record.confirmedAt.iso(),
            expiresAt = record.expiresAt?.iso()
        )

    fun registeredForServerUuid(serverUuid: String, source: String, online: Boolean = false): PlayerRefRecord? {
        val user = accounts.findByServerUuid(serverUuid) ?: return null
        return ensurePlayerRef(user.serverUuid, user.gameId, user.qq, true, online, source, null)
    }

    private fun ResultRow.toPlayerRefRecord(): PlayerRefRecord =
        PlayerRefRecord(
            playerRef = this[PlayerRefs.playerRef],
            serverUuid = this[PlayerRefs.serverUuid],
            gameId = this[PlayerRefs.currentGameId],
            qq = this[PlayerRefs.qq],
            registered = this[PlayerRefs.registered],
            online = this[PlayerRefs.online],
            source = this[PlayerRefs.sourceValue],
            confirmedAt = this[PlayerRefs.confirmedAt],
            expiresAt = this[PlayerRefs.expiresAt]
        )
}

class WalletRepository(private val playerRefs: PlayerRefRepository) {
    fun cachedBalance(userId: String): WalletBalance? =
        WalletBalances.selectAll().where { WalletBalances.userId eq userId }.singleOrNull()?.let {
            WalletBalance(
                currency = it[WalletBalances.currency],
                amount = it[WalletBalances.amount].money(),
                fresh = it[WalletBalances.fresh],
                refreshedAt = it[WalletBalances.refreshedAt]?.iso()
            )
        }

    fun upsertBalance(userId: String, amount: BigDecimal): WalletBalance {
        val now = Instant.now()
        upsertWalletBalance(userId, amount, now)
        return WalletBalance(
            currency = "CREDIT",
            amount = amount.money(),
            fresh = true,
            refreshedAt = now.iso()
        )
    }

    private fun upsertWalletBalance(userId: String, amount: BigDecimal, refreshedAt: Instant) {
        val table = WalletBalances.sqlName()
        val user = WalletBalances.userId.sqlName()
        val balanceAmount = WalletBalances.amount.sqlName()
        val balanceCurrency = WalletBalances.currency.sqlName()
        val balanceFresh = WalletBalances.fresh.sqlName()
        val balanceRefreshedAt = WalletBalances.refreshedAt.sqlName()
        TransactionManager.current().exec(
            """
            INSERT INTO $table ($user, $balanceAmount, $balanceCurrency, $balanceFresh, $balanceRefreshedAt)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              $balanceAmount = ?,
              $balanceCurrency = ?,
              $balanceFresh = ?,
              $balanceRefreshedAt = ?
            """.trimIndent(),
            listOf(
                WalletBalances.userId.columnType to userId,
                WalletBalances.amount.columnType to amount,
                WalletBalances.currency.columnType to "CREDIT",
                WalletBalances.fresh.columnType to true,
                WalletBalances.refreshedAt.columnType to refreshedAt,
                WalletBalances.amount.columnType to amount,
                WalletBalances.currency.columnType to "CREDIT",
                WalletBalances.fresh.columnType to true,
                WalletBalances.refreshedAt.columnType to refreshedAt
            ),
            StatementType.UPDATE
        )
    }

    fun createTransfer(
        id: String,
        clientRequestId: String,
        user: CurrentUser,
        recipient: PlayerRefRecord,
        amount: BigDecimal,
        note: String?,
        fingerprint: String,
    ): TransferRecord {
        val now = Instant.now()
        Transfers.insert {
            it[Transfers.id] = id
            it[Transfers.clientRequestId] = clientRequestId
            it[userId] = user.userId
            it[requestFingerprint] = fingerprint
            it[fromServerUuid] = user.serverUuid
            it[toServerUuid] = recipient.serverUuid
            it[recipientGameId] = recipient.gameId
            it[recipientQq] = recipient.qq
            it[Transfers.amount] = amount
            it[currency] = "CREDIT"
            it[Transfers.note] = note
            it[status] = "processing"
            it[createdAt] = now
            it[updatedAt] = now
        }
        return TransferRecord(
            id = id,
            clientRequestId = clientRequestId,
            userId = user.userId,
            requestFingerprint = fingerprint,
            fromServerUuid = user.serverUuid,
            toServerUuid = recipient.serverUuid,
            recipientGameId = recipient.gameId,
            recipientQq = recipient.qq,
            amount = amount,
            currency = "CREDIT",
            note = note,
            status = "processing",
            createdAt = now,
            updatedAt = now
        )
    }

    fun findTransferByClientRequest(userId: String, clientRequestId: String): TransferRecord? =
        Transfers.selectAll()
            .where { (Transfers.userId eq userId) and (Transfers.clientRequestId eq clientRequestId) }
            .singleOrNull()
            ?.toTransferRecord()

    fun updateTransferStatus(record: TransferRecord, status: String): TransferRecord {
        val updatedAt = Instant.now()
        Transfers.update({ Transfers.id eq record.id }) {
            it[Transfers.status] = status
            it[Transfers.updatedAt] = updatedAt
        }
        return record.copy(status = status, updatedAt = updatedAt)
    }

    fun updateTransferStatus(id: String, status: String) {
        Transfers.update({ Transfers.id eq id }) {
            it[Transfers.status] = status
            it[updatedAt] = Instant.now()
        }
    }

    fun getTransfer(id: String, userId: String): TransferRecord? =
        Transfers.selectAll()
            .where { (Transfers.id eq id) and (Transfers.userId eq userId) }
            .singleOrNull()
            ?.toTransferRecord()

    fun createRecord(
        userId: String,
        direction: String,
        other: PlayerRefRecord,
        amount: BigDecimal,
        status: String,
        note: String?,
        occurredAt: Instant = Instant.now(),
    ): WalletRecord =
        insertRecord(Ids.walletRecordId(), userId, direction, other, amount, status, note, occurredAt)

    fun createRecordIfAbsent(
        recordId: String,
        userId: String,
        direction: String,
        other: PlayerRefRecord,
        amount: BigDecimal,
        status: String,
        note: String?,
        occurredAt: Instant = Instant.now(),
    ): WalletRecord? {
        return try {
            insertRecord(recordId, userId, direction, other, amount, status, note, occurredAt)
        } catch (e: ExposedSQLException) {
            if (e.isDuplicateKey()) null else throw e
        }
    }

    private fun insertRecord(
        recordId: String,
        userId: String,
        direction: String,
        other: PlayerRefRecord,
        amount: BigDecimal,
        status: String,
        note: String?,
        occurredAt: Instant,
    ): WalletRecord {
        WalletRecords.insert {
            it[id] = recordId
            it[WalletRecords.userId] = userId
            it[WalletRecords.direction] = direction
            it[otherServerUuid] = other.serverUuid
            it[otherGameId] = other.gameId
            it[otherQq] = other.qq
            it[WalletRecords.amount] = amount
            it[currency] = "CREDIT"
            it[WalletRecords.status] = status
            it[WalletRecords.note] = note
            it[WalletRecords.occurredAt] = occurredAt
        }
        return WalletRecord(
            recordId = recordId,
            direction = direction,
            otherPlayer = PlayerSummary(
                playerRef = other.playerRef,
                gameId = other.gameId,
                qq = other.qq,
                online = other.online,
                registered = other.registered,
                source = other.source
            ),
            amount = amount.money(),
            currency = "CREDIT",
            status = status,
            note = note,
            occurredAt = occurredAt.iso()
        )
    }

    fun listRecords(userId: String, limit: Int): List<WalletRecord> {
        val rows = WalletRecords.selectAll()
            .where { WalletRecords.userId eq userId }
            .orderBy(WalletRecords.occurredAt to org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .toList()
        val refsByServerUuid = playerRefs.findActiveByServerUuids(rows.map { it[WalletRecords.otherServerUuid] })
        return rows.map { walletRecord(it, refsByServerUuid) }
    }

    fun listRecordsAfter(userId: String, afterRecordId: String, limit: Int): List<WalletRecord> {
        val marker = WalletRecords.selectAll()
            .where { (WalletRecords.userId eq userId) and (WalletRecords.id eq afterRecordId) }
            .singleOrNull()
            ?: return emptyList()
        val markerOccurredAt = marker[WalletRecords.occurredAt]
        val rows = WalletRecords.selectAll()
            .where {
                (WalletRecords.userId eq userId) and
                    (
                        (WalletRecords.occurredAt greater markerOccurredAt) or
                            ((WalletRecords.occurredAt eq markerOccurredAt) and (WalletRecords.id greater afterRecordId))
                        )
            }
            .orderBy(WalletRecords.occurredAt to SortOrder.ASC, WalletRecords.id to SortOrder.ASC)
            .limit(limit)
            .toList()
        val refsByServerUuid = playerRefs.findActiveByServerUuids(rows.map { it[WalletRecords.otherServerUuid] })
        return rows.map { walletRecord(it, refsByServerUuid) }
    }

    fun toApi(record: TransferRecord): Transfer {
        val recipient = playerRefs.findActiveByServerUuids(listOf(record.toServerUuid))[record.toServerUuid]
        return Transfer(
            transferId = record.id,
            clientRequestId = record.clientRequestId,
            recipient = PlayerSummary(
                playerRef = recipient?.playerRef ?: PlayerRefRepository.stableMissingPlayerRef(record.toServerUuid),
                gameId = recipient?.gameId ?: record.recipientGameId,
                qq = recipient?.qq ?: record.recipientQq,
                online = recipient?.online ?: false,
                registered = recipient?.registered ?: (record.recipientQq != null),
                source = recipient?.source ?: "transfer"
            ),
            amount = record.amount.money(),
            currency = record.currency,
            note = record.note,
            status = record.status,
            createdAt = record.createdAt.iso(),
            updatedAt = record.updatedAt.iso()
        )
    }

    private fun ResultRow.toTransferRecord(): TransferRecord =
        TransferRecord(
            id = this[Transfers.id],
            clientRequestId = this[Transfers.clientRequestId],
            userId = this[Transfers.userId],
            requestFingerprint = this[Transfers.requestFingerprint],
            fromServerUuid = this[Transfers.fromServerUuid],
            toServerUuid = this[Transfers.toServerUuid],
            recipientGameId = this[Transfers.recipientGameId],
            recipientQq = this[Transfers.recipientQq],
            amount = this[Transfers.amount],
            currency = this[Transfers.currency],
            note = this[Transfers.note],
            status = this[Transfers.status],
            createdAt = this[Transfers.createdAt],
            updatedAt = this[Transfers.updatedAt]
        )

    private fun walletRecord(row: ResultRow, refsByServerUuid: Map<String, PlayerRefRecord>): WalletRecord {
        val otherServerUuid = row[WalletRecords.otherServerUuid]
        val other = refsByServerUuid[otherServerUuid]
        return WalletRecord(
            recordId = row[WalletRecords.id],
            direction = row[WalletRecords.direction],
            otherPlayer = PlayerSummary(
                playerRef = other?.playerRef ?: PlayerRefRepository.stableMissingPlayerRef(otherServerUuid),
                gameId = other?.gameId ?: row[WalletRecords.otherGameId],
                qq = other?.qq ?: row[WalletRecords.otherQq],
                online = other?.online ?: false,
                registered = other?.registered ?: (row[WalletRecords.otherQq] != null),
                source = other?.source ?: "record"
            ),
            amount = row[WalletRecords.amount].money(),
            currency = row[WalletRecords.currency],
            status = row[WalletRecords.status],
            note = row[WalletRecords.note],
            occurredAt = row[WalletRecords.occurredAt].iso()
        )
    }
}

class ChatRepository(
    private val playerRefs: PlayerRefRepository,
    private val accounts: AccountRepository,
) {
    fun updateAppPresence(userId: String, foreground: Boolean, at: Instant = Instant.now()) {
        val table = AppPresence.sqlName()
        val user = AppPresence.userId.sqlName()
        val foregroundColumn = AppPresence.foreground.sqlName()
        val lastForegroundAt = AppPresence.lastForegroundAt.sqlName()
        val lastSeenAt = AppPresence.lastSeenAt.sqlName()
        val updatedAt = AppPresence.updatedAt.sqlName()
        TransactionManager.current().exec(
            """
            INSERT INTO $table ($user, $foregroundColumn, $lastForegroundAt, $lastSeenAt, $updatedAt)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              $foregroundColumn = ?,
              $lastForegroundAt = CASE WHEN ? THEN ? ELSE $lastForegroundAt END,
              $lastSeenAt = ?,
              $updatedAt = ?
            """.trimIndent(),
            listOf(
                AppPresence.userId.columnType to userId,
                AppPresence.foreground.columnType to foreground,
                AppPresence.lastForegroundAt.columnType to at.takeIf { foreground },
                AppPresence.lastSeenAt.columnType to at,
                AppPresence.updatedAt.columnType to at,
                AppPresence.foreground.columnType to foreground,
                AppPresence.foreground.columnType to foreground,
                AppPresence.lastForegroundAt.columnType to at,
                AppPresence.lastSeenAt.columnType to at,
                AppPresence.updatedAt.columnType to at
            ),
            StatementType.UPDATE
        )
    }

    fun followPlayer(userId: String, playerRef: String): PlayerRefRecord? {
        val target = playerRefs.get(playerRef) ?: return null
        PlayerFollows.insertIgnore {
            it[PlayerFollows.userId] = userId
            it[PlayerFollows.targetServerUuid] = target.serverUuid
            it[PlayerFollows.createdAt] = Instant.now()
        }
        return target
    }

    fun unfollowPlayer(userId: String, playerRef: String): PlayerRefRecord? {
        val target = playerRefs.get(playerRef) ?: return null
        PlayerFollows.deleteWhere {
            (PlayerFollows.userId eq userId) and
                (PlayerFollows.targetServerUuid eq target.serverUuid)
        }
        return target
    }

    fun listFollowedPlayers(
        currentUser: CurrentUser,
        serverPlayers: List<OnlinePlayer>,
        appConnections: Map<String, Boolean> = emptyMap(),
    ): List<PlayerDirectoryItem> {
        return listPlayerDirectory(currentUser, serverPlayers, appConnections).filter { it.followed }
    }

    fun listPlayerDirectory(
        currentUser: CurrentUser,
        serverPlayers: List<OnlinePlayer>,
        appConnections: Map<String, Boolean> = emptyMap(),
    ): List<PlayerDirectoryItem> {
        val followed = followedServerUuids(currentUser.userId)
        val serverRefsByPlayerRef = playerRefs.findByPlayerRefs(serverPlayers.map { it.playerRef })
        val activeUsers = accounts.listActiveUsers()
        val registeredUsersByServerUuid = activeUsers.associateBy { it.serverUuid }
        val presence = appPresenceByUserId(activeUsers.map { it.userId })
        val refsByServerUuid = playerRefs.findActiveByServerUuids(
            activeUsers.map { it.serverUuid } + serverRefsByPlayerRef.values.map { it.serverUuid }
        )
        val onlineServerUuids = mutableSetOf<String>()
        val players = LinkedHashMap<String, PlayerDirectoryItem>()

        serverPlayers.forEach { player ->
            val ref = serverRefsByPlayerRef[player.playerRef] ?: return@forEach
            onlineServerUuids.add(ref.serverUuid)
            players[ref.serverUuid] = playerDirectoryItem(
                currentUser = currentUser,
                ref = ref,
                serverOnline = true,
                onlineSince = player.onlineSince,
                presence = presence,
                followed = followed,
                registeredUsersByServerUuid = registeredUsersByServerUuid,
                appConnections = appConnections
            )
        }

        activeUsers.forEach { user ->
            val ref = refsByServerUuid[user.serverUuid] ?: PlayerRefRecord(
                playerRef = PlayerRefRepository.stableMissingPlayerRef(user.serverUuid),
                serverUuid = user.serverUuid,
                gameId = user.gameId,
                qq = user.qq,
                registered = true,
                online = onlineServerUuids.contains(user.serverUuid),
                source = "player_directory",
                confirmedAt = Instant.EPOCH,
                expiresAt = null
            )
            players[user.serverUuid] = playerDirectoryItem(
                currentUser = currentUser,
                ref = ref,
                serverOnline = onlineServerUuids.contains(user.serverUuid),
                onlineSince = players[user.serverUuid]?.onlineSince,
                presence = presence,
                followed = followed,
                registeredUsersByServerUuid = registeredUsersByServerUuid,
                appConnections = appConnections
            )
        }

        return players.values.sortedWith(
            compareByDescending<PlayerDirectoryItem> { it.serverOnline }
                .thenBy { appStatusRank(it.appStatus) }
                .thenBy { it.gameId.lowercase() }
        )
    }

    fun playerDirectoryItem(
        currentUser: CurrentUser,
        ref: PlayerRefRecord,
        serverOnline: Boolean,
        onlineSince: String? = null,
        appConnections: Map<String, Boolean> = emptyMap(),
    ): PlayerDirectoryItem {
        val registeredUsersByServerUuid = accounts.findByServerUuids(listOf(ref.serverUuid))
        return playerDirectoryItem(
            currentUser = currentUser,
            ref = ref,
            serverOnline = serverOnline,
            onlineSince = onlineSince,
            presence = appPresenceByUserId(registeredUsersByServerUuid.values.map { it.userId }),
            followed = followedServerUuids(currentUser.userId),
            registeredUsersByServerUuid = registeredUsersByServerUuid,
            appConnections = appConnections
        )
    }

    fun playerDirectoryItem(
        currentUser: CurrentUser,
        ref: PlayerRefRecord,
        serverPlayers: List<OnlinePlayer>,
        appConnections: Map<String, Boolean> = emptyMap(),
    ): PlayerDirectoryItem {
        val onlinePlayer = onlinePlayerFor(ref, serverPlayers)
        val registeredUsersByServerUuid = accounts.findByServerUuids(listOf(ref.serverUuid))
        return playerDirectoryItem(
            currentUser = currentUser,
            ref = ref,
            serverOnline = onlinePlayer != null,
            onlineSince = onlinePlayer?.onlineSince,
            presence = appPresenceByUserId(registeredUsersByServerUuid.values.map { it.userId }),
            followed = followedServerUuids(currentUser.userId),
            registeredUsersByServerUuid = registeredUsersByServerUuid,
            appConnections = appConnections
        )
    }

    fun insertChatMessage(messageId: String, serverUuid: String, gameId: String, content: String, sentAt: Instant): ChatMessage {
        val registered = accounts.findByServerUuid(serverUuid)
        val ref = playerRefs.ensurePlayerRef(
            serverUuid = serverUuid,
            gameId = registered?.gameId ?: gameId,
            qq = registered?.qq,
            registered = registered != null,
            online = true,
            source = "chat",
            expiresAt = null
        )
        ChatMessages.insert {
            it[id] = messageId
            it[senderServerUuid] = serverUuid
            it[senderGameId] = gameId
            it[ChatMessages.content] = content
            it[kind] = "public_chat"
            it[ChatMessages.sentAt] = sentAt
            it[createdAt] = Instant.now()
        }
        return ChatMessage(
            messageId = messageId,
            sender = playerRefs.summary(ref),
            content = content,
            sentAt = sentAt.iso()
        )
    }

    fun listMessages(limit: Int): List<ChatMessage> {
        val rows = ChatMessages.selectAll()
            .orderBy(ChatMessages.sentAt to org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .toList()
            .reversed()
        val serverUuids = rows.map { it[ChatMessages.senderServerUuid] }
        val registeredByServerUuid = accounts.findByServerUuids(serverUuids)
        val refsByServerUuid = playerRefs.findActiveByServerUuids(serverUuids)
        return rows.map { chatMessage(it, registeredByServerUuid, refsByServerUuid) }
    }

    fun insertServerEvent(eventId: String, eventType: String, content: String, occurredAt: Instant): ServerEvent {
        ServerEvents.insert {
            it[id] = eventId
            it[ServerEvents.eventType] = eventType
            it[ServerEvents.content] = content
            it[ServerEvents.occurredAt] = occurredAt
            it[createdAt] = Instant.now()
        }
        return ServerEvent(eventId, eventType, content, occurredAt.iso())
    }

    fun updatePresence(players: List<OnlinePlayer>, updatedAt: Instant) {
        val json = Json.encodeToString(players)
        val table = PresenceSnapshots.sqlName()
        val snapshotId = PresenceSnapshots.id.sqlName()
        val onlineCount = PresenceSnapshots.onlineCount.sqlName()
        val playersJson = PresenceSnapshots.playersJson.sqlName()
        val snapshotUpdatedAt = PresenceSnapshots.updatedAt.sqlName()
        TransactionManager.current().exec(
            """
            INSERT INTO $table ($snapshotId, $onlineCount, $playersJson, $snapshotUpdatedAt)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              $onlineCount = ?,
              $playersJson = ?,
              $snapshotUpdatedAt = ?
            """.trimIndent(),
            listOf(
                PresenceSnapshots.id.columnType to 1,
                PresenceSnapshots.onlineCount.columnType to players.size,
                PresenceSnapshots.playersJson.columnType to json,
                PresenceSnapshots.updatedAt.columnType to updatedAt,
                PresenceSnapshots.onlineCount.columnType to players.size,
                PresenceSnapshots.playersJson.columnType to json,
                PresenceSnapshots.updatedAt.columnType to updatedAt
            ),
            StatementType.UPDATE
        )
    }

    fun presenceSnapshot(): PresenceSnapshot? =
        PresenceSnapshots.selectAll().where { PresenceSnapshots.id eq 1 }.singleOrNull()?.let {
            PresenceSnapshot(
                onlineCount = it[PresenceSnapshots.onlineCount],
                players = Json.decodeFromString<List<OnlinePlayer>>(it[PresenceSnapshots.playersJson]),
                updatedAt = it[PresenceSnapshots.updatedAt]
            )
        }

    private fun chatMessage(
        row: ResultRow,
        registeredByServerUuid: Map<String, RegisteredUserRecord>,
        refsByServerUuid: Map<String, PlayerRefRecord>,
    ): ChatMessage {
        val serverUuid = row[ChatMessages.senderServerUuid]
        val registered = registeredByServerUuid[serverUuid]
        val ref = refsByServerUuid[serverUuid]
        return ChatMessage(
            messageId = row[ChatMessages.id],
            sender = PlayerSummary(
                playerRef = ref?.playerRef ?: PlayerRefRepository.stableMissingPlayerRef(serverUuid),
                gameId = registered?.gameId ?: ref?.gameId ?: row[ChatMessages.senderGameId],
                qq = registered?.qq ?: ref?.qq,
                online = ref?.online ?: true,
                registered = registered != null || ref?.registered == true,
                source = ref?.source ?: "chat"
            ),
            content = row[ChatMessages.content],
            sentAt = row[ChatMessages.sentAt].iso()
        )
    }

    private fun playerDirectoryItem(
        currentUser: CurrentUser,
        ref: PlayerRefRecord,
        serverOnline: Boolean,
        onlineSince: String?,
        presence: Map<String, AppPresenceRecord>,
        followed: Set<String>,
        registeredUsersByServerUuid: Map<String, RegisteredUserRecord>,
        appConnections: Map<String, Boolean>,
    ): PlayerDirectoryItem {
        val registeredUser = registeredUsersByServerUuid[ref.serverUuid]
        val appPresence = registeredUser?.let { presence[it.userId] }
        val appConnected = registeredUser?.let { appConnections.containsKey(it.userId) } == true
        val appForeground = registeredUser?.let { appConnections[it.userId] } == true
        return PlayerDirectoryItem(
            playerRef = ref.playerRef,
            gameId = registeredUser?.gameId ?: ref.gameId,
            qq = registeredUser?.qq ?: ref.qq,
            registered = registeredUser != null || ref.registered,
            serverOnline = serverOnline,
            appStatus = appStatus(appPresence, appConnected, appForeground),
            appConnected = appConnected,
            appForeground = appForeground,
            appLastSeenAt = appPresence?.lastSeenAt?.iso(),
            onlineSince = onlineSince,
            followed = followed.contains(ref.serverUuid),
            self = registeredUser?.userId == currentUser.userId
        )
    }

    private fun onlinePlayerFor(ref: PlayerRefRecord, serverPlayers: List<OnlinePlayer>): OnlinePlayer? {
        serverPlayers.firstOrNull { it.playerRef == ref.playerRef }?.let { return it }
        val refsByPlayerRef = playerRefs.findByPlayerRefs(serverPlayers.map { it.playerRef })
        return serverPlayers.firstOrNull { player -> refsByPlayerRef[player.playerRef]?.serverUuid == ref.serverUuid }
    }

    private fun appPresenceByUserId(userIds: Collection<String>): Map<String, AppPresenceRecord> {
        val uniqueUserIds = userIds.filter { it.isNotBlank() }.distinct()
        if (uniqueUserIds.isEmpty()) return emptyMap()
        return AppPresence.selectAll()
            .where { AppPresence.userId inList uniqueUserIds }
            .associate {
            it[AppPresence.userId] to AppPresenceRecord(
                userId = it[AppPresence.userId],
                foreground = it[AppPresence.foreground],
                lastSeenAt = it[AppPresence.lastSeenAt]
            )
        }
    }

    private fun followedServerUuids(userId: String): Set<String> =
        PlayerFollows.selectAll()
            .where { PlayerFollows.userId eq userId }
            .map { it[PlayerFollows.targetServerUuid] }
            .toSet()

    private fun appStatus(presence: AppPresenceRecord?, appConnected: Boolean, appForeground: Boolean): String {
        if (appForeground) return "online"
        if (appConnected) return "just_online"
        if (presence == null) return "long_offline"
        if (presence.foreground) return "online"
        val minutes = java.time.Duration.between(presence.lastSeenAt, Instant.now()).toMinutes()
        return when {
            minutes <= 5 -> "just_online"
            minutes <= 30 -> "recent_online"
            minutes <= 24 * 60 -> "recently_online"
            else -> "long_offline"
        }
    }

    private fun appStatusRank(status: String): Int =
        when (status) {
            "online" -> 0
            "just_online" -> 1
            "recent_online" -> 2
            "recently_online" -> 3
            else -> 4
        }
}

data class PresenceSnapshot(
    val onlineCount: Int,
    val players: List<OnlinePlayer>,
    val updatedAt: Instant,
)

class MaintenanceRepository(
    private val sessionDays: Long,
    private val chatRetentionDays: Long,
) {
    fun cleanupExpiredData(now: Instant = Instant.now()): MaintenanceCleanupResult {
        val verificationCutoff = now.minus(1, ChronoUnit.DAYS)
        val loginFailureCutoff = now.minus(1, ChronoUnit.DAYS)
        val chatCutoff = now.minus(chatRetentionDays, ChronoUnit.DAYS)
        val sessionCutoff = now
        val revokedSessionCutoff = now.minus(sessionDays.coerceAtLeast(1), ChronoUnit.DAYS)

        val sessions = Sessions.deleteWhere {
            (Sessions.expiresAt less sessionCutoff) or
                (Sessions.revokedAt.isNotNull() and (Sessions.revokedAt less revokedSessionCutoff))
        }
        val verifications = VerificationRequests.deleteWhere {
            VerificationRequests.expiresAt less verificationCutoff
        }
        val loginFailures = LoginFailures.deleteWhere {
            (LoginFailures.updatedAt less loginFailureCutoff) or
                (LoginFailures.lockedUntil less now)
        }
        val chatMessages = ChatMessages.deleteWhere {
            ChatMessages.sentAt less chatCutoff
        }
        val serverEvents = ServerEvents.deleteWhere {
            ServerEvents.occurredAt less chatCutoff
        }

        return MaintenanceCleanupResult(
            sessions = sessions,
            verificationRequests = verifications,
            loginFailures = loginFailures,
            chatMessages = chatMessages,
            serverEvents = serverEvents
        )
    }
}

data class MaintenanceCleanupResult(
    val sessions: Int,
    val verificationRequests: Int,
    val loginFailures: Int,
    val chatMessages: Int,
    val serverEvents: Int,
)

private data class AppPresenceRecord(
    val userId: String,
    val foreground: Boolean,
    val lastSeenAt: Instant,
)

private fun ExposedSQLException.isDuplicateKey(): Boolean =
    sqlState == "23505" || errorCode == 1062

private fun Table.sqlName(): String = tableName.sqlIdentifier()

private fun Column<*>.sqlName(): String = name.sqlIdentifier()

private fun String.sqlIdentifier(): String = "`" + replace("`", "``") + "`"
