package ru.tickets.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.tickets.domain.SubscriptionService
import ru.tickets.models.requests.SubscribeRequest
import ru.tickets.models.requests.UnsubscribeRequest
import java.util.*

fun Route.subscriptionRoutes(subscriptionService: SubscriptionService) {
    authenticate("bot-key") {
        post("/subscriptions") {
            val req = call.receive<SubscribeRequest>()
            subscriptionService.subscribe(req.telegramId, UUID.fromString(req.performanceId))
            call.respond(HttpStatusCode.Created, mapOf("status" to "subscribed"))
        }

        delete("/subscriptions") {
            val req = call.receive<UnsubscribeRequest>()
            subscriptionService.unsubscribe(req.telegramId, UUID.fromString(req.performanceId))
            call.respond(HttpStatusCode.OK, mapOf("status" to "unsubscribed"))
        }
    }
}
