import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.sessions.*
import ru.tickets.security.*
import kotlin.test.*

class AdminAuthTest {
    private val hash = BCrypt.withDefaults().hashToString(4, "test-password".toCharArray())
    private fun config() = MapApplicationConfig().apply {
        put("admin-auth.username", "admin")
        put("admin-auth.password-hash", hash)
        put("admin-auth.session-secret", "ab".repeat(32))
        put("admin-auth.origin", "https://tix.lobanovsky.ru")
        put("admin-auth.cookie-secure", "true")
        listOf("ramt", "nations", "vakhtangov", "fomenki", "lensov", "mxt", "satirikon").forEach {
            put("api-keys.$it", "$it-key")
        }
    }
    private fun ApplicationTestBuilder.setup() {
        environment { config = config() }
        application {
            install(ContentNegotiation) { json() }
            configureSecurity()
            routing {
                route("/api") {
                    adminAuthRoutes()
                    authenticate("bot-key", "admin-session") {
                        get("/shared") { call.respondText("ok") }
                        post("/write") { call.respondText("ok") }
                        get("/admin-only") {
                            if (call.principal<BotPrincipal>()!!.isAdmin) call.respondText("ok")
                            else call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                }
            }
        }
    }
    @Test fun loginSessionLogoutAndBots() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/session").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin-only") { bearerAuth("admin-secret") }.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/shared") { bearerAuth("ramt-key") }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin-only") { bearerAuth("ramt-key") }.status)
        assertEquals(HttpStatusCode.OK, client.post("/api/write") { bearerAuth("ramt-key") }.status)
        val login = client.post("/api/admin/login") {
            header(HttpHeaders.Origin, "https://tix.lobanovsky.ru")
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"test-password"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val cookie = login.headers[HttpHeaders.SetCookie]!!
        assertTrue(cookie.contains("HttpOnly", true))
        assertTrue(cookie.contains("Secure", true))
        assertTrue(cookie.contains("SameSite=Lax", true))
        assertFalse(cookie.contains("Domain=", true))
        val value = cookie.substringBefore(';')
        assertEquals(HttpStatusCode.OK, client.get("/api/admin/session") { header(HttpHeaders.Cookie, value) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/admin-only") { header(HttpHeaders.Cookie, value) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/write") { header(HttpHeaders.Cookie, value) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/write") {
            header(HttpHeaders.Cookie, value); header(HttpHeaders.Origin, "https://evil.example")
        }.status)
        assertEquals(HttpStatusCode.OK, client.post("/api/write") {
            header(HttpHeaders.Cookie, value); header(HttpHeaders.Origin, "https://tix.lobanovsky.ru")
        }.status)
        val logout = client.post("/api/admin/logout") {
            header(HttpHeaders.Origin, "https://tix.lobanovsky.ru"); header(HttpHeaders.Cookie, value)
        }
        assertEquals(HttpStatusCode.OK, logout.status)
        assertTrue(logout.headers[HttpHeaders.SetCookie]!!.contains("Max-Age=0", true))
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/session").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/session") { header(HttpHeaders.Cookie, value + "corrupt") }.status)
    }
    @Test fun rejectsInvalidLoginsAndOrigins() = testApplication {
        setup()
        for (body in listOf("""{"username":"admin","password":"wrong"}""", """{"username":"other","password":"test-password"}""")) {
            assertEquals(HttpStatusCode.Unauthorized, client.post("/api/admin/login") {
                header(HttpHeaders.Origin, "https://tix.lobanovsky.ru")
                contentType(ContentType.Application.Json); setBody(body)
            }.status)
        }
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/admin/login").status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/admin/logout").status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/admin/login") {
            header(HttpHeaders.Origin, "https://tix.lobanovsky.ru")
            contentType(ContentType.Application.Json); setBody("invalid")
        }.status)
    }
    @Test fun rejectsExpiredAndWrongUserSessions() = testApplication {
        setup()
        // Test-only routes issue signed fixtures using the actual configured transport.
        application {
            routing {
                get("/fixture/expired") { call.sessions.set(AdminSession("admin", 1)); call.respondText("ok") }
                get("/fixture/user") { call.sessions.set(AdminSession("other", Long.MAX_VALUE)); call.respondText("ok") }
            }
        }
        for (path in listOf("expired", "user")) {
            val cookie = client.get("/fixture/$path").headers[HttpHeaders.SetCookie]!!.substringBefore(';')
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/session") { header(HttpHeaders.Cookie, cookie) }.status)
        }
    }
    @Test fun validatesConfiguration() {
        assertFails { AdminSettings.from(MapApplicationConfig()) }
        assertFails { AdminSettings.from(config().apply { put("admin-auth.password-hash", "invalid") }) }
        assertFails { AdminSettings.from(config().apply { put("admin-auth.session-secret", "short") }) }
        assertFails { AdminSettings.from(config().apply { put("admin-auth.origin", "http://localhost:3000") }) }
        assertFalse(AdminSettings.from(config().apply {
            put("admin-auth.origin", "http://localhost:3000"); put("admin-auth.cookie-secure", "false")
        }).secure)
        // Go emits $2a$; explicitly verify all supported bcrypt prefixes.
        for (prefix in listOf("2a", "2b", "2y")) {
            val compatible = "$" + prefix + hash.substring(3)
            assertTrue(BCrypt.verifyer().verify("test-password".toCharArray(), compatible).verified)
        }
    }
}
