package ru.tickets.domain

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import org.slf4j.LoggerFactory
import ru.tickets.models.responses.PendingNotificationResponse

data class BotWebhookConfig(val url: String, val secret: String)

class BotWebhookClient(private val webhooks: Map<String, BotWebhookConfig>) {

    private val log = LoggerFactory.getLogger(BotWebhookClient::class.java)
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    suspend fun push(notification: PendingNotificationResponse): Boolean {
        val config = webhooks[notification.theatreSlug] ?: return false
        if (config.url.isBlank()) return false
        return try {
            val response = httpClient.post(config.url) {
                header("X-Webhook-Secret", config.secret)
                contentType(ContentType.Application.Json)
                setBody(notification)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            log.warn("[${notification.theatreSlug}] Не удалось отправить вебхук боту: ${e.message}")
            false
        }
    }
}
