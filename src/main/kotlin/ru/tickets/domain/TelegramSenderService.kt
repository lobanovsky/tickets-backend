package ru.tickets.domain

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.slf4j.LoggerFactory
import ru.tickets.db.schema.UserBotLinks
import ru.tickets.db.schema.Users
import ru.tickets.models.responses.MessageSendResponse
import ru.tickets.models.responses.MessageSendResult
import java.net.Authenticator
import java.net.PasswordAuthentication

data class TelegramProxyConfig(
    val host: String,
    val port: Int,
    val user: String,
    val password: String
)

class TelegramSenderService(
    private val database: Database,
    private val botTokens: Map<String, String>,
    private val proxy: TelegramProxyConfig? = null
) {
    private val log = LoggerFactory.getLogger(TelegramSenderService::class.java)

    private val telegramJson = Json { ignoreUnknownKeys = true }

    private val httpClient: HttpClient = run {
        val proxyConfig = proxy
        if (proxyConfig != null) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(proxyConfig.user, proxyConfig.password.toCharArray())
            })
        }
        HttpClient(CIO) {
            engine {
                if (proxyConfig != null) {
                    proxy = ProxyBuilder.socks(proxyConfig.host, proxyConfig.port)
                }
            }
            install(ContentNegotiation) { json(telegramJson) }
        }
    }

    @Serializable
    private data class TelegramSendRequest(
        @SerialName("chat_id") val chatId: Long,
        val text: String,
        @SerialName("parse_mode") val parseMode: String? = null
    )

    @Serializable
    private data class TelegramResponse(
        val ok: Boolean,
        @SerialName("error_code") val errorCode: Int? = null,
        val description: String? = null
    )

    suspend fun sendToUser(
        telegramId: Long,
        text: String,
        parseMode: String? = null,
        filterBotSlug: String? = null
    ): MessageSendResponse {
        val slugsToTry = if (filterBotSlug != null) {
            botTokens.keys.filter { it == filterBotSlug }
        } else {
            val knownBlocked = dbQuery(database) {
                UserBotLinks.join(Users, JoinType.INNER, UserBotLinks.userId, Users.id)
                    .selectAll()
                    .where { (Users.telegramId eq telegramId) and (UserBotLinks.isSubscribed eq false) }
                    .map { it[UserBotLinks.botSlug] }
                    .toSet()
            }
            botTokens.keys.filter { it !in knownBlocked }
        }

        val results = slugsToTry.map { slug ->
            val token = botTokens[slug]!!
            val (success, errorDesc) = callTelegramApi(token, telegramId, text, parseMode)
            val definitiveAnswer = success || isBlockedError(errorDesc)
            if (definitiveAnswer) updateBotLink(telegramId, slug, success)
            MessageSendResult(slug, telegramId, success, if (!success) errorDesc else null)
        }
        return toResponse(results)
    }

    suspend fun sendToBot(botSlug: String, text: String, parseMode: String? = null): MessageSendResponse {
        val token = botTokens[botSlug] ?: return MessageSendResponse(0, 0, emptyList())
        val telegramIds = dbQuery(database) {
            val blockedUserIds = UserBotLinks.selectAll()
                .where { (UserBotLinks.botSlug eq botSlug) and (UserBotLinks.isSubscribed eq false) }
                .map { it[UserBotLinks.userId] }
                .toSet()
            Users.selectAll()
                .where { Users.id notInList blockedUserIds.toList() }
                .map { it[Users.telegramId] }
        }

        val results = telegramIds.map { telegramId ->
            val (success, errorDesc) = callTelegramApi(token, telegramId, text, parseMode)
            if (success || isBlockedError(errorDesc)) updateBotLink(telegramId, botSlug, success)
            MessageSendResult(botSlug, telegramId, success, if (!success) errorDesc else null)
        }
        return toResponse(results)
    }

    suspend fun sendToAll(text: String, parseMode: String? = null): MessageSendResponse {
        val telegramIds = dbQuery(database) { Users.selectAll().map { it[Users.telegramId] } }
        val results = telegramIds.flatMap { telegramId ->
            sendToUser(telegramId, text, parseMode).results
        }
        return toResponse(results)
    }

    private suspend fun callTelegramApi(
        token: String,
        telegramId: Long,
        text: String,
        parseMode: String?
    ): Pair<Boolean, String?> = try {
        val response = httpClient.post("https://api.telegram.org/bot$token/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(TelegramSendRequest(telegramId, text, parseMode))
        }
        val tgResponse = response.body<TelegramResponse>()
        if (tgResponse.ok) true to null else false to tgResponse.description
    } catch (e: Exception) {
        log.warn("Telegram API error for telegramId=$telegramId: ${e.message}")
        false to e.message
    }

    private fun isBlockedError(description: String?): Boolean {
        if (description == null) return false
        return description.contains("403") ||
                description.contains("bot was blocked") ||
                description.contains("chat not found", ignoreCase = true) ||
                description.contains("user is deactivated", ignoreCase = true)
    }

    private suspend fun updateBotLink(telegramId: Long, botSlug: String, isSubscribed: Boolean) = dbQuery(database) {
        val userId = Users.selectAll()
            .where { Users.telegramId eq telegramId }
            .singleOrNull()?.get(Users.id) ?: return@dbQuery

        val exists = UserBotLinks.selectAll()
            .where { (UserBotLinks.userId eq userId) and (UserBotLinks.botSlug eq botSlug) }
            .count() > 0

        if (exists) {
            UserBotLinks.update({ (UserBotLinks.userId eq userId) and (UserBotLinks.botSlug eq botSlug) }) {
                it[UserBotLinks.isSubscribed] = isSubscribed
                it[UserBotLinks.checkedAt] = java.time.LocalDateTime.now()
            }
        } else {
            UserBotLinks.insert {
                it[this.userId] = userId
                it[this.botSlug] = botSlug
                it[this.isSubscribed] = isSubscribed
                it[checkedAt] = java.time.LocalDateTime.now()
            }
        }
    }

    private fun toResponse(results: List<MessageSendResult>) = MessageSendResponse(
        total = results.size,
        succeeded = results.count { it.success },
        results = results
    )
}
