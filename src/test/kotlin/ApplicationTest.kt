import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import ru.tickets.configureSerialization
import ru.tickets.db.configureDatabases
import ru.tickets.db.schema.*
import ru.tickets.routes.configureRouting
import ru.tickets.security.configureSecurity
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testRootAndProtectedRoutes() = testApplication {
        val testConfig = MapApplicationConfig().apply {
            put("postgres.url", "jdbc:postgresql://localhost:5455/tickets")
            put("postgres.user", "tickets")
            put("postgres.password", "tickets")
            put("admin-auth.username", "admin")
            put("admin-auth.password-hash", BCrypt.withDefaults().hashToString(4, "test-password".toCharArray()))
            put("admin-auth.session-secret", "ab".repeat(32))
            put("admin-auth.origin", "https://tix.lobanovsky.ru")
            listOf("ramt", "nations", "vakhtangov", "fomenki", "lensov", "mxt", "satirikon").forEach {
                put("api-keys.$it", "$it-key")
            }
        }
        environment { config = testConfig }
        application {
            // The integration fixture owns a local test database; never start scrapers or notifications here.
            val db = Database.connect("jdbc:postgresql://localhost:5455/tickets", "org.postgresql.Driver", "tickets", "tickets")
            transaction(db) {
                SchemaUtils.create(Theatres, Performances, Users, Subscriptions, PendingNotifications, PaidSubscriptions, UserBotLinks)
            }
            configureSerialization()
            configureDatabases()
            configureSecurity()
            configureRouting()
        }
        assertEquals(HttpStatusCode.OK, client.get("/").status)
        for (path in listOf("/api/admin/users", "/api/admin/stats", "/api/admin/theatres/ramt/subscriptions", "/api/users/1/subscriptions", "/api/users/1/paid-subscription")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status, path)
            assertEquals(HttpStatusCode.Unauthorized, client.get(path) { bearerAuth("admin-secret") }.status, path)
        }
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/users") { bearerAuth("ramt-key") }.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/admin/theatres/ramt/subscriptions") { bearerAuth("ramt-key") }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/theatres/nations/subscriptions") { bearerAuth("ramt-key") }.status)
    }
}
