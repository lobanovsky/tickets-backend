package ru.tickets.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import ru.tickets.domain.PerformanceService
import ru.tickets.models.ErrorResponse
import ru.tickets.security.BotPrincipal

private val logger = LoggerFactory.getLogger("PerformanceRoutes")

fun Route.performanceRoutes(performanceService: PerformanceService) {
    authenticate("bot-key", "admin-session") {
        get("/theatres/{slug}/performances") {
            val principal = call.principal<BotPrincipal>()!!
            val slug = call.parameters["slug"]!!
            if (!principal.isAdmin && principal.slug != slug) {
                return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", null))
            }
            val hasTelegramIdParameter = call.request.queryParameters.contains("telegramId")
            val telegramId = call.request.queryParameters["telegramId"]?.toLongOrNull()
            val startedAt = System.nanoTime()
            logger.info(
                "performances request started: theatre=$slug, hasTelegramIdParameter=$hasTelegramIdParameter, hasValidTelegramId=${telegramId != null}"
            )
            try {
                val performances = performanceService.findByTheatreWithSubscriptionStatus(slug, telegramId)
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                logger.info(
                    "performances data loaded: theatre=$slug, count=${performances.size}, durationMs=$elapsedMs"
                )
                call.respond(performances)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                logger.error(
                    "performances request failed: theatre=$slug, durationMs=$elapsedMs",
                    e
                )
                throw e
            }
        }
    }
}
