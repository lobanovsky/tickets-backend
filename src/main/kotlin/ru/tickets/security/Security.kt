package ru.tickets.security

import io.ktor.server.application.*
import io.ktor.server.auth.*

data class BotPrincipal(val slug: String, val isAdmin: Boolean)

fun Application.configureSecurity() {
    val keys: Map<String, BotPrincipal> = mapOf(
        environment.config.property("api-keys.ramt").getString() to BotPrincipal("ramt", false),
        environment.config.property("api-keys.nations").getString() to BotPrincipal("nations", false),
        environment.config.property("api-keys.vakhtangov").getString() to BotPrincipal("vakhtangov", false),
        environment.config.property("api-keys.fomenki").getString() to BotPrincipal("fomenki", false),
        environment.config.property("api-keys.lensov").getString() to BotPrincipal("lensov", false),
        environment.config.property("api-keys.mxt").getString() to BotPrincipal("mxt", false),
        environment.config.property("api-keys.satirikon").getString() to BotPrincipal("satirikon", false),
        environment.config.property("api-keys.admin").getString() to BotPrincipal("admin", true),
    )

    authentication {
        bearer("bot-key") {
            authenticate { credential ->
                keys[credential.token]
            }
        }
    }
}
