package com.deuterium.backend.config

import java.nio.file.Files
import java.nio.file.Path
import java.net.URI
import java.util.Properties
import kotlin.io.path.exists

data class AppConfig(
    val publicServer: ServerConfig,
    val bridgeServer: ServerConfig,
    val database: DatabaseConfig,
    val security: SecurityConfig,
    val pluginBridge: PluginBridgeConfig,
    val chat: ChatConfig,
    val ai: AiConfig = AiConfig(
        enabled = false,
        providerApiKey = "",
        providerBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-v4-flash",
        requestTimeoutMillis = 120000,
        connectTimeoutMillis = 12000,
        firstTokenTimeoutMillis = 60000,
        chunkIdleTimeoutMillis = 45000,
        streamHeartbeatMillis = 8000,
        knowledgeToolTimeoutMillis = 8000,
        knowledgeMaxToolCalls = 1,
        knowledgeMaxChunks = 5,
        quotaWindowHours = 5,
        maxInputChars = 2000,
        maxContextMessages = 10
    ),
    val oneBot: OneBotConfig = OneBotConfig(
        token = "",
        botSelfId = "",
        defaultGroupId = "",
        defaultTriggerPattern = "^(/ai|!ai)\\s+(.+)$"
    ),
    val admin: AdminConfig = AdminConfig(token = ""),
    val oidc: OidcConfig,
    val app: AppVersionConfig,
    val logLevel: String,
) {
    companion object {
        fun load(configPathOverride: String? = null): AppConfig {
            val configPath = configPathOverride?.takeIf { it.isNotBlank() }
                ?: System.getenv("DEUTERIUM_CONFIG")?.takeIf { it.isNotBlank() }
                ?: "config/application.conf"
            val props = Properties()
            val path = Path.of(configPath)
            if (path.exists()) {
                Files.newInputStream(path).use(props::load)
            }
            val publicServer = ServerConfig(
                host = props.value("public.host", "0.0.0.0"),
                port = props.value("public.port", "28657").toInt()
            )
            val bridgeServer = ServerConfig(
                host = props.value("bridge.host", "127.0.0.1"),
                port = props.value("bridge.port", "28658").toInt()
            )
            return AppConfig(
                publicServer = publicServer,
                bridgeServer = bridgeServer,
                database = DatabaseConfig(
                    jdbcUrl = props.required("database.jdbcUrl"),
                    user = props.required("database.user"),
                    password = props.required("database.password"),
                    maximumPoolSize = props.value("database.maximumPoolSize", "10").toInt()
                ),
                security = SecurityConfig(
                    sessionTokenPepper = props.required("security.sessionTokenPepper"),
                    verificationPepper = props.required("security.verificationPepper"),
                    sessionDays = props.value("security.sessionDays", "30").toLong()
                ),
                pluginBridge = PluginBridgeConfig(
                    token = props.required("pluginBridge.token"),
                    requestTimeoutMillis = props.value("pluginBridge.requestTimeoutMillis", "10000").toLong(),
                    heartbeatIntervalMillis = props.value("pluginBridge.heartbeatIntervalMillis", "30000").toLong(),
                    staleAfterMillis = props.value("pluginBridge.staleAfterMillis", "90000").toLong()
                ),
                chat = ChatConfig(
                    historyRetentionDays = props.value("chat.historyRetentionDays", "30").toLong(),
                    websocketPingIntervalMillis = props.value("chat.websocketPingIntervalMillis", "15000").toLong(),
                    websocketTimeoutMillis = props.value("chat.websocketTimeoutMillis", "35000").toLong()
                ),
                ai = AiConfig(
                    enabled = props.bool("ai.enabled", true),
                    providerApiKey = props.value("ai.providerApiKey", ""),
                    providerBaseUrl = props.value("ai.providerBaseUrl", "https://api.deepseek.com").trimEnd('/'),
                    defaultModel = props.value("ai.defaultModel", "deepseek-v4-flash"),
                    requestTimeoutMillis = props.value("ai.requestTimeoutMillis", "120000").toLong(),
                    connectTimeoutMillis = props.value("ai.connectTimeoutMillis", "12000").toLong(),
                    firstTokenTimeoutMillis = props.value("ai.firstTokenTimeoutMillis", "60000").toLong(),
                    chunkIdleTimeoutMillis = props.value("ai.chunkIdleTimeoutMillis", "45000").toLong(),
                    streamHeartbeatMillis = props.value("ai.streamHeartbeatMillis", "8000").toLong(),
                    knowledgeToolTimeoutMillis = props.value("ai.knowledgeToolTimeoutMillis", "8000").toLong(),
                    knowledgeMaxToolCalls = props.value("ai.knowledgeMaxToolCalls", "1").toInt(),
                    knowledgeMaxChunks = props.value("ai.knowledgeMaxChunks", "5").toInt(),
                    quotaWindowHours = props.value("ai.quotaWindowHours", "5").toLong(),
                    maxInputChars = props.value("ai.maxInputChars", "2000").toInt(),
                    maxContextMessages = props.value("ai.maxContextMessages", "10").toInt()
                ),
                oneBot = OneBotConfig(
                    token = props.value("oneBot.token", ""),
                    botSelfId = props.value("oneBot.botSelfId", ""),
                    defaultGroupId = props.value("oneBot.defaultGroupId", ""),
                    defaultTriggerPattern = props.value("oneBot.defaultTriggerPattern", "^(/ai|!ai)\\s+(.+)$")
                ),
                admin = AdminConfig(
                    token = props.value("admin.token", "")
                ),
                oidc = OidcConfig(
                    enabled = props.bool("oidc.enabled", false),
                    issuer = props.value("oidc.issuer", "http://127.0.0.1:${publicServer.port}").trimEnd('/'),
                    clientId = props.value("oidc.clientId", "wikijs"),
                    clientSecretHash = props.value("oidc.clientSecretHash", ""),
                    redirectUri = props.value("oidc.redirectUri", "https://wiki.deuterium.cafe/login/oidc/callback"),
                    signingKeyPath = props.value("oidc.signingKeyPath", "config/oidc-signing-key.json"),
                    allowInsecureHttp = props.bool("oidc.allowInsecureHttp", false),
                    authorizationCodeMinutes = props.value("oidc.authorizationCodeMinutes", "5").toLong(),
                    accessTokenMinutes = props.value("oidc.accessTokenMinutes", "10").toLong(),
                    idTokenMinutes = props.value("oidc.idTokenMinutes", "10").toLong(),
                    webSessionHours = props.value("oidc.webSessionHours", "12").toLong()
                ),
                app = AppVersionConfig(
                    latestVersionCode = props.value("app.latestVersionCode", "5").toInt(),
                    latestVersionName = props.value("app.latestVersionName", "1.0.3")
                ),
                logLevel = props.value("log.level", "INFO")
            )
        }

        private fun Properties.value(key: String, default: String): String =
            System.getenv(key.envName())?.takeIf { it.isNotBlank() }
                ?: getProperty(key)?.takeIf { it.isNotBlank() }
                ?: default

        private fun Properties.bool(key: String, default: Boolean): Boolean =
            value(key, default.toString()).lowercase().let {
                when (it) {
                    "true", "1", "yes", "y", "on" -> true
                    "false", "0", "no", "n", "off" -> false
                    else -> error("Invalid boolean config value: $key")
                }
            }

        private fun Properties.required(key: String): String {
            val value = System.getenv(key.envName())?.takeIf { it.isNotBlank() }
                ?: getProperty(key)?.takeIf { it.isNotBlank() }
            require(!value.isNullOrBlank() && !value.startsWith("CHANGE_ME")) {
                "Missing required config value: $key"
            }
            return value
        }

        private fun String.envName(): String =
            "DEUTERIUM_" + uppercase().replace('.', '_')
    }
}

