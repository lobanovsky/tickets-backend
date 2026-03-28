package ru.tickets.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.tickets.domain.SubscriptionService
import ru.tickets.domain.TheatreService
import ru.tickets.models.ErrorResponse
import ru.tickets.security.BotPrincipal

fun Route.adminRoutes(subscriptionService: SubscriptionService, theatreService: TheatreService) {
    authenticate("bot-key") {
        route("/admin") {
            get("/theatres/{slug}/subscriptions") {
                val principal = call.principal<BotPrincipal>()!!
                val slug = call.parameters["slug"]!!
                if (!principal.isAdmin && principal.slug != slug) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", null))
                }
                call.respond(subscriptionService.getAdminSubscriptionGroups(slug))
            }

            get("/stats") {
                val principal = call.principal<BotPrincipal>()!!
                if (!principal.isAdmin) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", null))
                }
                call.respond(theatreService.getStats())
            }
        }
    }
}
