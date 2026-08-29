package com.deuterium.backend.oidc

import com.deuterium.backend.ApplicationServices
import com.deuterium.backend.web.dbQuery
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.net.URLDecoder
import java.util.Base64

private const val OidcSessionCookie = "deuterium_oidc_session"

fun Routing.installOidcRoutes(services: ApplicationServices) {
    val oidc = services.oidc ?: return

    get("/.well-known/openid-configuration") {
        call.noStore()
        call.respond(oidc.discovery())
    }

    get("/.well-known/jwks.json") {
        call.noStore()
        call.respond(oidc.jwks())
    }

    get("/oauth/authorize") {
        val request = try {
            dbQuery {
                oidc.validateAuthorizationRequest(
                    responseType = call.request.queryParameters["response_type"],
                    clientId = call.request.queryParameters["client_id"],
                    redirectUri = call.request.queryParameters["redirect_uri"],
                    scope = call.request.queryParameters["scope"],
                    state = call.request.queryParameters["state"],
                    nonce = call.request.queryParameters["nonce"]
                )
            }
        } catch (e: OidcRedirectException) {
            call.respondRedirect(e.request.redirectWithError(e.error, e.message))
            return@get
        } catch (e: OidcDirectException) {
            call.respond(HttpStatusCode.BadRequest, OAuthErrorResponse(e.error, e.message))
            return@get
        }

        val currentUser = dbQuery { oidc.authenticateWebSession(call.cookieValue(OidcSessionCookie)) }
        if (currentUser != null) {
            val code = dbQuery { oidc.issueAuthorizationCode(request, currentUser.userId) }
            call.respondRedirect(request.redirectWithCode(code))
            return@get
        }

        call.noStore()
        call.respondText(loginPage(request), ContentType.Text.Html)
    }

    post("/oauth/login") {
        val form = call.receiveParameters()
        val request = try {
            dbQuery {
                oidc.validateAuthorizationRequest(
                    responseType = form["response_type"],
                    clientId = form["client_id"],
                    redirectUri = form["redirect_uri"],
                    scope = form["scope"],
                    state = form["state"],
                    nonce = form["nonce"]
                )
            }
        } catch (e: OidcRedirectException) {
            call.respondRedirect(e.request.redirectWithError(e.error, e.message))
            return@post
        } catch (e: OidcDirectException) {
            call.respond(HttpStatusCode.BadRequest, OAuthErrorResponse(e.error, e.message))
            return@post
        }

        val account = form["account"].orEmpty()
        val password = form["password"].orEmpty()
        val result = try {
            dbQuery {
                val user = oidc.login(account, password, call.request.local.remoteHost)
                val session = oidc.createWebSession(user)
                OidcLoginResult(
                    session = session,
                    code = oidc.issueAuthorizationCode(request, user.userId)
                )
            }
        } catch (e: OidcLoginException) {
            call.noStore()
            call.respondText(loginPage(request, e.message), ContentType.Text.Html, HttpStatusCode.Unauthorized)
            return@post
        }

        call.response.cookies.append(oidcSessionCookie(services, result.session.token))
        call.respondRedirect(request.redirectWithCode(result.code))
    }

    post("/oauth/token") {
        val form = call.receiveParameters()
        if (form["grant_type"] != "authorization_code") {
            call.respondTokenError(HttpStatusCode.BadRequest, "unsupported_grant_type", "Only authorization_code is supported.")
            return@post
        }
        val credentials = call.clientCredentials(form)
        if (credentials == null) {
            call.respondTokenError(HttpStatusCode.Unauthorized, "invalid_client", "Invalid client credentials.")
            return@post
        }
        val code = form["code"].orEmpty()
        val redirectUri = form["redirect_uri"].orEmpty()
        try {
            val response = dbQuery {
                oidc.exchangeAuthorizationCode(
                    clientId = credentials.clientId,
                    clientSecret = credentials.clientSecret,
                    code = code,
                    redirectUri = redirectUri
                )
            }
            call.noStore()
            call.respond(response)
        } catch (e: OidcTokenException) {
            val status = if (e.error == "invalid_client") HttpStatusCode.Unauthorized else HttpStatusCode.BadRequest
            call.respondTokenError(status, e.error, e.message)
        }
    }

    get("/oauth/userinfo") {
        val token = call.request.headers[HttpHeaders.Authorization]
            ?.removePrefix("Bearer")
            ?.trim()
            .orEmpty()
        if (token.isBlank()) {
            call.respondBearerError()
            return@get
        }
        try {
            val profile = dbQuery { oidc.userInfo(token) }
            call.noStore()
            call.respond(profile)
        } catch (e: OidcTokenException) {
            call.respondBearerError()
        }
    }

    get("/oauth/logout") {
        dbQuery { oidc.revokeWebSession(call.cookieValue(OidcSessionCookie)) }
        call.response.cookies.append(oidcSessionCookie(services, "", maxAge = 0))
        call.respondText(logoutPage(), ContentType.Text.Html)
    }
}

private data class OidcClientCredentials(val clientId: String, val clientSecret: String)

private data class OidcLoginResult(val session: OidcBrowserSession, val code: String)

