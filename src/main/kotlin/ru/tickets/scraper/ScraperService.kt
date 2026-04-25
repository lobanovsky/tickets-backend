package ru.tickets.scraper

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import ru.tickets.domain.BotWebhookClient
import ru.tickets.domain.NotificationService
import ru.tickets.domain.PerformanceService
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

private val REPERTOIRE_UPDATE_INTERVAL = 7.days

class ScraperService(
    private val performanceService: PerformanceService,
    private val notificationService: NotificationService,
    private val webhookClient: BotWebhookClient,
    private val scrapers: List<WebScraper>
) {
    private val log = LoggerFactory.getLogger(ScraperService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lastRepertoireUpdate = mutableMapOf<String, Long>()

    fun start() {
        scrapers.forEach { scraper ->
            scope.launch { runScraperLoop(scraper) }
        }
        log.info("Scrapers started for: ${scrapers.map { it.theatreSlug }}")
    }

    private suspend fun runScraperLoop(scraper: WebScraper) {
        while (true) {
            try {
                val theatreId = performanceService.findTheatreIdBySlug(scraper.theatreSlug)
                if (theatreId == null) {
                    log.warn("[${scraper.theatreSlug}] Theatre not found in DB, skipping")
                    delay(60_000L.milliseconds)
                    continue
                }

                // Step 1: Update repertoire (at most once per REPERTOIRE_UPDATE_INTERVAL)
                val now = System.currentTimeMillis()
                val lastUpdate = lastRepertoireUpdate[scraper.theatreSlug]
                if (lastUpdate == null || (now - lastUpdate) >= REPERTOIRE_UPDATE_INTERVAL.inWholeMilliseconds) {
                    val scraped = withContext(Dispatchers.IO) { scraper.scrapeRepertoire() }
                    if (scraped.isNotEmpty()) {
                        performanceService.upsertPerformances(theatreId, scraped)
                    }
                    lastRepertoireUpdate[scraper.theatreSlug] = now
                    log.info("[${scraper.theatreSlug}] Репертуар обновлён")
                }

                // Step 2: Check tickets for performances with active subscribers
                val semaphore = Semaphore(2)
                val performances = performanceService.findWithActiveSubscribers(theatreId)
                log.info("[${scraper.theatreSlug}] Активных спектаклей с подписчиками: ${performances.size}")

                coroutineScope {
                    performances.map { perf ->
                        async {
                            semaphore.withPermit {
                                try {
                                    log.info("[${scraper.theatreSlug}] Проверка спектакля: ${perf.title} (${perf.url})")
                                    val schedule = withContext(Dispatchers.IO) {
                                        scraper.scrapeSchedule(perf.url)
                                    }
                                    if (schedule == null) {
                                        log.warn("[${scraper.theatreSlug}] Скрапинг не удался, пропускаем обновление состояния: ${perf.title}")
                                        return@async
                                    }
                                    val available = schedule.filter { it.ticketsAvailable }
                                    val hasTickets = available.isNotEmpty()
                                    log.info(
                                        "[${scraper.theatreSlug}] Результат проверки: ${perf.title}, " +
                                        "слотов=${schedule.size}, доступных=${available.size}"
                                    )

                                    if (!perf.ticketsAvailable && hasTickets) {
                                        val summary = available.joinToString("\n") { s ->
                                            buildString {
                                                append("• ${s.date}")
                                                if (s.time.isNotBlank()) append(" ${s.time}")
                                            }
                                        }
                                        performanceService.updateTicketsAvailable(perf.id, true, summary)
                                        val created = notificationService.createNotifications(perf.id, summary)
                                        log.info("[${scraper.theatreSlug}] Билеты появились: ${perf.title}, создано уведомлений: ${created.size}")
                                    } else if (perf.ticketsAvailable && !hasTickets) {
                                        performanceService.updateTicketsAvailable(perf.id, false)
                                    }

                                    val pending = notificationService.getPendingForPerformance(perf.id)
                                    for (notif in pending) {
                                        if (webhookClient.push(notif)) {
                                            notificationService.acknowledge(java.util.UUID.fromString(notif.id))
                                            log.info("[${scraper.theatreSlug}] Вебхук отправлен: telegramId=${notif.telegramId}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    log.error("[${scraper.theatreSlug}] Ошибка скрапинга ${perf.url}: ${e.message}")
                                }
                            }
                        }
                    }.awaitAll()
                }
            } catch (e: Exception) {
                log.error("[${scraper.theatreSlug}] Ошибка в цикле скрапера: ${e.message}")
            }

            delay(Random.nextLong(5_000L, 30_000L).milliseconds)
        }
    }
}
