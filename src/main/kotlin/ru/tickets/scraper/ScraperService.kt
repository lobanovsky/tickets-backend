package ru.tickets.scraper

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import ru.tickets.domain.NotificationService
import ru.tickets.domain.PerformanceService
import kotlin.random.Random

class ScraperService(
    private val database: Database,
    private val performanceService: PerformanceService,
    private val notificationService: NotificationService,
    private val scrapers: List<WebScraper>
) {
    private val log = LoggerFactory.getLogger(ScraperService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
                    delay(60_000L)
                    continue
                }

                // Step 1: Update repertoire
                val scraped = withContext(Dispatchers.IO) { scraper.scrapeRepertoire() }
                if (scraped.isNotEmpty()) {
                    performanceService.upsertPerformances(theatreId, scraped)
                }

                // Step 2: Check tickets for performances with active subscribers
                val semaphore = Semaphore(2)
                val performances = performanceService.findWithActiveSubscribers(theatreId)

                coroutineScope {
                    performances.map { perf ->
                        async {
                            semaphore.withPermit {
                                try {
                                    val schedule = withContext(Dispatchers.IO) {
                                        scraper.scrapeSchedule(perf.url)
                                    }
                                    val available = schedule.filter { it.ticketsAvailable }
                                    if (available.isNotEmpty()) {
                                        val summary = available.joinToString("\n") { s ->
                                            buildString {
                                                append("• ${s.date}")
                                                if (s.time.isNotBlank()) append(" ${s.time}")
                                            }
                                        }
                                        notificationService.createNotifications(perf.id, summary)
                                        log.info("[${scraper.theatreSlug}] Билеты найдены: ${perf.title}")
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

            delay(Random.nextLong(5_000L, 30_000L))
        }
    }
}
