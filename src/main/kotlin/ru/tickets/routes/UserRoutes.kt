package ru.tickets.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.tickets.domain.SubscriptionService
import ru.tickets.domain.UserService
import ru.tickets.models.ErrorResponse
import ru.tickets.models.requests.SyncUserRequest
import ru.tickets.security.BotPrincipal

fun Route.userRoutes(userService: UserService, subscriptionService: SubscriptionService) {
    authenticate("bot-key") {
        post("/users/sync") {
            val principal = call.principal<BotPrincipal>()!!
            val req = call.receive<SyncUserRequest>()
            call.respond(HttpStatusCode.OK, userService.syncUser(req, principal.slug))
        }

        get("/users/{telegramId}/subscriptions") {
            val telegramId = call.parameters["telegramId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid telegramId"))
            val theatre = call.request.queryParameters["theatre"]
            call.respond(subscriptionService.getUserSubscriptions(telegramId, theatre))
        }

        post("/admin/users/{telegramId}/vip") {
            val principal = call.principal<BotPrincipal>()!!
            if (!principal.isAdmin) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Forbidden"))
            val telegramId = call.parameters["telegramId"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Invalid telegramId"))
            userService.setVip(telegramId, true)
            call.respond(HttpStatusCode.OK)
        }

        delete("/admin/users/{telegramId}/vip") {
            val principal = call.principal<BotPrincipal>()!!
            if (!principal.isAdmin) return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Forbidden"))
            val telegramId = call.parameters["telegramId"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Invalid telegramId"))
            userService.setVip(telegramId, false)
            call.respond(HttpStatusCode.OK)
        }

        get("/admin/users") {
            val principal = call.principal<BotPrincipal>()!!
            if (!principal.isAdmin) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Forbidden"))
            val hasSubscriptions = call.request.queryParameters["hasSubscriptions"]?.toBooleanStrictOrNull()
            call.respond(userService.findAll(hasSubscriptions))
        }
    }
}
