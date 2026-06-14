package com.deuterium.backend.oidc

import com.deuterium.backend.config.AppConfig
import com.deuterium.backend.model.RegisteredUserRecord
import com.deuterium.backend.repository.AccountRepository
import com.deuterium.backend.repository.LoginFailureRepository
import com.deuterium.backend.repository.OidcRepository
import com.deuterium.backend.util.Ids
import com.deuterium.backend.util.PasswordHasher
import com.deuterium.backend.util.Secrets
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

class OidcService(
    private val config: AppConfig,
    private val passwordHasher: PasswordHasher,
    private val accounts: AccountRepository,
    private val loginFailures: LoginFailureRepository,
    private val oidcRepository: OidcRepository,
    private val jwtSigner: OidcJwtSigner,
) {
    private val oidc = config.oidc

    fun discovery(): OidcDiscoveryResponse =
        OidcDiscoveryResponse(
            issuer = oidc.issuer,
            authorizationEndpoint = "${oidc.issuer}/oauth/authorize",
            tokenEndpoint = "${oidc.issuer}/oauth/token",
            userInfoEndpoint = "${oidc.issuer}/oauth/userinfo",
            jwksUri = "${oidc.issuer}/.well-known/jwks.json"
        )

    fun jwks(): JwksResponse =
        jwtSigner.jwks()

    fun validateAuthorizationRequest(
        responseType: String?,
        clientId: String?,
        redirectUri: String?,
        scope: String?,
        state: String?,
        nonce: String?,
    ): OidcAuthorizationRequest {
        oidcRepository.ensureConfiguredClient(oidc)
        val client = oidcRepository.findEnabledClient(clientId.orEmpty())
            ?: throw OidcDirectException("unauthorized_client", "Unknown or disabled OIDC client.")
        if (redirectUri != client.redirectUri) {
            throw OidcDirectException("invalid_request", "The redirect_uri is not registered for this client.")
        }
        val request = OidcAuthorizationRequest(
            clientId = client.clientId,
            redirectUri = client.redirectUri,
            scope = normalizeScope(scope),
            state = state?.takeIf { it.isNotBlank() },
            nonce = nonce?.takeIf { it.isNotBlank() }
        )
        if (responseType != "code") {
            throw OidcRedirectException(request, "unsupported_response_type", "Only response_type=code is supported.")
        }
        if (!request.scope.split(' ').contains("openid")) {
            throw OidcRedirectException(request, "invalid_scope", "The openid scope is required.")
        }
        return request
    }

    fun authenticateWebSession(sessionToken: String?): com.deuterium.backend.model.CurrentUser? {
        if (sessionToken.isNullOrBlank()) return null
        return oidcRepository.authenticateWebSession(Secrets.sha256(sessionToken, config.security.sessionTokenPepper))
    }

    fun login(account: String, password: String, remoteHost: String): RegisteredUserRecord {
        val normalizedAccount = account.trim()
        val failureKey = Secrets.sha256("oidc|${normalizedAccount.lowercase()}|$remoteHost")
        loginFailures.lockedUntil(failureKey)?.let { locked ->
            if (locked.isAfter(Instant.now())) {
                throw OidcLoginException("登录失败次数过多，请稍后再试。")
            }
        }
        val user = accounts.findByAccount(normalizedAccount)
        if (user == null || !passwordHasher.verify(user.passwordHash, password)) {
            val (_, lockedUntil) = loginFailures.recordFailure(failureKey)
            if (lockedUntil != null) {
                throw OidcLoginException("登录失败次数过多，请 15 分钟后再试。")
            }
            throw OidcLoginException("账号或密码错误。")
        }
        loginFailures.clear(failureKey)
        return user
    }

    fun createWebSession(user: RegisteredUserRecord): OidcBrowserSession {
        val token = Ids.token("oidc_web")
        val expiresAt = Instant.now().plus(oidc.webSessionHours, ChronoUnit.HOURS)
        oidcRepository.createWebSession(
            userId = user.userId,
            sessionHash = Secrets.sha256(token, config.security.sessionTokenPepper),
            expiresAt = expiresAt
        )
        return OidcBrowserSession(token, expiresAt)
    }

    fun revokeWebSession(sessionToken: String?) {
        if (!sessionToken.isNullOrBlank()) {
            oidcRepository.revokeWebSession(Secrets.sha256(sessionToken, config.security.sessionTokenPepper))
        }
    }

    fun issueAuthorizationCode(request: OidcAuthorizationRequest, userId: String): String {
        val code = Ids.token("oidc_code")
        oidcRepository.createAuthorizationCode(
            userId = userId,
            clientId = request.clientId,
            redirectUri = request.redirectUri,
            scope = request.scope,
            nonce = request.nonce,
            codeHash = Secrets.sha256(code, config.security.sessionTokenPepper),
            expiresAt = Instant.now().plus(oidc.authorizationCodeMinutes, ChronoUnit.MINUTES)
        )
        return code
    }

    fun exchangeAuthorizationCode(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String,
    ): OidcTokenResponse {
        oidcRepository.ensureConfiguredClient(oidc)
        val client = oidcRepository.findEnabledClient(clientId)
            ?: throw OidcTokenException("invalid_client", "Invalid client credentials.")
        if (redirectUri != client.redirectUri || !constantTimeEquals(Secrets.sha256Raw(clientSecret), client.clientSecretHash)) {
            throw OidcTokenException("invalid_client", "Invalid client credentials.")
        }
        val codeRecord = oidcRepository.consumeAuthorizationCode(
            codeHash = Secrets.sha256(code, config.security.sessionTokenPepper),
            clientId = client.clientId,
            redirectUri = client.redirectUri
        ) ?: throw OidcTokenException("invalid_grant", "Authorization code is invalid, expired, or already used.")

        val now = Instant.now()
        val accessToken = Ids.token("oidc_access")
        val accessExpiresAt = now.plus(oidc.accessTokenMinutes, ChronoUnit.MINUTES)
        oidcRepository.createAccessToken(
            userId = codeRecord.user.userId,
            clientId = client.clientId,
            tokenHash = Secrets.sha256(accessToken, config.security.sessionTokenPepper),
            scope = codeRecord.scope,
            expiresAt = accessExpiresAt
        )
        val idToken = jwtSigner.signIdToken(
            issuer = oidc.issuer,
            clientId = client.clientId,
            user = codeRecord.user,
            scope = codeRecord.scope,
            nonce = codeRecord.nonce,
            issuedAt = now,
            expiresAt = now.plus(oidc.idTokenMinutes, ChronoUnit.MINUTES)
        )
        return OidcTokenResponse(
            accessToken = accessToken,
            expiresIn = ChronoUnit.SECONDS.between(now, accessExpiresAt),
            idToken = idToken,
            scope = codeRecord.scope
        )
    }

    fun userInfo(accessToken: String): OidcProfile {
        val token = oidcRepository.authenticateAccessToken(Secrets.sha256(accessToken, config.security.sessionTokenPepper))
            ?: throw OidcTokenException("invalid_token", "Access token is invalid or expired.")
        return token.user.toOidcProfile()
    }

    private fun normalizeScope(scope: String?): String {
        val requested = scope.orEmpty()
            .split(' ', '\t', '\r', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return requested.joinToString(" ")
    }

    private fun constantTimeEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))
}

data class OidcAuthorizationRequest(
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val state: String?,
    val nonce: String?,
)

data class OidcBrowserSession(
    val token: String,
    val expiresAt: Instant,
)

class OidcDirectException(
    val error: String,
    override val message: String,
) : RuntimeException(message)

class OidcRedirectException(
    val request: OidcAuthorizationRequest,
    val error: String,
    override val message: String,
) : RuntimeException(message)

class OidcTokenException(
    val error: String,
    override val message: String,
) : RuntimeException(message)

class OidcLoginException(
    override val message: String,
) : RuntimeException(message)
