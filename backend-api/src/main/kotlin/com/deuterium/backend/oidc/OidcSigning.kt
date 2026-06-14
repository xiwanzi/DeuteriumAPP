package com.deuterium.backend.oidc

import com.deuterium.backend.model.RegisteredUserRecord
import com.deuterium.backend.util.Ids
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

class OidcSigningKeyStore(
    private val path: Path,
    private val json: Json,
) {
    fun loadOrCreate(): OidcSigningKey {
        if (Files.exists(path)) {
            val persisted = json.decodeFromString<PersistedSigningKey>(Files.readString(path))
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(
                PKCS8EncodedKeySpec(Base64.getDecoder().decode(persisted.privateKeyPkcs8))
            ) as RSAPrivateKey
            val publicKey = keyFactory.generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(persisted.publicKeyX509))
            ) as RSAPublicKey
            return OidcSigningKey(persisted.kid, privateKey, publicKey)
        }
        path.parent?.let { Files.createDirectories(it) }
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        val key = OidcSigningKey(
            kid = "kid_" + Ids.token("oidc").removePrefix("oidc_").take(16),
            privateKey = pair.private as RSAPrivateKey,
            publicKey = pair.public as RSAPublicKey
        )
        Files.writeString(
            path,
            json.encodeToString(
                PersistedSigningKey(
                    kid = key.kid,
                    privateKeyPkcs8 = Base64.getEncoder().encodeToString(key.privateKey.encoded),
                    publicKeyX509 = Base64.getEncoder().encodeToString(key.publicKey.encoded),
                    createdAt = Instant.now().toString()
                )
            )
        )
        return key
    }
}

class OidcJwtSigner(
    private val signingKey: OidcSigningKey,
    private val json: Json,
) {
    fun jwks(): JwksResponse =
        JwksResponse(
            keys = listOf(
                JwkKey(
                    kid = signingKey.kid,
                    n = signingKey.publicKey.modulus.base64UrlUnsigned(),
                    e = signingKey.publicKey.publicExponent.base64UrlUnsigned()
                )
            )
        )

    fun signIdToken(
        issuer: String,
        clientId: String,
        user: RegisteredUserRecord,
        scope: String,
        nonce: String?,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String {
        val header = buildJsonObject {
            put("alg", "RS256")
            put("typ", "JWT")
            put("kid", signingKey.kid)
        }
        val profile = user.toOidcProfile()
        val payload = buildJsonObject {
            put("iss", issuer)
            put("sub", profile.sub)
            put("aud", clientId)
            put("iat", issuedAt.epochSecond)
            put("exp", expiresAt.epochSecond)
            put("scope", scope)
            put("email", profile.email)
            put("displayName", profile.displayName)
            put("preferred_username", profile.preferredUsername)
            put("name", profile.name)
            if (!nonce.isNullOrBlank()) {
                put("nonce", nonce)
            }
        }
        return signJwt(header, payload)
    }

    private fun signJwt(header: JsonObject, payload: JsonObject): String {
        val unsigned = listOf(header, payload)
            .joinToString(".") { json.encodeToString(JsonObject.serializer(), it).base64Url() }
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(signingKey.privateKey)
        signature.update(unsigned.toByteArray(Charsets.US_ASCII))
        return "$unsigned.${signature.sign().base64Url()}"
    }
}

data class OidcSigningKey(
    val kid: String,
    val privateKey: RSAPrivateKey,
    val publicKey: RSAPublicKey,
)

fun RegisteredUserRecord.toOidcProfile(): OidcProfile =
    OidcProfile(
        sub = userId,
        email = "${userId.lowercase()}@users.deuterium.local",
        displayName = gameId,
        preferredUsername = gameId,
        name = gameId
    )

private fun String.base64Url(): String =
    toByteArray(Charsets.UTF_8).base64Url()

private fun ByteArray.base64Url(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(this)

private fun BigInteger.base64UrlUnsigned(): String {
    val bytes = toByteArray()
    val unsigned = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    return unsigned.base64Url()
}

@Serializable
private data class PersistedSigningKey(
    val kid: String,
    val privateKeyPkcs8: String,
    val publicKeyX509: String,
    val createdAt: String,
)
