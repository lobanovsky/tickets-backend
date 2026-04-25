package ru.tickets.scraper

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import ru.tickets.domain.ScrapedPerformance

class VakhtangovScraper : BaseWebScraper() {

    override val theatreSlug = "vakhtangov"
    private val log = LoggerFactory.getLogger(VakhtangovScraper::class.java)

    override fun scrapeRepertoire(): List<ScrapedPerformance> {
        val performances = mutableListOf<ScrapedPerformance>()
        try {
            val doc = Jsoup.connect("https://vakhtangov.ru/shows/now/").get()
            val allowedScenes = setOf("Основная сцена", "Новая сцена", "Симоновская сцена")
            for (section in doc.select("section.shows-stage")) {
                val scene = section.selectFirst("header.shows-stage-header h2")?.text() ?: continue
                if (scene !in allowedScenes) continue
                for (show in section.select("article.shows-item")) {
                    val link = show.selectFirst("a") ?: continue
                    val title = link.selectFirst("h1")?.text() ?: continue
                    val href = link.attr("href")
                    if (href.isNotBlank()) {
                        performances.add(ScrapedPerformance(title = title, url = href, scene = scene))
                    }
                }
            }
            log.info("[vakhtangov] Найдено ${performances.size} спектаклей")
        } catch (e: Exception) {
            log.error("[vakhtangov] Ошибка при парсинге репертуара: ${e.message}")
        }
        return performances
    }

    override fun scrapeSchedule(performanceUrl: String): List<ScrapedSchedule> {
        val schedules = mutableListOf<ScrapedSchedule>()
        try {
            val html = fetchHtmlWithPlaywright(performanceUrl) ?: return schedules
            val doc = Jsoup.parse(html)
            for (li in doc.select("ul.show-afisha > li")) {
                val dateElem = li.selectFirst("span.date > span.date")
                    ?: li.selectFirst("p.info span.date")
                val rawDate = dateElem?.text()?.trim().orEmpty()
                val date = rawDate.split(",").firstOrNull()?.trim()?.removeSuffix(",").orEmpty()
                val time = li.select("span.time").firstOrNull()?.text()?.trim() ?: ""
                val ticketButton = li.select("a.js-buy-tickets-btn").firstOrNull()
                val ticketsAvailable = ticketButton != null && !ticketButton.hasClass("disabled")
                if (date.isNotBlank() && time.isNotBlank()) {
                    schedules.add(ScrapedSchedule(date = date, time = time, ticketsAvailable = ticketsAvailable))
                }
            }
        } catch (e: Exception) {
            log.warn("[vakhtangov] Ошибка при парсинге расписания $performanceUrl: ${e.message}")
        }
        return schedules
    }
}
