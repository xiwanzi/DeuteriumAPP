package com.deuterium.backend.db

import com.deuterium.backend.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import java.sql.DriverManager

object DatabaseFactory {
    private val resetTables = listOf(
        "player_follows",
        "app_presence",
        "wallet_records",
        "transfers",
        "wallet_balances",
        "presence_snapshots",
        "server_events",
        "chat_messages",
        "player_refs",
        "login_failures",
        "verification_requests",
        "sessions",
        "app_users"
    )

    fun connect(config: DatabaseConfig): HikariDataSource {
        ensureMysqlDatabase(config)
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.user
            password = config.password
            maximumPoolSize = config.maximumPoolSize
            driverClassName = when {
                config.jdbcUrl.startsWith("jdbc:h2:") -> "org.h2.Driver"
                else -> "com.mysql.cj.jdbc.Driver"
            }
            validate()
        }
        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)
        return dataSource
    }

    fun migrate(config: DatabaseConfig) {
        ensureMysqlDatabase(config)
        Flyway.configure()
            .dataSource(config.jdbcUrl, config.user, config.password)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
            .migrate()
    }

    fun resetData(config: DatabaseConfig) {
        ensureMysqlDatabase(config)
        DriverManager.getConnection(config.jdbcUrl, config.user, config.password).use { connection ->
            connection.autoCommit = false
            try {
                connection.createStatement().use { statement ->
                    statement.execute("SET FOREIGN_KEY_CHECKS=0")
                    resetTables.forEach { table ->
                        statement.executeUpdate("DELETE FROM `$table`")
                    }
                    statement.execute("SET FOREIGN_KEY_CHECKS=1")
                }
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.createStatement().use { statement ->
                    statement.execute("SET FOREIGN_KEY_CHECKS=1")
                }
            }
        }
    }

    private fun ensureMysqlDatabase(config: DatabaseConfig) {
        if (!config.jdbcUrl.startsWith("jdbc:mysql://")) {
            return
        }
        val match = Regex("""^(jdbc:mysql://[^/?]+/)([^?]+)(\?.*)?$""").find(config.jdbcUrl) ?: return
        val prefix = match.groupValues[1]
        val databaseName = match.groupValues[2]
        val suffix = match.groupValues.getOrNull(3).orEmpty()
        if (databaseName.isBlank()) {
            return
        }
        val serverUrl = "$prefix$suffix"
        DriverManager.getConnection(serverUrl, config.user, config.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "CREATE DATABASE IF NOT EXISTS `${databaseName.replace("`", "``")}` " +
                        "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
                )
            }
        }
    }
}

