package ru.tickets.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.tickets.domain.PaidSubscriptionService
import ru.tickets.models.ErrorResponse
import ru.tickets.models.requests.CreatePaidSubscriptionRequest
import ru.tickets.models.requests.UpdatePaidSubscriptionRequest
import ru.tickets.security.BotPrincipal
import java.time.LocalDate
import java.util.UUID

fun Route.paidSubscriptionRoutes(paidSubscriptionService: PaidSubscriptionService) {
    authenticate("bot-key") {
        get("/users/{telegramId}/paid-subscription") {
            val telegramId = call.parameters["telegramId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Invalid telegramId"))
            call.respond(paidSubscriptionService.getStatus(telegramId))
        }

        post("/admin/users/{telegramId}/paid-subscriptions") {
            val principal = call.principal<BotPrincipal>()!!
            if (!principal.isAdmin) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Forbidden"))
            val telegramId = call.parameters["telegramId"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Invalid telegramId"))
            val req = call.receive<CreatePaidSubscriptionRequest>()
            val result = paidSubscriptionService.create(telegramId, req, principal.slug)
            call.respond(HttpStatusCode.Created, result)
        }

        get("/admin/users/{telegramId}/paid-subscriptions") {
            val principal = call.principal<BotPrincipal>()!!
            if (!principal.isAdmin) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Forbidden"))
            val telegramId = call.parameters["telegramId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Invalid telegramId"))
            call.respond(paidSubscriptionService.getHistory(telegramId))
        }

        post("/admin/users/{telegramId}/trial") {
            val principal = call.principal<BotPrincipal>()!!
            if (!principal.isAdmin) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Forbidden"))
            val telegramId = call.parameters["telegramId"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Invalid telegramId"))
            val today = LocalDate.now()
            val req = CreatePaidSubscriptionRequest(
                startDate = today.toString(),
                endDate = today.plusDays(7).toString(),
                amountPaid = 0,
                comment = "Пробный период"
            )
            val result = paidSubscriptionService.create(telegramId, req, principal.slug)
            call.respond(HttpStatusCode.Created, result)
        }

        patch("/admin/paid-subscriptions/{id}") {
            val principal = call.principal<BotPrincipal>()!!
            if (!principal.isAdmin) return@patch call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Forbidden"))
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Invalid id"))
            val req = call.receive<UpdatePaidSubscriptionRequest>()
            call.respond(paidSubscriptionService.update(id, req))
        }
    }
}