private fun ApplicationCall.cookieValue(name: String): String? =
    request.headers[HttpHeaders.Cookie]
        ?.split(';')
        ?.mapNotNull { cookie ->
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0] == name) URLDecoder.decode(parts[1], Charsets.UTF_8) else null
        }
        ?.firstOrNull()

private fun ApplicationCall.clientCredentials(form: Parameters): OidcClientCredentials? {
    val basic = request.headers[HttpHeaders.Authorization]
        ?.takeIf { it.startsWith("Basic ", ignoreCase = true) }
        ?.substringAfter(' ', missingDelimiterValue = "")
        ?.trim()
        ?.let { encoded ->
            runCatching {
                val decoded = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
                val parts = decoded.split(":", limit = 2)
                if (parts.size == 2) {
                    URLDecoder.decode(parts[0], Charsets.UTF_8) to URLDecoder.decode(parts[1], Charsets.UTF_8)
                } else {
                    null
                }
            }.getOrNull()
        }
    if (basic != null) return OidcClientCredentials(basic.first, basic.second)
    val clientId = form["client_id"]?.takeIf { it.isNotBlank() } ?: return null
    val clientSecret = form["client_secret"]?.takeIf { it.isNotBlank() } ?: return null
    return OidcClientCredentials(clientId, clientSecret)
}

private fun oidcSessionCookie(services: ApplicationServices, value: String, maxAge: Int = -1): Cookie =
    Cookie(
        name = OidcSessionCookie,
        value = value,
        encoding = CookieEncoding.URI_ENCODING,
        maxAge = maxAge,
        path = "/",
        secure = services.config.oidc.issuer.startsWith("https://"),
        httpOnly = true,
        extensions = mapOf("SameSite" to "Lax")
    )

private suspend fun ApplicationCall.respondTokenError(status: HttpStatusCode, error: String, description: String) {
    noStore()
    if (status == HttpStatusCode.Unauthorized) {
        response.header(HttpHeaders.WWWAuthenticate, """Basic realm="deuterium-oidc"""")
    }
    respond(status, OAuthErrorResponse(error, description))
}

private suspend fun ApplicationCall.respondBearerError() {
    noStore()
    response.header(HttpHeaders.WWWAuthenticate, """Bearer error="invalid_token"""")
    respond(HttpStatusCode.Unauthorized, OAuthErrorResponse("invalid_token", "Access token is invalid or expired."))
}

private fun ApplicationCall.noStore() {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header(HttpHeaders.Pragma, "no-cache")
}

private fun OidcAuthorizationRequest.redirectWithCode(code: String): String =
    redirectUrl {
        parameters.append("code", code)
        state?.let { parameters.append("state", it) }
    }

private fun OidcAuthorizationRequest.redirectWithError(error: String, description: String): String =
    redirectUrl {
        parameters.append("error", error)
        parameters.append("error_description", description)
        state?.let { parameters.append("state", it) }
    }

private fun OidcAuthorizationRequest.redirectUrl(block: URLBuilder.() -> Unit): String {
    val builder = URLBuilder(redirectUri)
    builder.block()
    return builder.buildString()
}

private fun loginPage(request: OidcAuthorizationRequest, error: String? = null): String =
    """
    <!doctype html>
    <html lang="zh-CN">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>Deuterium 账号登录</title>
      <style>
        :root { color-scheme: light; font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
        body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #f5f7f9; color: #1d2730; }
        main { width: min(100% - 32px, 360px); }
        h1 { font-size: 24px; margin: 0 0 20px; }
        form { display: grid; gap: 14px; padding: 24px; background: white; border: 1px solid #dde3ea; border-radius: 8px; box-shadow: 0 10px 30px rgba(20, 34, 48, .08); }
        label { display: grid; gap: 6px; font-size: 14px; color: #40505f; }
        input { height: 42px; border: 1px solid #b8c4d0; border-radius: 6px; padding: 0 12px; font-size: 16px; }
        button { height: 44px; border: 0; border-radius: 6px; background: #136f63; color: white; font-size: 16px; font-weight: 650; cursor: pointer; }
        .error { margin: 0; color: #b42318; font-size: 14px; }
      </style>
    </head>
    <body>
      <main>
        <form method="post" action="/oauth/login" autocomplete="on">
          <h1>Deuterium 账号登录</h1>
          ${error?.let { """<p class="error">${it.html()}</p>""" }.orEmpty()}
          ${hidden("response_type", "code")}
          ${hidden("client_id", request.clientId)}
          ${hidden("redirect_uri", request.redirectUri)}
          ${hidden("scope", request.scope)}
          ${request.state?.let { hidden("state", it) }.orEmpty()}
          ${request.nonce?.let { hidden("nonce", it) }.orEmpty()}
          <label>账号<input name="account" autocomplete="username" required autofocus></label>
          <label>密码<input name="password" type="password" autocomplete="current-password" required></label>
          <button type="submit">登录 Wiki</button>
        </form>
      </main>
    </body>
    </html>
    """.trimIndent()

private fun logoutPage(): String =
    """
    <!doctype html>
    <html lang="zh-CN">
    <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>已退出</title></head>
    <body><p>已退出 Deuterium Wiki 登录。</p></body>
    </html>
    """.trimIndent()

private fun hidden(name: String, value: String): String =
    """<input type="hidden" name="${name.html()}" value="${value.html()}">"""

private fun String.html(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