data class ServerConfig(val host: String, val port: Int)

data class DatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val maximumPoolSize: Int,
)

data class SecurityConfig(
    val sessionTokenPepper: String,
    val verificationPepper: String,
    val sessionDays: Long,
)

data class PluginBridgeConfig(
    val token: String,
    val requestTimeoutMillis: Long,
    val heartbeatIntervalMillis: Long,
    val staleAfterMillis: Long,
)

data class ChatConfig(
    val historyRetentionDays: Long,
    val websocketPingIntervalMillis: Long,
    val websocketTimeoutMillis: Long,
) {
    init {
        require(historyRetentionDays > 0) { "chat.historyRetentionDays must be positive." }
        require(websocketPingIntervalMillis > 0) { "chat.websocketPingIntervalMillis must be positive." }
        require(websocketTimeoutMillis > websocketPingIntervalMillis) {
            "chat.websocketTimeoutMillis must be greater than chat.websocketPingIntervalMillis."
        }
    }
}

data class AiConfig(
    val enabled: Boolean,
    val providerApiKey: String,
    val providerBaseUrl: String,
    val defaultModel: String,
    val requestTimeoutMillis: Long,
    val connectTimeoutMillis: Long,
    val firstTokenTimeoutMillis: Long,
    val chunkIdleTimeoutMillis: Long,
    val streamHeartbeatMillis: Long,
    val knowledgeToolTimeoutMillis: Long,
    val knowledgeMaxToolCalls: Int,
    val knowledgeMaxChunks: Int,
    val quotaWindowHours: Long,
    val maxInputChars: Int,
    val maxContextMessages: Int,
) {
    init {
        require(providerBaseUrl.isNotBlank()) { "ai.providerBaseUrl must not be blank." }
        require(requestTimeoutMillis > 0) { "ai.requestTimeoutMillis must be positive." }
        require(connectTimeoutMillis > 0) { "ai.connectTimeoutMillis must be positive." }
        require(firstTokenTimeoutMillis > 0) { "ai.firstTokenTimeoutMillis must be positive." }
        require(chunkIdleTimeoutMillis > 0) { "ai.chunkIdleTimeoutMillis must be positive." }
        require(streamHeartbeatMillis in 1000..60000) { "ai.streamHeartbeatMillis must be 1000..60000." }
        require(knowledgeToolTimeoutMillis > 0) { "ai.knowledgeToolTimeoutMillis must be positive." }
        require(knowledgeMaxToolCalls in 0..2) { "ai.knowledgeMaxToolCalls must be 0..2." }
        require(knowledgeMaxChunks in 0..12) { "ai.knowledgeMaxChunks must be 0..12." }
        require(quotaWindowHours > 0) { "ai.quotaWindowHours must be positive." }
        require(maxInputChars in 1..10000) { "ai.maxInputChars must be 1..10000." }
        require(maxContextMessages in 0..50) { "ai.maxContextMessages must be 0..50." }
    }
}

