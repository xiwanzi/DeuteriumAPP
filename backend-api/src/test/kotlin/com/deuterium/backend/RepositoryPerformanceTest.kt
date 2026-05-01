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
import com.deuterium.backend.repository.PlayerRefRepository
import com.deuterium.backend.repository.WalletRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
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

    private companion object {
        val SeedConfirmedAt: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}

