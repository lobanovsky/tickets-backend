package ru.tickets

import io.ktor.server.application.*
import ru.tickets.db.DatabaseKey
import ru.tickets.db.configureDatabases
import ru.tickets.domain.NotificationService
import ru.tickets.domain.PerformanceService
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

    // Один скрапер на каждый театр — каждый умеет парсить расписание своего сайта
    val scrapers = listOf(
        RamtScraper(),
        NationsScraper(),
        VakhtangovScraper(),
        FomenkiScraper()
    )
    // Оркестратор: запускает все скраперы по расписанию, сохраняет спектакли и рассылает уведомления
    val scraperService = ScraperService(database, performanceService, notificationService, scrapers)

    // Стартуем после полной инициализации сервера, чтобы БД и сервисы были готовы
    monitor.subscribe(ApplicationStarted) {
        scraperService.start()
    }
}
