package ru.tickets.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.tickets.domain.TelegramSenderService
import ru.tickets.models.ErrorResponse
import ru.tickets.models.requests.SendMessageRequest
import ru.tickets.security.BotPrincipal

fun Route.messageRoutes(telegramSenderService: TelegramSenderService) {
    authenticate("bot-key") {
        post("/admin/messages/send/user/{telegramId}") {
            val principal = call.principal<BotPrincipal>()!!
            if (!principal.isAdmin) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Forbidden"))
            val telegramId = call.parameters["telegramId"]?.toLongOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Invalid telegramId"))
            val botSlug = call.request.queryParameters["botSlug"]
            val req = call.receive<SendMessageRequest>()
            call.respond(telegramSenderService.sendToUser(telegramId, req.text, req.parseMode, botSlug))
        }

        post("/admin/messages/send/bot/{slug}") {
            val principal = call.principal<BotPrincipal>()!!
            val slug = call.parameters["slug"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Missing slug"))
            if (!principal.isAdmin && principal.slug != slug) {
                return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Forbidden"))
            }
            val req = call.receive<SendMessageRequest>()
            call.respond(telegramSenderService.sendToBot(slug, req.text, req.parseMode))
        }

        post("/admin/messages/send/all") {
            val principal = call.principal<BotPrincipal>()!!
            if (!principal.isAdmin) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Forbidden"))
            val req = call.receive<SendMessageRequest>()
            call.respond(telegramSenderService.sendToAll(req.text, req.parseMode))
        }
    }
}
