package com.deuterium.backend

import com.deuterium.backend.config.AppConfig
import com.deuterium.backend.db.DatabaseFactory
import com.deuterium.backend.model.ApiErrorBody
import com.deuterium.backend.model.ApiErrorResponse
import com.deuterium.backend.routes.installBridgeRoutes
import com.deuterium.backend.routes.installRoutes
import com.deuterium.backend.routes.installPublicRoutes
import com.deuterium.backend.util.Ids
import com.deuterium.backend.web.ApiException
import com.deuterium.backend.web.RequestIdKey
import com.deuterium.backend.web.fail
import com.deuterium.backend.web.requestId
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

fun main() {
    try {
        runBackend()
    } catch (cause: Throwable) {
        System.err.println("DeuteriumAPP backend startup failed: ${cause::class.java.name}: ${cause.message}")
        var next = cause.cause
        while (next != null) {
            System.err.println("Caused by: ${next::class.java.name}: ${next.message}")
            next = next.cause
        }
        cause.printStackTrace()
        exitProcess(1)
    }
}

private fun runBackend() {
    val config = AppConfig.load()
    System.setProperty("LOG_LEVEL", config.logLevel)
    DatabaseFactory.migrate(config.database)
    if (System.getenv("DEUTERIUM_RESET_DATABASE") == "true") {
        DatabaseFactory.resetData(config.database)
        println("Database reset complete.")
        return
    }
    if (System.getenv("DEUTERIUM_MIGRATE_ONLY") == "true") {
        println("Database migration complete.")
        return
    }
    val dataSource = DatabaseFactory.connect(config.database)
    val services = ApplicationServices.create(config)
    Runtime.getRuntime().addShutdownHook(Thread { dataSource.close() })

    val publicServer = embeddedServer(
        Netty,
        host = config.publicServer.host,
        port = config.publicServer.port,
        module = { publicModule(services) }
    )
    val bridgeServer = embeddedServer(
        Netty,
        host = config.bridgeServer.host,
        port = config.bridgeServer.port,
        module = { bridgeModule(services) }
    )

    publicServer.start(wait = false)
    bridgeServer.start(wait = true)
}

fun Application.module(config: AppConfig = AppConfig.load()) {
    DatabaseFactory.connect(config.database)
    val services = ApplicationServices.create(config)
    configureCommon(services)
    routing {
        installRoutes(services)
    }
}

fun Application.publicModule(services: ApplicationServices) {
    configureCommon(services)
    routing {
        installPublicRoutes(services)
    }
}

fun Application.bridgeModule(services: ApplicationServices) {
    configureCommon(services)
    routing {
        installBridgeRoutes(services)
    }
    environment.monitor.subscribe(ApplicationStarted) {
        launch {
            while (true) {
                delay(services.config.pluginBridge.heartbeatIntervalMillis)
                services.bridge.sendPing()
            }
        }
    }
}

private fun Application.configureCommon(services: ApplicationServices) {
    val logger = LoggerFactory.getLogger("DeuteriumBackend")
    val json = services.json
    install(ContentNegotiation) {
        json(json)
    }
    install(WebSockets) {
        pingPeriodMillis = services.config.chat.websocketPingIntervalMillis
        timeoutMillis = services.config.chat.websocketTimeoutMillis
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    install(CallLogging) {
        filter { call -> !call.request.path().startsWith("/health") }
        format { call -> "${call.request.httpMethod.value} ${call.request.path()} ${call.response.status()}" }
    }
    intercept(io.ktor.server.application.ApplicationCallPipeline.Setup) {
        call.attributes.put(RequestIdKey, call.request.headers["X-Request-Id"]?.takeIf { it.isNotBlank() } ?: Ids.requestId())
    }
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.fail(cause)
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled request failure requestId={}", call.requestId(), cause)
            call.respond(
                io.ktor.http.HttpStatusCode.InternalServerError,
                ApiErrorResponse(
                    requestId = call.requestId(),
                    error = ApiErrorBody("SERVER_UNAVAILABLE", "服务暂不可用，请稍后再试。")
                )
            )
        }
    }
}

