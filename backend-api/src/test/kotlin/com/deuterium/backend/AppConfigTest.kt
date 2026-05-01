package com.deuterium.backend

import com.deuterium.backend.config.AppConfig
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

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
}

