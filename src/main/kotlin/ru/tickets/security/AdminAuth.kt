package ru.tickets.security

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import java.net.URI
import java.security.MessageDigest
import java.time.Instant

private const val COOKIE = "tickets_session"
private const val TTL = 12 * 60 * 60L
private val AdminKey = AttributeKey<AdminSettings>("admin-settings")

@Serializable
data class AdminSession(val username: String, val expiresAt: Long)

@Serializable
private data class LoginRequest(val username: String, val password: String)

class AdminSettings(
    val username: String,
    val passwordHash: String,
    val secret: ByteArray,
    val origin: String,
    val secure: Boolean
) {
    companion object {
        fun from(config: ApplicationConfig): AdminSettings {
            fun required(name: String) = config.propertyOrNull("admin-auth.$name")?.getString()
                ?.takeIf { it.isNotBlank() } ?: error("Missing admin-auth.$name")
            val username = required("username")
            val hash = required("password-hash")
            require(BCrypt.verifyer().verify("validation-probe".toCharArray(), hash).validFormat) {
                "Invalid admin password bcrypt hash"
            }
            val secret = required("session-secret")
            require(secret.matches(Regex("[0-9a-fA-F]{64}"))) { "Admin session secret must be 32 bytes encoded as hex" }
            val origin = required("origin")
            val uri = URI(origin)
            val secure = config.propertyOrNull("admin-auth.cookie-secure")?.getString()?.toBooleanStrict() ?: true
            require(uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.isNullOrEmpty()) {
                "Admin origin must contain only scheme and host (and optional port)"
            }
            require(uri.scheme == "https" || (!secure && uri.scheme == "http")) { "Admin origin requires HTTPS unless cookie-secure is explicitly false" }
            return AdminSettings(username, hash, secret.hexToByteArray(), origin, secure)
        }
    }
}

fun Application.configureAdminSessions(): AdminSettings {
    val settings = AdminSettings.from(environment.config)
    attributes.put(AdminKey, settings)
    install(Sessions) {
        cookie<AdminSession>(COOKIE) {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = settings.secure
            cookie.maxAgeInSeconds = TTL
            cookie.extensions["SameSite"] = "Lax"
            transform(SessionTransportTransformerMessageAuthentication(settings.secret))
        }
    }
    install(createApplicationPlugin("AdminOriginGuard") {
        onCall { call ->
            if (call.request.path().startsWith("/api/")) {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                val mutating = call.request.httpMethod !in listOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)
                val loginOrLogout = call.request.path() in listOf("/api/admin/login", "/api/admin/logout")
                if (mutating && (loginOrLogout || call.request.cookies[COOKIE] != null) &&
                    call.request.header(HttpHeaders.Origin) != settings.origin) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "forbidden origin"))
                }
            }
        }
    })
    return settings
}

fun AuthenticationConfig.configureAdminAuthentication(settings: AdminSettings) {
    session<AdminSession>("admin-session") {
        validate { session ->
            if (session.username == settings.username && session.expiresAt > Instant.now().epochSecond) {
                BotPrincipal("admin", true)
            } else null
        }
        challenge { call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized")) }
    }
}

fun Route.adminAuthRoutes() {
    post("/admin/login") {
        val settings = call.application.attributes[AdminKey]
        val req = try { call.receive<LoginRequest>() } catch (_: Exception) {
            return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid request"))
        }
        // Always perform bcrypt, including for an unknown username.
        val passwordMatches = req.password.toByteArray().size <= 72 &&
            BCrypt.verifyer().verify(req.password.toCharArray(), settings.passwordHash).verified
        val usernameMatches = MessageDigest.isEqual(req.username.toByteArray(), settings.username.toByteArray())
        if (!passwordMatches || !usernameMatches) {
            return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid credentials"))
        }
        call.sessions.set(AdminSession(settings.username, Instant.now().epochSecond + TTL))
        call.respond(mapOf("ok" to true))
    }
    post("/admin/logout") {
        call.sessions.clear<AdminSession>()
        call.respond(mapOf("ok" to true))
    }
    authenticate("admin-session") {
        get("/admin/session") {
            call.respond(mapOf("username" to call.application.attributes[AdminKey].username))
        }
    }
}
