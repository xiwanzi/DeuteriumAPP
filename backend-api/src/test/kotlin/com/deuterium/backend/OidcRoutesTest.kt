package com.deuterium.backend

import com.deuterium.backend.config.AppConfig
import com.deuterium.backend.config.AppVersionConfig
import com.deuterium.backend.config.AdminConfig
import com.deuterium.backend.config.AiConfig
import com.deuterium.backend.config.ChatConfig
import com.deuterium.backend.config.DatabaseConfig
import com.deuterium.backend.config.OneBotConfig
import com.deuterium.backend.config.OidcConfig
import com.deuterium.backend.config.PluginBridgeConfig
import com.deuterium.backend.config.SecurityConfig
import com.deuterium.backend.config.ServerConfig
import com.deuterium.backend.db.AppPresence
import com.deuterium.backend.db.ChatMessages
import com.deuterium.backend.db.LoginFailures
import com.deuterium.backend.db.OidcAccessTokens
import com.deuterium.backend.db.OidcAuthorizationCodes
import com.deuterium.backend.db.OidcClients
import com.deuterium.backend.db.OidcWebSessions
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
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.parallel.ResourceLock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ResourceLock("exposed-default-database")
class OidcRoutesTest {
    @Test
    fun `well known configuration advertises configured issuer and endpoints`() = withOidcApp { services ->
        testApplication {
            application { publicModule(services) }

            val response = client.get("/.well-known/openid-configuration")

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(TestIssuer, json["issuer"]?.jsonPrimitive?.content)
            assertEquals("$TestIssuer/oauth/authorize", json["authorization_endpoint"]?.jsonPrimitive?.content)
            assertEquals("$TestIssuer/oauth/token", json["token_endpoint"]?.jsonPrimitive?.content)
            assertEquals("$TestIssuer/.well-known/jwks.json", json["jwks_uri"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `token endpoint accepts wiki js form encoded authorization code exchange`() = withOidcApp { services ->
        seedUser(services)
        testApplication {
            application { publicModule(services) }
            val noRedirectClient = createClient { followRedirects = false }

            val authorize = noRedirectClient.post("/oauth/login") {
                setBody(authorizeLoginForm())
            }
            assertEquals(HttpStatusCode.Found, authorize.status)
            val code = Url(assertNotNull(authorize.headers[HttpHeaders.Location])).parameters["code"]
            assertNotNull(code)

            val token = noRedirectClient.post("/oauth/token") {
                header(HttpHeaders.Authorization, basicAuth(TestClientId, TestClientSecret))
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("grant_type", "authorization_code")
                            append("code", code)
                            append("redirect_uri", TestRedirectUri)
                        }
                    )
                )
            }

            assertEquals(HttpStatusCode.OK, token.status)
            val json = Json.parseToJsonElement(token.bodyAsText()).jsonObject
            assertEquals("Bearer", json["token_type"]?.jsonPrimitive?.content)
            assertTrue(json["access_token"]?.jsonPrimitive?.content.orEmpty().startsWith("oidc_access_"))
            assertTrue(json["id_token"]?.jsonPrimitive?.content.orEmpty().split('.').size == 3)
        }
    }

    @Test
    fun `authorize rejects unregistered redirect uri`() = withOidcApp { services ->
        testApplication {
            application { publicModule(services) }

            val response = client.get(
                "/oauth/authorize?response_type=code&client_id=$TestClientId&redirect_uri=${TestRedirectUri}/extra&scope=openid"
            )

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("invalid_request"))
        }
    }

    @Test
    fun `token endpoint rejects reused authorization code`() = withOidcApp { services ->
        seedUser(services)
        testApplication {
            application { publicModule(services) }
            val noRedirectClient = createClient { followRedirects = false }
            val authorize = noRedirectClient.post("/oauth/login") {
                setBody(authorizeLoginForm())
            }
            val code = Url(assertNotNull(authorize.headers[HttpHeaders.Location])).parameters["code"]
            assertNotNull(code)

            val first = noRedirectClient.post("/oauth/token") {
                header(HttpHeaders.Authorization, basicAuth(TestClientId, TestClientSecret))
                setBody(tokenForm(code))
            }
            assertEquals(HttpStatusCode.OK, first.status)

            val second = noRedirectClient.post("/oauth/token") {
                header(HttpHeaders.Authorization, basicAuth(TestClientId, TestClientSecret))
                setBody(tokenForm(code))
            }
            assertEquals(HttpStatusCode.BadRequest, second.status)
            assertTrue(second.bodyAsText().contains("invalid_grant"))
        }
    }

