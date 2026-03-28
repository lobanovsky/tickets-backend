package ru.tickets.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.tickets.domain.SubscriptionService
import ru.tickets.domain.UserService
import ru.tickets.models.requests.SyncUserRequest

fun Route.userRoutes(userService: UserService, subscriptionService: SubscriptionService) {
    authenticate("bot-key") {
        post("/users/sync") {
            val req = call.receive<SyncUserRequest>()
            call.respond(HttpStatusCode.OK, userService.syncUser(req))
        }

        get("/users/{telegramId}/subscriptions") {
            val telegramId = call.parameters["telegramId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid telegramId"))
            call.respond(subscriptionService.getUserSubscriptions(telegramId))
        }
    }
}
