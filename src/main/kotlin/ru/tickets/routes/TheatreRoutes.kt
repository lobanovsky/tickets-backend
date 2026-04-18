package ru.tickets.routes

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.tickets.domain.TheatreService

fun Route.theatreRoutes(theatreService: TheatreService) {
    authenticate("bot-key") {
        get("/theatres") {
            call.respond(theatreService.findAll())
        }
    }
}
