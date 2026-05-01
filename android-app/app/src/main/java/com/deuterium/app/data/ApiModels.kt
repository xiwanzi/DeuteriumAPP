package com.deuterium.app.data

data class ApiResponse<T>(
    val requestId: String? = null,
    val data: T? = null,
    val page: Page? = null
)

data class ApiErrorResponse(
    val requestId: String? = null,
    val error: ApiErrorBody? = null
)

data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
    val details: Map<String, Any>? = null,
    val retryAfterSeconds: Long? = null
)

data class Page(val nextCursor: String? = null)

data class RegistrationCodeRequest(val gameId: String, val qq: String, val password: String)

data class RegisterRequest(val verificationToken: String, val code: String, val password: String)

data class LoginRequest(val account: String, val password: String)

data class PasswordResetCodeRequest(val account: String)

data class PasswordResetRequest(val verificationToken: String, val code: String, val newPassword: String)

data class CreateTransferRequest(
    val clientRequestId: String,
    val recipientPlayerRef: String,
    val amount: String,
    val note: String?
)

data class UserProfile(
    val userId: String,
    val playerRef: String,
    val gameId: String,
    val qq: String,
    val identityStatus: String = "bound"
)

data class PlayerSummary(
    val playerRef: String,
    val gameId: String,
    val qq: String? = null,
    val online: Boolean = false,
    val registered: Boolean = false,
    val source: String = "search"
)

data class ResolvedPlayerRef(
    val playerRef: String,
    val gameId: String,
    val qq: String? = null,
    val online: Boolean = false,
    val registered: Boolean = false,
    val source: String = "search",
    val confirmedAt: String? = null,
    val expiresAt: String? = null
)

data class WalletBalance(
    val currency: String,
    val amount: String,
    val fresh: Boolean,
    val refreshedAt: String?
)

data class WalletRecord(
    val recordId: String,
    val direction: String,
    val otherPlayer: PlayerSummary,
    val amount: String,
    val currency: String,
    val status: String,
    val note: String? = null,
    val occurredAt: String
)

data class Transfer(
    val transferId: String,
    val clientRequestId: String,
    val recipient: PlayerSummary,
    val amount: String,
    val currency: String,
    val note: String? = null,
    val status: String,
    val createdAt: String,
    val updatedAt: String
)

data class ChatMessage(
    val messageId: String,
    val sender: PlayerSummary,
    val content: String,
    val kind: String = "public_chat",
    val sentAt: String
)

data class ServerEvent(
    val eventId: String,
    val eventType: String,
    val content: String,
    val occurredAt: String
)

data class OnlinePlayer(
    val playerRef: String,
    val gameId: String,
    val qq: String? = null,
    val registered: Boolean = false,
    val onlineSince: String? = null
)

data class PlayerDirectoryItem(
    val playerRef: String,
    val gameId: String,
    val qq: String? = null,
    val registered: Boolean = false,
    val serverOnline: Boolean = false,
    val appStatus: String = "long_offline",
    val appConnected: Boolean = false,
    val appForeground: Boolean = false,
    val appLastSeenAt: String? = null,
    val onlineSince: String? = null,
    val followed: Boolean = false,
    val self: Boolean = false
)

data class VerificationTokenData(
    val verificationToken: String,
    val expiresAt: String,
    val resendAfterSeconds: Int
)

data class AuthData(val token: String, val user: UserProfile)

data class PasswordResetData(val passwordReset: Boolean)

data class LogoutData(val loggedOut: Boolean)

data class UserProfileData(val user: UserProfile)

data class WalletBalanceData(val balance: WalletBalance)

data class RecipientSearchData(val candidates: List<ResolvedPlayerRef> = emptyList())

data class TransferData(val transfer: Transfer)

data class WalletRecordsData(val records: List<WalletRecord> = emptyList())

data class AppUpdateCheckData(
    val latest: Boolean,
    val message: String,
    val latestVersionCode: Int,
    val latestVersionName: String
)

data class ChatMessagesData(val messages: List<ChatMessage> = emptyList())

data class PresenceData(val onlineCount: Int, val available: Boolean, val updatedAt: String?)

data class OnlinePlayersData(val players: List<OnlinePlayer> = emptyList())

data class PlayerDirectoryData(val players: List<PlayerDirectoryItem> = emptyList())

data class PlayerFollowRequest(val playerRef: String)

data class PlayerFollowData(val followed: Boolean, val player: PlayerDirectoryItem? = null)

data class FollowedPlayersData(val players: List<PlayerDirectoryItem> = emptyList())

data class AppStatePayload(val foreground: Boolean)

data class WalletRecordEventData(val record: WalletRecord)

data class ChatMentionEventData(val message: ChatMessage)

data class ChatFeedItem(
    val id: String,
    val sender: String,
    val senderPlayerRef: String? = null,
    val content: String,
    val time: String,
    val mine: Boolean = false,
    val event: Boolean = false,
    val sentAt: String? = null,
    val kind: String = "public_chat"
)

data class ChatHistoryItem(
    val messageId: String,
    val sender: String,
    val content: String,
    val kind: String,
    val sentAt: String,
    val displayTime: String,
    val mine: Boolean,
    val event: Boolean
)

sealed class RepoResult<out T> {
    data class Success<T>(val value: T) : RepoResult<T>()
    data class Error(
        val message: String,
        val code: String? = null,
        val retryAfterSeconds: Long? = null
    ) : RepoResult<Nothing>()
}

