package com.deuterium.backend.config

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists

data class AppConfig(
    val publicServer: ServerConfig,
    val bridgeServer: ServerConfig,
    val database: DatabaseConfig,
    val security: SecurityConfig,
    val pluginBridge: PluginBridgeConfig,
    val chat: ChatConfig,
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
            return AppConfig(
                publicServer = ServerConfig(
                    host = props.value("public.host", "0.0.0.0"),
                    port = props.value("public.port", "28657").toInt()
                ),
                bridgeServer = ServerConfig(
                    host = props.value("bridge.host", "127.0.0.1"),
                    port = props.value("bridge.port", "28658").toInt()
                ),
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

data class AppVersionConfig(
    val latestVersionCode: Int,
    val latestVersionName: String,
)

