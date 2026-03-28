package ru.tickets.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.tickets.domain.PerformanceService
import ru.tickets.models.ErrorResponse
import ru.tickets.security.BotPrincipal

fun Route.performanceRoutes(performanceService: PerformanceService) {
    authenticate("bot-key") {
        get("/theatres/{slug}/performances") {
            val principal = call.principal<BotPrincipal>()!!
            val slug = call.parameters["slug"]!!
            if (!principal.isAdmin && principal.slug != slug) {
                return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", null))
            }
            val telegramId = call.request.queryParameters["telegramId"]?.toLongOrNull()
            call.respond(performanceService.findByTheatreWithSubscriptionStatus(slug, telegramId))
        }
    }
}
