package ru.tickets.scraper

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import ru.tickets.domain.ScrapedPerformance

class NationsScraper : BaseWebScraper() {

    override val theatreSlug = "nations"
    private val log = LoggerFactory.getLogger(NationsScraper::class.java)
    private val baseUrl = "https://theatreofnations.ru"

    override fun scrapeRepertoire(): List<ScrapedPerformance> {
        val performances = mutableListOf<ScrapedPerformance>()
        try {
            val doc = Jsoup.connect("$baseUrl/performances/").get()
            for (link in doc.select("a.sidebar-item")) {
                val title = link.text().trim()
                val relativeUrl = link.attr("href").trim()
                if (title.isNotBlank() && relativeUrl.isNotBlank()) {
                    performances.add(ScrapedPerformance(title = title, url = "$baseUrl$relativeUrl"))
                }
            }
            log.info("[nations] Найдено ${performances.size} спектаклей")
        } catch (e: Exception) {
            log.error("[nations] Ошибка при парсинге репертуара: ${e.message}")
        }
        return performances
    }

    override fun scrapeSchedule(performanceUrl: String): List<ScrapedSchedule> {
        val schedules = mutableListOf<ScrapedSchedule>()
        try {
            val html = fetchHtmlWithSelenium(performanceUrl) ?: return schedules
            val doc = Jsoup.parse(html)
            for (item in doc.select(".play-info__meta-item")) {
                val spans = item.select("span")
                if (spans.size < 2) continue
                val dateTimeText = spans[0].text().trim()
                val parts = dateTimeText.split(" - ")
                if (parts.size != 2) continue
                val date = parts[0].trim()
                val time = parts[1].trim()
                val ticketsAvailable = item.select("a.btn")
                    .any { it.text().contains("Купить билет", ignoreCase = true) }
                schedules.add(ScrapedSchedule(date = date, time = time, ticketsAvailable = ticketsAvailable))
            }
        } catch (e: Exception) {
            log.warn("[nations] Ошибка при парсинге расписания $performanceUrl: ${e.message}")
        }
        return schedules
    }
}
