package com.deuterium.backend.repository

import com.deuterium.backend.config.OidcConfig
import com.deuterium.backend.db.OidcAccessTokens
import com.deuterium.backend.db.OidcAuthorizationCodes
import com.deuterium.backend.db.OidcClients
import com.deuterium.backend.db.OidcWebSessions
import com.deuterium.backend.db.Users
import com.deuterium.backend.model.CurrentUser
import com.deuterium.backend.model.RegisteredUserRecord
import com.deuterium.backend.util.Ids
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant

class OidcRepository {
    fun ensureConfiguredClient(config: OidcConfig): OidcClientRecord {
        val existing = OidcClients.selectAll()
            .where { OidcClients.clientId eq config.clientId }
            .singleOrNull()
        val now = Instant.now()
        if (existing == null) {
            OidcClients.insert {
                it[clientId] = config.clientId
                it[clientSecretHash] = config.clientSecretHash
                it[redirectUri] = config.redirectUri
                it[enabled] = config.enabled
                it[createdAt] = now
                it[updatedAt] = now
            }
        } else {
            OidcClients.update({ OidcClients.clientId eq config.clientId }) {
                it[clientSecretHash] = config.clientSecretHash
                it[redirectUri] = config.redirectUri
                it[enabled] = config.enabled
                it[updatedAt] = now
            }
        }
        return OidcClientRecord(
            clientId = config.clientId,
            clientSecretHash = config.clientSecretHash,
            redirectUri = config.redirectUri,
            enabled = config.enabled
        )
    }

    fun findEnabledClient(clientId: String): OidcClientRecord? =
        OidcClients.selectAll()
            .where { (OidcClients.clientId eq clientId) and (OidcClients.enabled eq true) }
            .singleOrNull()
            ?.toClientRecord()

    fun createWebSession(userId: String, sessionHash: String, expiresAt: Instant): OidcWebSessionRecord {
        val id = Ids.oidcWebSessionId()
        val now = Instant.now()
        OidcWebSessions.insert {
            it[OidcWebSessions.id] = id
            it[OidcWebSessions.userId] = userId
            it[OidcWebSessions.sessionHash] = sessionHash
            it[OidcWebSessions.expiresAt] = expiresAt
            it[revokedAt] = null
            it[createdAt] = now
        }
        return OidcWebSessionRecord(id, userId, expiresAt)
    }

    fun authenticateWebSession(sessionHash: String, now: Instant = Instant.now()): CurrentUser? {
        val row = OidcWebSessions
            .innerJoin(Users)
            .selectAll()
            .where {
                (OidcWebSessions.sessionHash eq sessionHash) and
                    OidcWebSessions.revokedAt.isNull() and
                    (OidcWebSessions.expiresAt greater now) and
                    (Users.status eq "active")
            }
            .singleOrNull()
            ?: return null
        return row.toCurrentUser()
    }

    fun revokeWebSession(sessionHash: String) {
        OidcWebSessions.update({ OidcWebSessions.sessionHash eq sessionHash }) {
            it[revokedAt] = Instant.now()
        }
    }

    fun createAuthorizationCode(
        userId: String,
        clientId: String,
        redirectUri: String,
        scope: String,
        nonce: String?,
        codeHash: String,
        expiresAt: Instant,
    ) {
        val now = Instant.now()
        OidcAuthorizationCodes.insert {
            it[id] = Ids.oidcAuthorizationCodeId()
            it[OidcAuthorizationCodes.codeHash] = codeHash
            it[OidcAuthorizationCodes.userId] = userId
            it[OidcAuthorizationCodes.clientId] = clientId
            it[OidcAuthorizationCodes.redirectUri] = redirectUri
            it[OidcAuthorizationCodes.scope] = scope
            it[OidcAuthorizationCodes.nonce] = nonce
            it[OidcAuthorizationCodes.expiresAt] = expiresAt
            it[consumedAt] = null
            it[createdAt] = now
        }
    }

