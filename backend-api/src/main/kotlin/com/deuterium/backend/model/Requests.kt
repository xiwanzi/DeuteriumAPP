package com.deuterium.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class RegistrationCodeRequest(val gameId: String, val qq: String, val password: String)

@Serializable
data class RegisterRequest(val verificationToken: String, val code: String, val password: String)

@Serializable
data class LoginRequest(val account: String, val password: String)

@Serializable
data class PasswordResetCodeRequest(val account: String)

@Serializable
data class PasswordResetRequest(val verificationToken: String, val code: String, val newPassword: String)

@Serializable
data class CreateTransferRequest(
    val clientRequestId: String,
    val recipientPlayerRef: String,
    val amount: String,
    val note: String? = null,
)

@Serializable
data class ChatSendPayload(
    val clientMessageId: String,
    val content: String,
    val mentionedPlayerRefs: List<String> = emptyList(),
)

