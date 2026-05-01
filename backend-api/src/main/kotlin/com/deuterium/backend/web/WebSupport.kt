package com.deuterium.backend.web

import com.deuterium.backend.model.ApiErrorBody
import com.deuterium.backend.model.ApiErrorResponse
import com.deuterium.backend.model.ApiSuccess
import com.deuterium.backend.model.CurrentUser
import com.deuterium.backend.model.Page
import com.deuterium.backend.util.Ids
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import io.ktor.util.AttributeKey

val RequestIdKey = AttributeKey<String>("deuterium.requestId")
val CurrentUserKey = AttributeKey<CurrentUser>("deuterium.currentUser")

fun ApplicationCall.requestId(): String =
    attributes.getOrNull(RequestIdKey)
        ?: request.headers["X-Request-Id"]?.takeIf { it.isNotBlank() }
        ?: Ids.requestId()

fun ApplicationCall.currentUser(): CurrentUser =
    attributes.getOrNull(CurrentUserKey)
        ?: throw ApiException("UNAUTHORIZED", "请先登录。", 401)

suspend inline fun <reified T> ApplicationCall.ok(data: T, page: Page? = null, status: HttpStatusCode = HttpStatusCode.OK) {
    respond(status, ApiSuccess(requestId = requestId(), data = data, page = page))
}

suspend fun ApplicationCall.fail(exception: ApiException) {
    respond(
        HttpStatusCode.fromValue(exception.status),
        ApiErrorResponse(
            requestId = requestId(),
            error = ApiErrorBody(
                code = exception.code,
                message = exception.message,
                details = exception.details,
                retryAfterSeconds = exception.retryAfterSeconds
            )
        )
    )
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

fun ApplicationCall.bearerToken(): String? {
    val header = request.headers[HttpHeaders.Authorization] ?: return null
    return header.removePrefix("Bearer").trim().takeIf { it.isNotBlank() }
}

