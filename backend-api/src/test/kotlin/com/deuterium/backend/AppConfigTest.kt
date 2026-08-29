package com.deuterium.backend

import com.deuterium.backend.config.AppConfig
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppConfigTest {
    @Test
    fun `chat websocket heartbeat config has production defaults`() {
        val file = Files.createTempFile("deuterium-app-config", ".conf")
        Files.writeString(
            file,
            """
            database.jdbcUrl=jdbc:h2:mem:test
            database.user=test
            database.password=test
            security.sessionTokenPepper=session-pepper
            security.verificationPepper=verification-pepper
            pluginBridge.token=bridge-token
            """.trimIndent()
        )

        val config = AppConfig.load(file.toString())

        assertEquals(30, config.chat.historyRetentionDays)
        assertEquals(15_000, config.chat.websocketPingIntervalMillis)
        assertEquals(35_000, config.chat.websocketTimeoutMillis)
        assertEquals(5, config.app.latestVersionCode)
        assertEquals("1.0.3", config.app.latestVersionName)
    }

    @Test
    fun `chat websocket heartbeat config can be overridden by config file`() {
        val file = Files.createTempFile("deuterium-app-config", ".conf")
        Files.writeString(
            file,
            """
            database.jdbcUrl=jdbc:h2:mem:test
            database.user=test
            database.password=test
            security.sessionTokenPepper=session-pepper
            security.verificationPepper=verification-pepper
            pluginBridge.token=bridge-token
            chat.websocketPingIntervalMillis=20000
            chat.websocketTimeoutMillis=45000
            """.trimIndent()
        )

        val config = AppConfig.load(file.toString())

        assertEquals(20_000, config.chat.websocketPingIntervalMillis)
        assertEquals(45_000, config.chat.websocketTimeoutMillis)
    }

    @Test
    fun `oidc rejects non-local http issuer unless explicitly allowed`() {
        val file = Files.createTempFile("deuterium-app-config", ".conf")
        Files.writeString(file, oidcConfig(allowInsecureHttp = false))

        assertFailsWith<IllegalArgumentException> {
            AppConfig.load(file.toString())
        }
    }

    @Test
    fun `oidc allows non-local http issuer when explicitly configured for internal environment`() {
        val file = Files.createTempFile("deuterium-app-config", ".conf")
        Files.writeString(file, oidcConfig(allowInsecureHttp = true))

        val config = AppConfig.load(file.toString())

        assertEquals("http://106.52.237.179", config.oidc.issuer)
        assertEquals("http://wiki.internal/login/oidc/callback", config.oidc.redirectUri)
        assertEquals(true, config.oidc.allowInsecureHttp)
    }

    private fun oidcConfig(allowInsecureHttp: Boolean): String =
        """
        database.jdbcUrl=jdbc:h2:mem:test
        database.user=test
        database.password=test
        security.sessionTokenPepper=session-pepper
        security.verificationPepper=verification-pepper
        pluginBridge.token=bridge-token
        oidc.enabled=true
        oidc.issuer=http://106.52.237.179
        oidc.clientId=wikijs
        oidc.clientSecretHash=${"a".repeat(64)}
        oidc.redirectUri=http://wiki.internal/login/oidc/callback
        oidc.allowInsecureHttp=$allowInsecureHttp
        """.trimIndent()
}
