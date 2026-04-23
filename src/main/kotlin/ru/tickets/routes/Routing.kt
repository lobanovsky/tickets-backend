package ru.tickets.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.tickets.db.DatabaseKey
import ru.tickets.domain.*
import ru.tickets.models.*
import ru.tickets.domain.TelegramSenderService

fun Application.configureRouting() {
    val database = attributes[DatabaseKey]

    val theatreService = TheatreService(database)
    val performanceService = PerformanceService(database)
    val userService = UserService(database)
    val subscriptionService = SubscriptionService(database)
    val notificationService = NotificationService(database)
    val paidSubscriptionService = PaidSubscriptionService(database)

    val botTokens = listOf("vakhtangov", "ramt", "nations", "fomenki", "lensov", "mxt").mapNotNull { slug ->
        val token = environment.config.propertyOrNull("bot-tokens.$slug")?.getString()
        if (!token.isNullOrBlank()) slug to token else null
    }.toMap()
    val telegramApiUrl = environment.config.propertyOrNull("telegram-api.url")?.getString() ?: "http://localhost:8080"
    val telegramApiKey = environment.config.propertyOrNull("telegram-api.key")?.getString() ?: ""
    val telegramSenderService = TelegramSenderService(database, botTokens, telegramApiUrl, telegramApiKey)

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
    }

    install(StatusPages) {
        exception<NotFoundException> { call, e ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", e.message))
        }
        exception<BadRequestException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", e.message))
        }
        exception<ConflictException> { call, e ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("CONFLICT", e.message))
        }
        exception<ForbiddenException> { call, e ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", e.message))
        }
        exception<Throwable> { call, e ->
            call.application.log.error("Unhandled exception", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"))
        }
    }

    routing {
        get("/") {
            call.respondText("Tickets API is running")
        }
        route("/api") {
            theatreRoutes(theatreService)
            userRoutes(userService, subscriptionService)
            performanceRoutes(performanceService)
            subscriptionRoutes(subscriptionService)
            notificationRoutes(notificationService)
            adminRoutes(subscriptionService, theatreService)
            paidSubscriptionRoutes(paidSubscriptionService)
            messageRoutes(telegramSenderService)
        }
    }
}
