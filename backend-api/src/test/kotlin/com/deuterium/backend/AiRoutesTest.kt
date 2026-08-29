package com.deuterium.backend

import com.deuterium.backend.config.AdminConfig
import com.deuterium.backend.config.AiConfig
import com.deuterium.backend.config.AppConfig
import com.deuterium.backend.config.AppVersionConfig
import com.deuterium.backend.config.ChatConfig
import com.deuterium.backend.config.DatabaseConfig
import com.deuterium.backend.config.OidcConfig
import com.deuterium.backend.config.OneBotConfig
import com.deuterium.backend.config.PluginBridgeConfig
import com.deuterium.backend.config.SecurityConfig
import com.deuterium.backend.config.ServerConfig
import com.deuterium.backend.db.AppPresence
import com.deuterium.backend.db.AiAdminEvents
import com.deuterium.backend.db.AiConversations
import com.deuterium.backend.db.AiEntitlements
import com.deuterium.backend.db.AiKnowledgeItems
import com.deuterium.backend.db.AiKnowledgeChunks
import com.deuterium.backend.db.AiKnowledgeDocuments
import com.deuterium.backend.db.AiMemoryItems
import com.deuterium.backend.db.AiMessages
import com.deuterium.backend.db.AiModelConfigs
import com.deuterium.backend.db.AiPlans
import com.deuterium.backend.db.AiPromptVersions
import com.deuterium.backend.db.AiPurchases
import com.deuterium.backend.db.AiQqGroups
import com.deuterium.backend.db.AiQuotaUsage
import com.deuterium.backend.db.AiRequestAudits
import com.deuterium.backend.db.AiRequestExchanges
import com.deuterium.backend.db.AiSettings
import com.deuterium.backend.db.ChatMessages
import com.deuterium.backend.db.LoginFailures
import com.deuterium.backend.db.PlayerFollows
import com.deuterium.backend.db.PlayerRefs
import com.deuterium.backend.db.PresenceSnapshots
import com.deuterium.backend.db.ServerEvents
import com.deuterium.backend.db.Sessions
import com.deuterium.backend.db.Transfers
import com.deuterium.backend.db.Users
import com.deuterium.backend.db.VerificationRequests
import com.deuterium.backend.db.WalletBalances
import com.deuterium.backend.db.WalletRecords
import com.deuterium.backend.util.Secrets
import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.parallel.ResourceLock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ResourceLock("exposed-default-database")
class AiRoutesTest {
    @Test
    fun `ai chat stream forwards provider deltas before done`() {
        val providerRequestBody = AtomicReference("")
        val provider = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        provider.createContext("/chat/completions") { exchange ->
            providerRequestBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            val response = """
                data: {"choices":[{"delta":{"content":"你"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"好"},"finish_reason":null}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

            """.trimIndent()
            exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
            exchange.sendResponseHeaders(200, response.toByteArray(Charsets.UTF_8).size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray(Charsets.UTF_8)) }
        }
        provider.start()
        try {
            withAiApp(providerBaseUrl = "http://127.0.0.1:${provider.address.port}") { services ->
                val user = seedUser(services)
                testApplication {
                    application { publicModule(services) }

                    val login = client.post("/api/v1/account/login") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"account":"Alice","password":"$TestPassword"}""")
                    }
                    assertEquals(HttpStatusCode.OK, login.status)
                    val token = Json.parseToJsonElement(login.bodyAsText())
                        .jsonObject["data"]!!
                        .jsonObject["token"]!!
                        .jsonPrimitive.content
                    val authenticated = transaction {
                        services.sessions.authenticate(Secrets.sha256(token, services.config.security.sessionTokenPepper))
                    }
                    assertEquals(user.userId, authenticated?.userId)

                    val stream = client.post("/api/v1/ai/chat/stream") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"clientMessageId":"client-1","content":"你好"}""")
                    }

