package com.deuterium.backend

import com.deuterium.backend.db.AppPresence
import com.deuterium.backend.db.ChatMessages
import com.deuterium.backend.db.LoginFailures
import com.deuterium.backend.db.PlayerFollows
import com.deuterium.backend.db.PlayerRefs
import com.deuterium.backend.db.PresenceSnapshots
import com.deuterium.backend.db.ServerEvents
import com.deuterium.backend.db.Sessions
import com.deuterium.backend.db.Transfers
import com.deuterium.backend.db.Users
import com.deuterium.backend.db.VerificationRequests
import com.deuterium.backend.db.WalletBalances
import com.deuterium.backend.db.WalletRecords
import com.deuterium.backend.model.CurrentUser
import com.deuterium.backend.model.OnlinePlayer
import com.deuterium.backend.repository.AccountRepository
import com.deuterium.backend.repository.ChatRepository
import com.deuterium.backend.repository.LoginFailureRepository
import com.deuterium.backend.repository.MaintenanceRepository
import com.deuterium.backend.repository.PlayerRefRepository
import com.deuterium.backend.repository.WalletRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryPerformanceTest {
    @Test
    fun `wallet balance upsert uses one database statement per write`() = withRepositoryDatabase {
        val accounts = AccountRepository()
        val playerRefs = PlayerRefRepository(accounts)
        val wallet = WalletRepository(playerRefs)
        val user = accounts.createUser("server-alice", "Alice", "10001", "hash")
        val sql = CountingSqlLogger()
        TransactionManager.current().addLogger(sql)

        wallet.upsertBalance(user.userId, BigDecimal("12.50"))
        assertEquals(1, sql.sqlStatements().size)
        assertFalse(sql.sqlStatements().single().contains("VALUES(amount", ignoreCase = true))

        sql.clear()
        wallet.upsertBalance(user.userId, BigDecimal("13.50"))
        assertEquals(1, sql.sqlStatements().size)
        assertFalse(sql.sqlStatements().single().contains("VALUES(amount", ignoreCase = true))
    }

    @Test
    fun `app presence first write and update both use one database statement`() = withRepositoryDatabase {
        val accounts = AccountRepository()
        val playerRefs = PlayerRefRepository(accounts)
        val chat = ChatRepository(playerRefs, accounts)
        val user = accounts.createUser("server-alice", "Alice", "10001", "hash")
        val sql = CountingSqlLogger()
        TransactionManager.current().addLogger(sql)

        chat.updateAppPresence(user.userId, foreground = true, at = Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals(1, sql.sqlStatements().size)

        sql.clear()
        chat.updateAppPresence(user.userId, foreground = false, at = Instant.parse("2026-01-01T00:01:00Z"))
        assertEquals(1, sql.sqlStatements().size)
    }

    @Test
    fun `app presence update avoids preselect for existing row`() = withRepositoryDatabase {
        val accounts = AccountRepository()
        val playerRefs = PlayerRefRepository(accounts)
        val chat = ChatRepository(playerRefs, accounts)
        val user = accounts.createUser("server-alice", "Alice", "10001", "hash")
        chat.updateAppPresence(user.userId, foreground = true, at = Instant.parse("2026-01-01T00:00:00Z"))
        val sql = CountingSqlLogger()
        TransactionManager.current().addLogger(sql)

        chat.updateAppPresence(user.userId, foreground = false, at = Instant.parse("2026-01-01T00:01:00Z"))

        assertEquals(1, sql.sqlStatements().size)
    }

    @Test
    fun `presence snapshot upsert uses one database statement per write`() = withRepositoryDatabase {
        val accounts = AccountRepository()
        val playerRefs = PlayerRefRepository(accounts)
        val chat = ChatRepository(playerRefs, accounts)
        val sql = CountingSqlLogger()
        TransactionManager.current().addLogger(sql)

        chat.updatePresence(
            listOf(OnlinePlayer("ref-alice", "Alice", "10001", registered = true, onlineSince = "2026-01-01T00:00:00Z")),
            Instant.parse("2026-01-01T00:00:00Z")
        )
        assertEquals(1, sql.sqlStatements().size)

        sql.clear()
        chat.updatePresence(
            listOf(OnlinePlayer("ref-bob", "Bob", "10002", registered = true, onlineSince = "2026-01-01T00:01:00Z")),
            Instant.parse("2026-01-01T00:01:00Z")
        )
        assertEquals(1, sql.sqlStatements().size)
        assertEquals(1, PresenceSnapshots.selectAll().count())
    }

    @Test
    fun `login failure counter locks at fifth failed attempt`() = withRepositoryDatabase {
        val failures = LoginFailureRepository()

        repeat(4) {
            val (_, lockedUntil) = failures.recordFailure("alice|ip")
            assertEquals(null, lockedUntil)
        }
        val (attempts, lockedUntil) = failures.recordFailure("alice|ip")

        assertEquals(5, attempts)
        assertTrue(lockedUntil != null)
        assertEquals(5, LoginFailures.selectAll().where { LoginFailures.failureKey eq "alice|ip" }.single()[LoginFailures.attempts])
    }

    @Test
    fun `wallet record create returns inserted record without readback`() = withRepositoryDatabase {
        val accounts = AccountRepository()
        val playerRefs = PlayerRefRepository(accounts)
        val wallet = WalletRepository(playerRefs)
        val user = accounts.createUser("server-alice", "Alice", "10001", "hash")
        seedPlayerRef("ref-bob", "server-bob", "Bob", null, registered = false, source = "seed")
        val other = playerRefs.get("ref-bob")!!
        val sql = CountingSqlLogger()
        TransactionManager.current().addLogger(sql)

        val record = wallet.createRecord(user.userId, "income", other, BigDecimal("1.00"), "success", null)

        assertEquals("income", record.direction)
        assertEquals(1, sql.sqlStatements().size)
    }

    @Test
    fun `transfer create returns inserted transfer without readback`() = withRepositoryDatabase {
        val accounts = AccountRepository()
        val playerRefs = PlayerRefRepository(accounts)
        val wallet = WalletRepository(playerRefs)
        val user = accounts.createUser("server-alice", "Alice", "10001", "hash")
        seedPlayerRef("ref-bob", "server-bob", "Bob", null, registered = false, source = "seed")
        val recipient = playerRefs.get("ref-bob")!!
        val sql = CountingSqlLogger()
        TransactionManager.current().addLogger(sql)

        val transfer = wallet.createTransfer(
            id = "transfer-1",
            clientRequestId = "client-1",
            user = CurrentUser(user.userId, user.serverUuid, user.gameId, user.qq),
            recipient = recipient,
            amount = BigDecimal("2.00"),
            note = null,
            fingerprint = "fingerprint"
        )

        assertEquals("processing", transfer.status)
        assertEquals(1, sql.sqlStatements().size)
    }

    @Test
    fun `chat history read does not update player refs`() = withRepositoryDatabase {
        val accounts = AccountRepository()
        val playerRefs = PlayerRefRepository(accounts)
        val chat = ChatRepository(playerRefs, accounts)
        accounts.createUser("server-alice", "Alice", "10001", "hash")
        seedPlayerRef("ref-alice", "server-alice", "Alice", "10001", registered = true, source = "seed")
        ChatMessages.insert {
            it[id] = "msg-1"
            it[senderServerUuid] = "server-alice"
            it[senderGameId] = "AliceOld"
            it[content] = "hello"
            it[kind] = "public_chat"
            it[sentAt] = Instant.parse("2026-01-01T00:00:01Z")
            it[createdAt] = Instant.parse("2026-01-01T00:00:01Z")
        }

        val messages = chat.listMessages(100)

        assertEquals("ref-alice", messages.single().sender.playerRef)
        assertEquals("Alice", messages.single().sender.gameId)
        val ref = playerRefs.get("ref-alice")!!
        assertEquals(SeedConfirmedAt, ref.confirmedAt)
        assertEquals("seed", ref.source)
    }

    @Test
    fun `wallet record read does not update player refs`() = withRepositoryDatabase {
        val accounts = AccountRepository()
        val playerRefs = PlayerRefRepository(accounts)
        val wallet = WalletRepository(playerRefs)
        val user = accounts.createUser("server-alice", "Alice", "10001", "hash")
        seedPlayerRef("ref-bob", "server-bob", "Bob", null, registered = false, source = "seed")
        WalletRecords.insert {
            it[id] = "wrec-1"
            it[userId] = user.userId
            it[direction] = "expense"
            it[otherServerUuid] = "server-bob"
            it[otherGameId] = "Bob"
            it[otherQq] = null
            it[amount] = BigDecimal("12.50")
            it[currency] = "CREDIT"
            it[status] = "success"
            it[note] = null
            it[occurredAt] = Instant.parse("2026-01-01T00:00:01Z")
        }

        val records = wallet.listRecords(user.userId, 20)

        assertEquals("ref-bob", records.single().otherPlayer.playerRef)
        val ref = playerRefs.get("ref-bob")!!
        assertEquals(SeedConfirmedAt, ref.confirmedAt)
        assertEquals("seed", ref.source)
        assertEquals(1, PlayerRefs.selectAll().count())
    }

    @Test
    fun `wallet records after marker return incremental rows oldest to newest`() = withRepositoryDatabase {
        val accounts = AccountRepository()
        val playerRefs = PlayerRefRepository(accounts)
        val wallet = WalletRepository(playerRefs)
        val user = accounts.createUser("server-alice", "Alice", "10001", "hash")
        seedPlayerRef("ref-bob", "server-bob", "Bob", null, registered = false, source = "seed")
        listOf(
            "wrec-1" to "2026-01-01T00:00:01Z",
            "wrec-2" to "2026-01-01T00:00:02Z",
            "wrec-3" to "2026-01-01T00:00:03Z"
        ).forEach { (recordId, occurredAt) ->
            WalletRecords.insert {
                it[id] = recordId
                it[userId] = user.userId
                it[direction] = "income"
                it[otherServerUuid] = "server-bob"
                it[otherGameId] = "Bob"
                it[otherQq] = null
                it[amount] = BigDecimal("1.00")
                it[currency] = "CREDIT"
                it[status] = "success"
                it[note] = null
                it[WalletRecords.occurredAt] = Instant.parse(occurredAt)
            }
        }

        val records = wallet.listRecordsAfter(user.userId, "wrec-1", 100)

        assertEquals(listOf("wrec-2", "wrec-3"), records.map { it.recordId })
    }

    @Test
    fun `player directory keeps server online players first and app status order`() = withRepositoryDatabase {
        val accounts = AccountRepository()
        val playerRefs = PlayerRefRepository(accounts)
        val chat = ChatRepository(playerRefs, accounts)
        val alice = accounts.createUser("server-alice", "Alice", "10001", "hash")
        val bob = accounts.createUser("server-bob", "Bob", "10002", "hash")
        val carol = accounts.createUser("server-carol", "Carol", "10003", "hash")
        seedPlayerRef("ref-alice", "server-alice", "Alice", "10001", registered = true, source = "seed")
        seedPlayerRef("ref-bob", "server-bob", "Bob", "10002", registered = true, source = "seed")
        seedPlayerRef("ref-carol", "server-carol", "Carol", "10003", registered = true, source = "seed")
        chat.updateAppPresence(alice.userId, foreground = false, at = Instant.parse("2026-01-01T00:00:00Z"))
        chat.updateAppPresence(bob.userId, foreground = false, at = Instant.parse("2026-01-01T00:00:00Z"))
        chat.updateAppPresence(carol.userId, foreground = true, at = Instant.now())

        val directory = chat.listPlayerDirectory(
            CurrentUser(alice.userId, alice.serverUuid, alice.gameId, alice.qq),
            listOf(OnlinePlayer("ref-bob", "Bob", "10002", registered = true, onlineSince = "2026-01-01T00:00:00Z")),
            appConnections = mapOf(bob.userId to false, carol.userId to true)
        )

        assertEquals("Bob", directory.first().gameId)
        assertTrue(directory.first().serverOnline)
        assertTrue(directory.first().appConnected)
        assertFalse(directory.first().appForeground)
        assertEquals("Carol", directory[1].gameId)
        assertEquals("online", directory[1].appStatus)
        assertTrue(directory[1].appConnected)
        assertTrue(directory[1].appForeground)
        assertTrue(directory.first { it.gameId == "Alice" }.self)
    }

    @Test
    fun `follow and unfollow are idempotent`() = withRepositoryDatabase {
        val accounts = AccountRepository()
        val playerRefs = PlayerRefRepository(accounts)
        val chat = ChatRepository(playerRefs, accounts)
        val user = accounts.createUser("server-alice", "Alice", "10001", "hash")
        seedPlayerRef("ref-bob", "server-bob", "Bob", null, registered = false, source = "seed")

        chat.followPlayer(user.userId, "ref-bob")
        chat.followPlayer(user.userId, "ref-bob")
        assertEquals(1, followedCount(user.userId, "server-bob"))

        chat.unfollowPlayer(user.userId, "ref-bob")
        chat.unfollowPlayer(user.userId, "ref-bob")
        assertEquals(0, followedCount(user.userId, "server-bob"))
    }

    @Test
    fun `maintenance cleanup deletes only expired operational data`() = withRepositoryDatabase {
        val now = Instant.parse("2026-05-03T00:00:00Z")
        val accounts = AccountRepository()
        val user = accounts.createUser("server-alice", "Alice", "10001", "hash")
        Sessions.insert {
            it[id] = "sess-expired"
            it[userId] = user.userId
            it[tokenHash] = "token-expired"
            it[expiresAt] = now.minusSeconds(1)
            it[revokedAt] = null
            it[createdAt] = now.minusSeconds(3600)
        }
        Sessions.insert {
            it[id] = "sess-live"
            it[userId] = user.userId
            it[tokenHash] = "token-live"
            it[expiresAt] = now.plusSeconds(3600)
            it[revokedAt] = null
            it[createdAt] = now
        }
        VerificationRequests.insert {
            it[id] = "ver-expired"
            it[tokenHash] = "ver-token"
            it[purpose] = "registration"
            it[serverUuid] = "server-alice"
            it[gameId] = "Alice"
            it[qq] = "10001"
            it[codeHash] = "code"
            it[expiresAt] = now.minus(2, java.time.temporal.ChronoUnit.DAYS)
            it[resendAvailableAt] = now.minus(2, java.time.temporal.ChronoUnit.DAYS)
            it[attempts] = 0
            it[maxAttempts] = 5
            it[consumedAt] = null
            it[createdAt] = now.minus(2, java.time.temporal.ChronoUnit.DAYS)
        }
        LoginFailures.insert {
            it[failureKey] = "alice|ip"
            it[attempts] = 1
            it[lockedUntil] = null
            it[updatedAt] = now.minus(2, java.time.temporal.ChronoUnit.DAYS)
        }
        ChatMessages.insert {
            it[id] = "msg-old"
            it[senderServerUuid] = "server-alice"
            it[senderGameId] = "Alice"
            it[content] = "old"
            it[kind] = "public_chat"
            it[sentAt] = now.minus(31, java.time.temporal.ChronoUnit.DAYS)
            it[createdAt] = now.minus(31, java.time.temporal.ChronoUnit.DAYS)
        }
        ServerEvents.insert {
            it[id] = "event-old"
            it[eventType] = "server_say"
            it[content] = "old"
            it[occurredAt] = now.minus(31, java.time.temporal.ChronoUnit.DAYS)
            it[createdAt] = now.minus(31, java.time.temporal.ChronoUnit.DAYS)
        }

        val result = MaintenanceRepository(sessionDays = 30, chatRetentionDays = 30).cleanupExpiredData(now)

        assertEquals(1, result.sessions)
        assertEquals(1, result.verificationRequests)
        assertEquals(1, result.loginFailures)
        assertEquals(1, result.chatMessages)
        assertEquals(1, result.serverEvents)
        assertEquals(1, Sessions.selectAll().count())
    }

    private fun withRepositoryDatabase(block: () -> Unit) {
        Database.connect(
            url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(
                Users,
                Sessions,
                VerificationRequests,
                LoginFailures,
                PlayerRefs,
                WalletBalances,
                Transfers,
                WalletRecords,
                ChatMessages,
                ServerEvents,
                PresenceSnapshots,
                AppPresence,
                PlayerFollows
            )
            block()
        }
    }

    private fun seedPlayerRef(
        playerRef: String,
        serverUuid: String,
        gameId: String,
        qq: String?,
        registered: Boolean,
        source: String,
    ) {
        PlayerRefs.insert {
            it[PlayerRefs.playerRef] = playerRef
            it[PlayerRefs.serverUuid] = serverUuid
            it[currentGameId] = gameId
            it[PlayerRefs.qq] = qq
            it[PlayerRefs.registered] = registered
            it[online] = false
            it[sourceValue] = source
            it[confirmedAt] = SeedConfirmedAt
            it[expiresAt] = null
            it[createdAt] = SeedConfirmedAt
        }
    }

    private fun followedCount(userId: String, serverUuid: String): Long =
        PlayerFollows.selectAll()
            .where { (PlayerFollows.userId eq userId) and (PlayerFollows.targetServerUuid eq serverUuid) }
            .count()

    private class CountingSqlLogger : SqlLogger {
        private val statements = mutableListOf<String>()

        override fun log(context: StatementContext, transaction: Transaction) {
            statements.add(context.sql(transaction))
        }

        fun clear() {
            statements.clear()
        }

        fun sqlStatements(): List<String> =
            statements.filter { sql ->
                val normalized = sql.trimStart().uppercase()
                normalized.startsWith("SELECT") ||
                    normalized.startsWith("INSERT") ||
                    normalized.startsWith("UPDATE") ||
                    normalized.startsWith("DELETE") ||
                    normalized.startsWith("MERGE")
            }
    }

    private companion object {
        val SeedConfirmedAt: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
