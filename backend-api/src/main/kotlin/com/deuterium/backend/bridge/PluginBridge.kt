package com.deuterium.backend.bridge

import java.math.BigDecimal
import java.time.Instant

interface PluginBridge {
    fun isAvailable(): Boolean
    suspend fun deliverVerification(
        verificationId: String,
        purpose: String,
        gameId: String,
        code: String,
        expiresAt: Instant,
    ): VerificationDelivery

    suspend fun resolvePlayer(gameId: String, purpose: String): PlayerResolution
    suspend fun walletBalance(serverUuid: String): WalletBalanceResult
    suspend fun walletTransfer(request: WalletTransferRequest): WalletTransferResult
    suspend fun sendAppChat(request: AppChatRequest): AppChatResult
    suspend fun presenceList(): PresenceListResult
}

data class VerificationDelivery(
    val status: String,
    val serverUuid: String?,
    val currentGameId: String?,
)

data class PlayerResolution(
    val status: String,
    val serverUuid: String?,
    val currentGameId: String?,
    val online: Boolean,
)

data class WalletBalanceResult(
    val status: String,
    val amount: BigDecimal?,
    val currency: String = "CREDIT",
    val currentGameId: String?,
)

data class WalletTransferRequest(
    val transferId: String,
    val idempotencyKey: String,
    val fromServerUuid: String,
    val toServerUuid: String,
    val amount: BigDecimal,
    val currency: String,
    val note: String?,
)

data class WalletTransferResult(val status: String, val reason: String?)

data class AppChatRequest(
    val appMessageId: String,
    val senderServerUuid: String,
    val senderGameId: String,
    val content: String,
)

data class AppChatResult(val status: String)

data class PresenceListResult(
    val status: String,
    val onlineCount: Int,
    val players: List<BridgeOnlinePlayer>,
)

data class BridgeOnlinePlayer(
    val serverUuid: String,
    val currentGameId: String,
    val onlineSince: Instant? = null,
)


