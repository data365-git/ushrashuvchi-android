package com.example.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = org.slf4j.LoggerFactory.getLogger("Db")

object Db {
    fun init() {
        val rawUrl = System.getenv("DATABASE_URL")
            ?: error("DATABASE_URL not set")
        // Parse postgres://user:pass@host:port/db into JDBC URL + separate creds.
        // The PG JDBC driver does NOT accept embedded user:pass in the URL — it
        // tries to interpret user:pass@host as a hostname.
        val uri = java.net.URI(rawUrl.removePrefix("jdbc:"))
        val user = uri.userInfo?.substringBefore(":")
        val password = uri.userInfo?.substringAfter(":", missingDelimiterValue = "")
        val host = uri.host ?: error("DATABASE_URL has no host")
        val port = if (uri.port > 0) uri.port else 5432
        val database = uri.path?.trimStart('/') ?: "postgres"
        val jdbcUrl = "jdbc:postgresql://$host:$port/$database"
        logger.info("Connecting to $jdbcUrl as user=$user")
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
            connectionTimeout = 30_000
            initializationFailTimeout = -1L
        }
        try {
            val ds = HikariDataSource(config)
            // Test the connection eagerly so we see the real error in logs.
            ds.connection.use { it.createStatement().use { st -> st.executeQuery("SELECT 1").close() } }
            Database.connect(ds)
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    Devices, Meetings, TranscriptLines, Tasks, ShareTokens
                )
            }
            logger.info("Connected and schema verified")
        } catch (e: Exception) {
            logger.error("DB init failed: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }
    }
}
