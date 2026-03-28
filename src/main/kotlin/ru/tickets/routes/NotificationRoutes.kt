package ru.tickets.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.tickets.domain.NotificationService
import ru.tickets.models.ErrorResponse
import ru.tickets.security.BotPrincipal
import java.util.*

fun Route.notificationRoutes(notificationService: NotificationService) {
    authenticate("bot-key") {
        get("/notifications/pending") {
            val principal = call.principal<BotPrincipal>()!!
            val slug = call.request.queryParameters["theatreSlug"] ?: principal.slug
            if (!principal.isAdmin && principal.slug != slug) {
                return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", null))
            }
            call.respond(notificationService.getPendingForTheatre(slug))
        }

        post("/notifications/{id}/ack") {
            val id = UUID.fromString(call.parameters["id"]!!)
            notificationService.acknowledge(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}