    @Test
    fun `app account me rejects oidc access token`() = withOidcApp { services ->
        seedUser(services)
        testApplication {
            application { publicModule(services) }
            val noRedirectClient = createClient { followRedirects = false }
            val authorize = noRedirectClient.post("/oauth/login") {
                setBody(authorizeLoginForm())
            }
            val code = Url(assertNotNull(authorize.headers[HttpHeaders.Location])).parameters["code"]
            assertNotNull(code)
            val token = noRedirectClient.post("/oauth/token") {
                header(HttpHeaders.Authorization, basicAuth(TestClientId, TestClientSecret))
                setBody(tokenForm(code))
            }
            val accessToken = Json.parseToJsonElement(token.bodyAsText())
                .jsonObject["access_token"]!!
                .jsonPrimitive
                .content

            val appMe = noRedirectClient.get("/api/v1/account/me") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }

            assertEquals(HttpStatusCode.Unauthorized, appMe.status)
        }
    }

    private fun withOidcApp(block: (ApplicationServices) -> Unit) {
        val database = Database.connect(
            url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction(database) {
            SchemaUtils.create(
                Users,
                Sessions,
                OidcClients,
                OidcAuthorizationCodes,
                OidcAccessTokens,
                OidcWebSessions,
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
                PlayerFollows
            )
        }
        val signingKey = Files.createTempDirectory("deuterium-oidc-test").resolve("oidc-signing-key.json")
        val services = ApplicationServices.create(testConfig(signingKey.toString()))
        block(services)
    }

    private fun seedUser(services: ApplicationServices) {
        transaction {
            services.accounts.createUser(
                serverUuid = "server-alice",
                gameId = "Alice",
                qq = "10001",
                passwordHash = services.passwordHasher.hash(TestPassword)
            )
        }
    }

    private fun authorizeLoginForm(): FormDataContent =
        FormDataContent(
            Parameters.build {
                append("response_type", "code")
                append("client_id", TestClientId)
                append("redirect_uri", TestRedirectUri)
                append("scope", "openid profile email")
                append("state", "state-1")
                append("nonce", "nonce-1")
                append("account", "Alice")
                append("password", TestPassword)
            }
        )

    private fun tokenForm(code: String): FormDataContent =
        FormDataContent(
            Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", TestRedirectUri)
            }
        )

    private fun basicAuth(clientId: String, clientSecret: String): String {
        val encoded = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }

    private fun testConfig(signingKeyPath: String): AppConfig =
        AppConfig(
            publicServer = ServerConfig("127.0.0.1", 28657),
            bridgeServer = ServerConfig("127.0.0.1", 28658),
            database = DatabaseConfig("jdbc:h2:mem:test", "test", "test", 4),
            security = SecurityConfig("session-pepper", "verification-pepper", 30),
            pluginBridge = PluginBridgeConfig("bridge-token", 10000, 30000, 90000),
            chat = ChatConfig(30, 15000, 35000),
            ai = AiConfig(false, "", "https://api.deepseek.com", "deepseek-v4-flash", 120000, 12000, 60000, 45000, 8000, 8000, 1, 5, 5, 2000, 10),
            oneBot = OneBotConfig("", "", "", "^(/ai|!ai)\\s+(.+)$"),
            admin = AdminConfig(""),
            oidc = OidcConfig(
                enabled = true,
                issuer = TestIssuer,
                clientId = TestClientId,
                clientSecretHash = Secrets.sha256Raw(TestClientSecret),
                redirectUri = TestRedirectUri,
                signingKeyPath = signingKeyPath,
                allowInsecureHttp = false,
                authorizationCodeMinutes = 5,
                accessTokenMinutes = 10,
                idTokenMinutes = 10,
                webSessionHours = 12
            ),
            app = AppVersionConfig(5, "1.0.3"),
            logLevel = "INFO"
        )

    private companion object {
        const val TestIssuer = "http://127.0.0.1:28657"
        const val TestClientId = "wikijs"
        const val TestClientSecret = "wiki-secret"
        const val TestRedirectUri = "http://127.0.0.1/wiki-callback"
        const val TestPassword = "correct horse battery staple"
    }
}