    fun consumeAuthorizationCode(
        codeHash: String,
        clientId: String,
        redirectUri: String,
        now: Instant = Instant.now(),
    ): OidcAuthorizationCodeRecord? {
        val row = OidcAuthorizationCodes
            .innerJoin(Users)
            .selectAll()
            .where {
                (OidcAuthorizationCodes.codeHash eq codeHash) and
                    (OidcAuthorizationCodes.clientId eq clientId) and
                    (OidcAuthorizationCodes.redirectUri eq redirectUri) and
                    OidcAuthorizationCodes.consumedAt.isNull() and
                    (OidcAuthorizationCodes.expiresAt greater now) and
                    (Users.status eq "active")
            }
            .singleOrNull()
            ?: return null
        val updated = OidcAuthorizationCodes.update({
            (OidcAuthorizationCodes.id eq row[OidcAuthorizationCodes.id]) and OidcAuthorizationCodes.consumedAt.isNull()
        }) {
            it[consumedAt] = now
        }
        if (updated != 1) return null
        return row.toAuthorizationCodeRecord()
    }

    fun createAccessToken(
        userId: String,
        clientId: String,
        tokenHash: String,
        scope: String,
        expiresAt: Instant,
    ) {
        val now = Instant.now()
        OidcAccessTokens.insert {
            it[id] = Ids.oidcAccessTokenId()
            it[OidcAccessTokens.tokenHash] = tokenHash
            it[OidcAccessTokens.userId] = userId
            it[OidcAccessTokens.clientId] = clientId
            it[OidcAccessTokens.scope] = scope
            it[OidcAccessTokens.expiresAt] = expiresAt
            it[revokedAt] = null
            it[createdAt] = now
        }
    }

    fun authenticateAccessToken(tokenHash: String, now: Instant = Instant.now()): OidcAccessTokenRecord? {
        val row = OidcAccessTokens
            .innerJoin(Users)
            .selectAll()
            .where {
                (OidcAccessTokens.tokenHash eq tokenHash) and
                    OidcAccessTokens.revokedAt.isNull() and
                    (OidcAccessTokens.expiresAt greater now) and
                    (Users.status eq "active")
            }
            .singleOrNull()
            ?: return null
        return row.toAccessTokenRecord()
    }

    fun cleanupExpiredData(now: Instant = Instant.now()): OidcCleanupResult =
        OidcCleanupResult(
            authorizationCodes = OidcAuthorizationCodes.deleteWhere {
                expiresAt lessEq now
            },
            accessTokens = OidcAccessTokens.deleteWhere {
                expiresAt lessEq now
            },
            webSessions = OidcWebSessions.deleteWhere {
                expiresAt lessEq now
            }
        )

    private fun ResultRow.toClientRecord(): OidcClientRecord =
        OidcClientRecord(
            clientId = this[OidcClients.clientId],
            clientSecretHash = this[OidcClients.clientSecretHash],
            redirectUri = this[OidcClients.redirectUri],
            enabled = this[OidcClients.enabled]
        )

    private fun ResultRow.toAuthorizationCodeRecord(): OidcAuthorizationCodeRecord =
        OidcAuthorizationCodeRecord(
            user = toRegisteredUser(),
            clientId = this[OidcAuthorizationCodes.clientId],
            redirectUri = this[OidcAuthorizationCodes.redirectUri],
            scope = this[OidcAuthorizationCodes.scope],
            nonce = this[OidcAuthorizationCodes.nonce]
        )

    private fun ResultRow.toAccessTokenRecord(): OidcAccessTokenRecord =
        OidcAccessTokenRecord(
            user = toRegisteredUser(),
            clientId = this[OidcAccessTokens.clientId],
            scope = this[OidcAccessTokens.scope]
        )

    private fun ResultRow.toCurrentUser(): CurrentUser =
        CurrentUser(
            userId = this[Users.id],
            serverUuid = this[Users.serverUuid],
            gameId = this[Users.currentGameId],
            qq = this[Users.qq]
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

data class OidcClientRecord(
    val clientId: String,
    val clientSecretHash: String,
    val redirectUri: String,
    val enabled: Boolean,
)

data class OidcAuthorizationCodeRecord(
    val user: RegisteredUserRecord,
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val nonce: String?,
)

data class OidcAccessTokenRecord(
    val user: RegisteredUserRecord,
    val clientId: String,
    val scope: String,
)

data class OidcWebSessionRecord(
    val id: String,
    val userId: String,
    val expiresAt: Instant,
)

data class OidcCleanupResult(
    val authorizationCodes: Int,
    val accessTokens: Int,
    val webSessions: Int,
)
