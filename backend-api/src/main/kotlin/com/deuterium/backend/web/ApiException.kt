package com.deuterium.backend.web

class ApiException(
    val code: String,
    override val message: String,
    val status: Int,
    val retryAfterSeconds: Long? = null,
    val details: Map<String, String> = emptyMap(),
) : RuntimeException(message) {
    companion object {
        fun invalid(code: String, message: String, status: Int = 400, retryAfterSeconds: Long? = null): ApiException =
            ApiException(code, message, status, retryAfterSeconds)
    }
}

class PluginBridgeUnavailable : RuntimeException("Plugin bridge unavailable")

class PluginBridgeTimeout : RuntimeException("Plugin bridge request timed out")