data class OneBotConfig(
    val token: String,
    val botSelfId: String,
    val defaultGroupId: String,
    val defaultTriggerPattern: String,
)

data class AdminConfig(
    val token: String,
)

data class OidcConfig(
    val enabled: Boolean,
    val issuer: String,
    val clientId: String,
    val clientSecretHash: String,
    val redirectUri: String,
    val signingKeyPath: String,
    val allowInsecureHttp: Boolean,
    val authorizationCodeMinutes: Long,
    val accessTokenMinutes: Long,
    val idTokenMinutes: Long,
    val webSessionHours: Long,
) {
    init {
        if (enabled) {
            require(isHttpsOrAllowedHttp(issuer)) {
                "oidc.issuer must be HTTPS outside localhost unless oidc.allowInsecureHttp=true."
            }
            require(clientId.isNotBlank()) { "oidc.clientId is required when OIDC is enabled." }
            require(clientSecretHash.isNotBlank() && !clientSecretHash.startsWith("CHANGE_ME")) {
                "oidc.clientSecretHash is required when OIDC is enabled."
            }
            require(isHttpsOrAllowedHttp(redirectUri)) {
                "oidc.redirectUri must be HTTPS outside localhost unless oidc.allowInsecureHttp=true."
            }
            require(signingKeyPath.isNotBlank()) { "oidc.signingKeyPath is required when OIDC is enabled." }
        }
        require(authorizationCodeMinutes > 0) { "oidc.authorizationCodeMinutes must be positive." }
        require(accessTokenMinutes > 0) { "oidc.accessTokenMinutes must be positive." }
        require(idTokenMinutes > 0) { "oidc.idTokenMinutes must be positive." }
        require(webSessionHours > 0) { "oidc.webSessionHours must be positive." }
    }

    private fun isHttpsOrAllowedHttp(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> allowInsecureHttp || uri.host in setOf("127.0.0.1", "localhost", "::1")
            else -> false
        }
    }
}

data class AppVersionConfig(
    val latestVersionCode: Int,
    val latestVersionName: String,
)
