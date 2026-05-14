package ru.tickets

import io.ktor.server.application.*
import ru.tickets.db.DatabaseKey
import ru.tickets.db.configureDatabases
import ru.tickets.domain.BotWebhookClient
import ru.tickets.domain.BotWebhookConfig
import ru.tickets.domain.NotificationService
import ru.tickets.domain.PaidSubscriptionService
import ru.tickets.domain.PerformanceService
import ru.tickets.domain.SubscriptionScheduler
import ru.tickets.domain.TelegramSenderService
import ru.tickets.routes.configureRouting
import ru.tickets.scraper.*
import ru.tickets.security.configureSecurity

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureDatabases()
    configureSecurity()
    configureRouting()
    startScrapers()
}

fun Application.startScrapers() {
    val database = attributes[DatabaseKey]
    val performanceService = PerformanceService(database)
    val notificationService = NotificationService(database)
    val paidSubscriptionService = PaidSubscriptionService(database)
    val botTokens = listOf("vakhtangov", "ramt", "nations", "fomenki", "lensov", "mxt", "satirikon").mapNotNull { slug ->
        val token = environment.config.propertyOrNull("bot-tokens.$slug")?.getString()
        if (!token.isNullOrBlank()) slug to token else null
    }.toMap()
    val telegramApiUrl = environment.config.propertyOrNull("telegram-api.url")?.getString() ?: "http://localhost:8080"
    val telegramApiKey = environment.config.propertyOrNull("telegram-api.key")?.getString() ?: ""
    val telegramSenderService = TelegramSenderService(database, botTokens, telegramApiUrl, telegramApiKey)
    val subscriptionScheduler = SubscriptionScheduler(paidSubscriptionService, telegramSenderService, notificationService)

    val webhooks = listOf("vakhtangov", "ramt", "nations", "fomenki", "lensov", "mxt", "satirikon").associateWith { slug ->
        BotWebhookConfig(
            url = environment.config.propertyOrNull("bot-webhooks.$slug.url")?.getString() ?: "",
            secret = environment.config.propertyOrNull("bot-webhooks.$slug.secret")?.getString() ?: ""
        )
    }
    val webhookClient = BotWebhookClient(webhooks)

    // Один скрапер на каждый театр — каждый умеет парсить расписание своего сайта
    val scrapers = listOf(
        RamtScraper(),
        NationsScraper(),
        VakhtangovScraper(),
        FomenkiScraper(),
        LensovScraper(),
        MxtScraper(),
        SatirikonScraper()
    )
    // Оркестратор: запускает все скраперы по расписанию, сохраняет спектакли и рассылает уведомления
    val scraperService = ScraperService(performanceService, notificationService, webhookClient, scrapers)

    // Стартуем после полной инициализации сервера, чтобы БД и сервисы были готовы
    monitor.subscribe(ApplicationStarted) {
        scraperService.start()
        subscriptionScheduler.start()
    }
}