                    assertEquals(HttpStatusCode.OK, stream.status)
                    val body = stream.bodyAsText()
                    assertTrue(body.indexOf("event: meta") < body.indexOf("event: delta"))
                    assertTrue(body.indexOf("event: delta") < body.indexOf("event: done"))
                    assertTrue(body.contains("你"))
                    assertTrue(body.contains("好"))
                    assertTrue(providerRequestBody.get().contains(""""stream":true"""))
                    assertTrue(user.userId.isNotBlank())
                }
            }
        } finally {
            provider.stop(0)
        }
    }

    @Test
    fun `ai chat stream replays completed request for duplicate client message id`() {
        val providerHits = AtomicInteger(0)
        val provider = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        provider.createContext("/chat/completions") { exchange ->
            providerHits.incrementAndGet()
            exchange.requestBody.bufferedReader().use { it.readText() }
            val response = """
                data: {"choices":[{"delta":{"content":"重复答案"},"finish_reason":null}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

            """.trimIndent()
            exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
            exchange.sendResponseHeaders(200, response.toByteArray(Charsets.UTF_8).size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray(Charsets.UTF_8)) }
        }
        provider.start()
        try {
            withAiApp(providerBaseUrl = "http://127.0.0.1:${provider.address.port}") { services ->
                seedUser(services)
                testApplication {
                    application { publicModule(services) }

                    val login = client.post("/api/v1/account/login") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"account":"Alice","password":"$TestPassword"}""")
                    }
                    val token = Json.parseToJsonElement(login.bodyAsText())
                        .jsonObject["data"]!!
                        .jsonObject["token"]!!
                        .jsonPrimitive.content

                    repeat(2) {
                        val stream = client.post("/api/v1/ai/chat/stream") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                            contentType(ContentType.Application.Json)
                            setBody("""{"clientMessageId":"client-duplicate","content":"同一个问题"}""")
                        }
                        assertEquals(HttpStatusCode.OK, stream.status)
                        assertTrue(stream.bodyAsText().contains("重复答案"))
                    }
                    assertEquals(1, providerHits.get())
                }
            }
        } finally {
            provider.stop(0)
        }
    }

    @Test
    fun `ai chat stream releases exchange after partial provider stream stalls`() {
        val providerHits = AtomicInteger(0)
        val providerExecutor = Executors.newCachedThreadPool()
        val provider = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        provider.executor = providerExecutor
        provider.createContext("/chat/completions") { exchange ->
            val hit = providerHits.incrementAndGet()
            exchange.requestBody.bufferedReader().use { it.readText() }
            exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
            if (hit == 1) {
                exchange.sendResponseHeaders(200, 0)
                try {
                    val partial = """data: {"choices":[{"delta":{"content":"半截"},"finish_reason":null}]}""" + "\n\n"
                    exchange.responseBody.write(partial.toByteArray(Charsets.UTF_8))
                    exchange.responseBody.flush()
                    Thread.sleep(2_000)
                } finally {
                    exchange.responseBody.close()
                }
            } else {
                val response = """
                    data: {"choices":[{"delta":{"content":"恢复"},"finish_reason":null}]}

                    data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                    data: [DONE]

                """.trimIndent()
                exchange.sendResponseHeaders(200, response.toByteArray(Charsets.UTF_8).size.toLong())
                exchange.responseBody.use { it.write(response.toByteArray(Charsets.UTF_8)) }
            }
        }
        provider.start()
        try {
            val providerBaseUrl = "http://127.0.0.1:${provider.address.port}"
            withAiApp(
                providerBaseUrl = providerBaseUrl,
                aiConfig = testAiConfig(
                    providerBaseUrl = providerBaseUrl,
                    requestTimeoutMillis = 5_000,
                    firstTokenTimeoutMillis = 1_000,
                    chunkIdleTimeoutMillis = 250
                )
            ) { services ->
                seedUser(services)
                testApplication {
                    application { publicModule(services) }

                    val login = client.post("/api/v1/account/login") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"account":"Alice","password":"$TestPassword"}""")
                    }
                    val token = Json.parseToJsonElement(login.bodyAsText())
                        .jsonObject["data"]!!
                        .jsonObject["token"]!!
                        .jsonPrimitive.content

                    val stalledStream = client.post("/api/v1/ai/chat/stream") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"clientMessageId":"client-stall","content":"先卡住"}""")
                    }
                    assertEquals(HttpStatusCode.OK, stalledStream.status)
                    val stalledBody = stalledStream.bodyAsText()
                    assertTrue(stalledBody.contains("event: delta"))
                    assertTrue(stalledBody.contains("半截"))
                    assertTrue(stalledBody.contains("event: error"))
                    assertTrue(stalledBody.contains("AI_PROVIDER_TIMEOUT"))
                    assertFalse(stalledBody.contains("event: done"))
                    assertExchangeNotProcessing("client-stall")

                    val nextStream = client.post("/api/v1/ai/chat/stream") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"clientMessageId":"client-after-stall","content":"再试一次"}""")
                    }
                    assertEquals(HttpStatusCode.OK, nextStream.status)
                    val nextBody = nextStream.bodyAsText()
                    assertTrue(nextBody.contains("恢复"))
                    assertTrue(nextBody.contains("event: done"))
                    assertEquals(2, providerHits.get())
                    assertExchangeNotProcessing("client-after-stall")
                }
            }
        } finally {
            provider.stop(0)
            providerExecutor.shutdownNow()
        }
    }

    @Test
    fun `ai chat stream treats provider eof before terminal as error`() {
        val provider = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        provider.createContext("/chat/completions") { exchange ->
            exchange.requestBody.bufferedReader().use { it.readText() }
            val response = """data: {"choices":[{"delta":{"content":"半截 EOF"},"finish_reason":null}]}""" + "\n\n"
            exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
            exchange.sendResponseHeaders(200, response.toByteArray(Charsets.UTF_8).size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray(Charsets.UTF_8)) }
        }
        provider.start()
        try {
            withAiApp(providerBaseUrl = "http://127.0.0.1:${provider.address.port}") { services ->
                seedUser(services)
                testApplication {
                    application { publicModule(services) }

                    val login = client.post("/api/v1/account/login") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"account":"Alice","password":"$TestPassword"}""")
                    }
                    val token = Json.parseToJsonElement(login.bodyAsText())
                        .jsonObject["data"]!!
                        .jsonObject["token"]!!
                        .jsonPrimitive.content

                    val stream = client.post("/api/v1/ai/chat/stream") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"clientMessageId":"client-eof","content":"EOF 测试"}""")
                    }

                    assertEquals(HttpStatusCode.OK, stream.status)
                    val body = stream.bodyAsText()
                    assertTrue(body.contains("event: delta"))
                    assertTrue(body.contains("半截 EOF"))
                    assertTrue(body.contains("event: error"))
                    assertFalse(body.contains("event: done"))
                    assertExchangeNotProcessing("client-eof")
                }
            }
        } finally {
            provider.stop(0)
        }
    }

    @Test
    fun `ai chat stream stops hidden reasoning by total timeout and allows next request`() {
        val providerHits = AtomicInteger(0)
        val providerExecutor = Executors.newCachedThreadPool()
        val provider = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        provider.executor = providerExecutor
        provider.createContext("/chat/completions") { exchange ->
            val hit = providerHits.incrementAndGet()
            exchange.requestBody.bufferedReader().use { it.readText() }
            exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
            if (hit == 1) {
                exchange.sendResponseHeaders(200, 0)
                try {
                    repeat(40) {
                        val reasoning = """data: {"choices":[{"delta":{"reasoning_content":"思考$it"},"finish_reason":null}]}""" + "\n\n"
                        exchange.responseBody.write(reasoning.toByteArray(Charsets.UTF_8))
                        exchange.responseBody.flush()
                        Thread.sleep(50)
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { exchange.responseBody.close() }
                }
            } else {
                val response = """
                    data: {"choices":[{"delta":{"content":"恢复"},"finish_reason":null}]}

                    data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                    data: [DONE]

                """.trimIndent()
                exchange.sendResponseHeaders(200, response.toByteArray(Charsets.UTF_8).size.toLong())
                exchange.responseBody.use { it.write(response.toByteArray(Charsets.UTF_8)) }
            }
        }
        provider.start()
        try {
            val providerBaseUrl = "http://127.0.0.1:${provider.address.port}"
            withAiApp(
                providerBaseUrl = providerBaseUrl,
                aiConfig = testAiConfig(
                    providerBaseUrl = providerBaseUrl,
                    requestTimeoutMillis = 700,
                    firstTokenTimeoutMillis = 300,
                    chunkIdleTimeoutMillis = 500
                )
            ) { services ->
                seedUser(services)
                testApplication {
                    application { publicModule(services) }

                    val login = client.post("/api/v1/account/login") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"account":"Alice","password":"$TestPassword"}""")
                    }
                    val token = Json.parseToJsonElement(login.bodyAsText())
                        .jsonObject["data"]!!
                        .jsonObject["token"]!!
                        .jsonPrimitive.content

                    val hiddenStream = client.post("/api/v1/ai/chat/stream") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"clientMessageId":"client-reasoning-timeout","content":"只思考不输出"}""")
                    }
                    assertEquals(HttpStatusCode.OK, hiddenStream.status)
                    val hiddenBody = hiddenStream.bodyAsText()
                    assertTrue(hiddenBody.contains("event: error"))
                    assertTrue(hiddenBody.contains("AI_PROVIDER_TIMEOUT"))
                    assertFalse(hiddenBody.contains("event: delta"))
                    assertFalse(hiddenBody.contains("event: done"))
                    assertExchangeNotProcessing("client-reasoning-timeout")

                    val nextStream = client.post("/api/v1/ai/chat/stream") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"clientMessageId":"client-after-reasoning-timeout","content":"恢复请求"}""")
                    }
                    assertEquals(HttpStatusCode.OK, nextStream.status)
                    val nextBody = nextStream.bodyAsText()
                    assertTrue(nextBody.contains("恢复"))
                    assertTrue(nextBody.contains("event: done"))
                    assertEquals(2, providerHits.get())
                    assertExchangeNotProcessing("client-after-reasoning-timeout")
                }
            }
        } finally {
            provider.stop(0)
            providerExecutor.shutdownNow()
        }
    }

    @Test
    fun `late success cannot complete failed exchange or consume quota`() {
        withAiApp(providerBaseUrl = "http://127.0.0.1:1") { services ->
            val user = seedUser(services)
            val assistantMessageId = "ai_assistant_late_success"
            val setup = transaction {
                val exchange = services.aiRepository.beginRequestExchange(user.userId, "client-late-success").exchange
                val conversation = services.aiRepository.currentConversation(user.userId)
                val userMessage = services.aiRepository.appendMessage(
                    conversationId = conversation.id,
                    userId = user.userId,
                    role = "user",
                    content = "迟到成功测试",
                    clientMessageId = "client-late-success"
                )
                services.aiRepository.markRequestExchangeStreaming(
                    id = exchange.id,
                    conversationId = conversation.id,
                    userMessageId = userMessage.messageId.orEmpty(),
                    assistantMessageId = assistantMessageId
                )
                services.aiRepository.markRequestExchangeFailed(exchange.id, "AI_REQUEST_STALE", "stale processing request")
                exchange.id to conversation.id
            }
            val usedBefore = transaction {
                services.aiRepository.quota(user.userId, services.aiRepository.currentPlan(user.userId)).used
            }

            val completedRows = transaction {
                val updated = services.aiRepository.markRequestExchangeCompleted(setup.first, assistantMessageId)
                if (updated > 0) {
                    services.aiRepository.appendMessage(setup.second, user.userId, "assistant", "不应写入", messageId = assistantMessageId)
                    services.aiRepository.consumeQuota(user.userId, services.aiRepository.currentPlan(user.userId))
                }
                updated
            }

            val (status, assistantCount, usedAfter) = transaction {
                val status = AiRequestExchanges.selectAll()
                    .where { AiRequestExchanges.clientMessageId eq "client-late-success" }
                    .single()[AiRequestExchanges.status]
                val assistantCount = services.aiRepository.listMessages(setup.second, 20)
                    .count { it.role == "assistant" }
                val used = services.aiRepository.quota(user.userId, services.aiRepository.currentPlan(user.userId)).used
                Triple(status, assistantCount, used)
            }
            assertEquals(0, completedRows)
            assertEquals("failed", status)
            assertEquals(0, assistantCount)
            assertEquals(usedBefore, usedAfter)
        }
    }

    private fun withAiApp(
        providerBaseUrl: String,
        aiConfig: AiConfig = testAiConfig(providerBaseUrl),
        block: (ApplicationServices) -> Unit
    ) {
        val database = Database.connect(
            url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction(database) {
            SchemaUtils.create(
                Users,
                Sessions,
                VerificationRequests,
                LoginFailures,
                PlayerRefs,
                WalletBalances,
                Transfers,
                WalletRecords,
                ChatMessages,
                ServerEvents,
                PresenceSnapshots,
                AppPresence,
                PlayerFollows,
                AiPromptVersions,
                AiModelConfigs,
                AiSettings,
                AiPlans,
                AiEntitlements,
                AiQuotaUsage,
                AiPurchases,
                AiConversations,
                AiMessages,
                AiRequestExchanges,
                AiMemoryItems,
                AiKnowledgeItems,
                AiKnowledgeDocuments,
                AiKnowledgeChunks,
                AiRequestAudits,
                AiQqGroups,
                AiAdminEvents
            )
        }
        val services = ApplicationServices.create(testConfig(providerBaseUrl, aiConfig))
        transaction(database) {
            services.aiRepository.updateSetting("assistant_name", "xxxAI")
            AiPlans.insert {
                it[AiPlans.id] = "plan_free"
                it[AiPlans.code] = "free"
                it[AiPlans.name] = "Free"
                it[AiPlans.description] = "默认免费额度"
                it[AiPlans.price] = BigDecimal.ZERO
                it[AiPlans.currency] = "CREDIT"
                it[AiPlans.quotaPerWindow] = 20
                it[AiPlans.quotaWindowHours] = 5
                it[AiPlans.durationDays] = 0
                it[AiPlans.modelTier] = "flash"
                it[AiPlans.active] = true
                it[AiPlans.sortOrder] = 0
                it[AiPlans.createdAt] = Instant.now()
                it[AiPlans.updatedAt] = Instant.now()
            }
        }
        block(services)
    }

    private fun seedUser(services: ApplicationServices) =
        transaction {
            services.accounts.createUser(
                serverUuid = "server-alice",
                gameId = "Alice",
                qq = "10001",
                passwordHash = services.passwordHasher.hash(TestPassword)
            )
        }

    private fun testConfig(providerBaseUrl: String, aiConfig: AiConfig = testAiConfig(providerBaseUrl)): AppConfig =
        AppConfig(
            publicServer = ServerConfig("127.0.0.1", 28657),
            bridgeServer = ServerConfig("127.0.0.1", 28658),
            database = DatabaseConfig("jdbc:h2:mem:test", "test", "test", 4),
            security = SecurityConfig("session-pepper", "verification-pepper", 30),
            pluginBridge = PluginBridgeConfig("bridge-token", 10000, 30000, 90000),
            chat = ChatConfig(30, 15000, 35000),
            ai = aiConfig,
            oneBot = OneBotConfig("", "", "", "^(/ai|!ai)\\s+(.+)$"),
            admin = AdminConfig("admin-token"),
            oidc = OidcConfig(false, "http://127.0.0.1:28657", "wikijs", "", "http://127.0.0.1/callback", "config/oidc.json", true, 5, 10, 10, 12),
            app = AppVersionConfig(5, "1.0.3"),
            logLevel = "INFO"
        )

    private fun testAiConfig(
        providerBaseUrl: String,
        requestTimeoutMillis: Long = 120_000,
        firstTokenTimeoutMillis: Long = 60_000,
        chunkIdleTimeoutMillis: Long = 45_000,
    ): AiConfig =
        AiConfig(
            enabled = true,
            providerApiKey = "provider-key",
            providerBaseUrl = providerBaseUrl,
            defaultModel = "deepseek-v4-flash",
            requestTimeoutMillis = requestTimeoutMillis,
            connectTimeoutMillis = 12_000,
            firstTokenTimeoutMillis = firstTokenTimeoutMillis,
            chunkIdleTimeoutMillis = chunkIdleTimeoutMillis,
            streamHeartbeatMillis = 8_000,
            knowledgeToolTimeoutMillis = 8_000,
            knowledgeMaxToolCalls = 1,
            knowledgeMaxChunks = 5,
            quotaWindowHours = 5,
            maxInputChars = 2_000,
            maxContextMessages = 10
        )

    private fun assertExchangeNotProcessing(clientMessageId: String) {
        val status = transaction {
            AiRequestExchanges.selectAll()
                .where { AiRequestExchanges.clientMessageId eq clientMessageId }
                .single()[AiRequestExchanges.status]
        }
        assertFalse(status == "pending" || status == "streaming")
    }

    private companion object {
        const val TestPassword = "correct horse battery staple"
    }
}
