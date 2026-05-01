package com.deuterium.backend.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

@Serializable
data class ApiSuccess<T>(
    val requestId: String,
    val data: T,
    val page: Page? = null,
)

@Serializable
data class ApiErrorResponse(
    val requestId: String,
    val error: ApiErrorBody,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val retryAfterSeconds: Long? = null,
)

@Serializable
data class Page(val nextCursor: String?)

data class CurrentUser(
    val userId: String,
    val serverUuid: String,
    val gameId: String,
    val qq: String,
)

@Serializable
data class UserProfile(
    val userId: String,
    val playerRef: String,
    val gameId: String,
    val qq: String,
    val identityStatus: String = "bound",
)

@Serializable
data class PlayerSummary(
    val playerRef: String,
    val gameId: String,
    val qq: String? = null,
    val online: Boolean = false,
    val registered: Boolean,
    val source: String,
)

@Serializable
data class ResolvedPlayerRef(
    val playerRef: String,
    val gameId: String,
    val qq: String? = null,
    val online: Boolean,
    val registered: Boolean,
    val source: String,
    val confirmedAt: String,
    val expiresAt: String? = null,
)

@Serializable
data class WalletBalance(
    val currency: String,
    val amount: String,
    val fresh: Boolean,
    val refreshedAt: String?,
)

@Serializable
data class Transfer(
    val transferId: String,
    val clientRequestId: String,
    val recipient: PlayerSummary,
    val amount: String,
    val currency: String,
    val note: String? = null,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class WalletRecord(
    val recordId: String,
    val direction: String,
    val otherPlayer: PlayerSummary,
    val amount: String,
    val currency: String,
    val status: String,
    val note: String? = null,
    val occurredAt: String,
)

@Serializable
data class ChatMessage(
    val messageId: String,
    val sender: PlayerSummary,
    val content: String,
    val kind: String = "public_chat",
    val sentAt: String,
)

@Serializable
data class ServerEvent(
    val eventId: String,
    val eventType: String,
    val content: String,
    val occurredAt: String,
)

@Serializable
data class OnlinePlayer(
    val playerRef: String,
    val gameId: String,
    val qq: String? = null,
    val registered: Boolean,
    val onlineSince: String? = null,
)

@Serializable
data class PlayerDirectoryItem(
    val playerRef: String,
    val gameId: String,
    val qq: String? = null,
    val registered: Boolean,
    val serverOnline: Boolean,
    val appStatus: String,
    val appConnected: Boolean = false,
    val appForeground: Boolean = false,
    val appLastSeenAt: String? = null,
    val onlineSince: String? = null,
    val followed: Boolean,
    val self: Boolean,
)

@Serializable
data class VerificationTokenData(
    val verificationToken: String,
    val expiresAt: String,
    val resendAfterSeconds: Int,
)

@Serializable
data class AuthData(val token: String, val user: UserProfile)

@Serializable
data class PasswordResetData(val passwordReset: Boolean)

@Serializable
data class LogoutData(val loggedOut: Boolean)

@Serializable
data class UserProfileData(val user: UserProfile)

@Serializable
data class WalletBalanceData(val balance: WalletBalance)

@Serializable
data class RecipientSearchData(val candidates: List<ResolvedPlayerRef>)

@Serializable
data class TransferData(val transfer: Transfer)

@Serializable
data class WalletRecordsData(val records: List<WalletRecord>)

@Serializable
data class AppUpdateCheckData(
    val latest: Boolean,
    val message: String,
    val latestVersionCode: Int,
    val latestVersionName: String,
)

@Serializable
data class ChatMessagesData(val messages: List<ChatMessage>)

@Serializable
data class PresenceData(val onlineCount: Int, val available: Boolean, val updatedAt: String?)

@Serializable
data class OnlinePlayersData(val players: List<OnlinePlayer>)

@Serializable
data class PlayerDirectoryData(val players: List<PlayerDirectoryItem>)

@Serializable
data class PlayerFollowRequest(val playerRef: String)

@Serializable
data class PlayerFollowData(val followed: Boolean, val player: PlayerDirectoryItem? = null)

@Serializable
data class FollowedPlayersData(val players: List<PlayerDirectoryItem>)

@Serializable
data class LiveHealthData(val alive: Boolean)

@Serializable
data class ReadyHealthData(val ready: Boolean)

data class RegisteredUserRecord(
    val userId: String,
    val serverUuid: String,
    val gameId: String,
    val qq: String,
    val passwordHash: String,
)

data class VerificationRecord(
    val id: String,
    val tokenHash: String,
    val purpose: String,
    val serverUuid: String,
    val gameId: String,
    val qq: String?,
    val codeHash: String,
    val expiresAt: Instant,
    val attempts: Int,
    val maxAttempts: Int,
    val consumedAt: Instant?,
)

data class PlayerRefRecord(
    val playerRef: String,
    val serverUuid: String,
    val gameId: String,
    val qq: String?,
    val registered: Boolean,
    val online: Boolean,
    val source: String,
    val confirmedAt: Instant,
    val expiresAt: Instant?,
)

data class TransferRecord(
    val id: String,
    val clientRequestId: String,
    val userId: String,
    val requestFingerprint: String,
    val fromServerUuid: String,
    val toServerUuid: String,
    val recipientGameId: String,
    val recipientQq: String?,
    val amount: BigDecimal,
    val currency: String,
    val note: String?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Instant.iso(): String = toString()

fun BigDecimal.money(): String = setScale(2).toPlainString()

