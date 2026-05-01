package com.deuterium.backend.util

import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.nio.charset.StandardCharsets

object Ids {
    private val random = SecureRandom()

    fun requestId(): String = "req_" + UUID.randomUUID().toString().replace("-", "")
    fun userId(): String = "usr_" + shortToken()
    fun sessionId(): String = "ses_" + shortToken()
    fun verificationId(): String = "ver_" + shortToken()
    fun playerRef(): String = "player_" + shortToken()
    fun transferId(): String = "tr_" + shortToken()
    fun walletRecordId(): String = "wrec_" + shortToken()
    fun walletRecordIdFromExternal(externalId: String, direction: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$externalId:$direction".toByteArray(StandardCharsets.UTF_8))
        return "wrec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest).take(32)
    }
    fun messageId(): String = "msg_" + shortToken()
    fun eventId(): String = "evt_" + shortToken()
    fun bridgeMessageId(): String = "bridge_msg_" + shortToken()
    fun token(prefix: String): String = prefix + "_" + longToken()

    private fun shortToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun longToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

