import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}
