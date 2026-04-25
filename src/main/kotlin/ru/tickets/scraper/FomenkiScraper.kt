package ru.tickets.scraper

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import ru.tickets.domain.ScrapedPerformance

class FomenkiScraper : BaseWebScraper() {

    override val theatreSlug = "fomenki"
    private val log = LoggerFactory.getLogger(FomenkiScraper::class.java)
    private val baseUrl = "https://fomenki.ru"
    private val repertoireUrl = "$baseUrl/performance/"

    override fun scrapeRepertoire(): List<ScrapedPerformance> {
        val performances = mutableListOf<ScrapedPerformance>()
        try {
            val doc = Jsoup.connect(repertoireUrl)
                .userAgent("Mozilla/5.0 (compatible; bot)")
                .get()

            val seen = mutableSetOf<String>()
            val cards = doc.select("main a[href][title]").filter { el ->
                val href = el.attr("href")
                val title = el.attr("title").trim()
                title.isNotEmpty() && !href.startsWith("/") && !href.startsWith("http") &&
                !href.startsWith("#") && href.isNotBlank()
            }

            for (card in cards) {
                val title = card.attr("title")
                    .replace("\u00AD", "")
                    .replace("\u00A0", " ")
                    .trim()
                val href = card.attr("href").trim()
                val url = if (href.startsWith("/")) "$baseUrl$href" else "$repertoireUrl$href"
                if (seen.add(url)) {
                    performances.add(ScrapedPerformance(title = title, url = url))
                }
            }
            log.info("[fomenki] Найдено ${performances.size} спектаклей")
        } catch (e: Exception) {
            log.error("[fomenki] Ошибка при парсинге репертуара: ${e.message}")
        }
        return performances
    }

    override fun scrapeSchedule(performanceUrl: String): List<ScrapedSchedule>? {
        return try {
            val html = fetchHtmlWithPlaywright(performanceUrl) ?: return null
            val doc = Jsoup.parse(html)
            val schedules = doc.select("div.event").mapNotNull { block ->
                val dateText = block.selectFirst("p.date")?.text()?.trim() ?: return@mapNotNull null
                val ticketsAvailable = block.select("a[title=Купить билет], a[href*=/boxoffice/]").isNotEmpty()
                ScrapedSchedule(date = dateText, time = "", ticketsAvailable = ticketsAvailable)
            }
            if (schedules.isEmpty()) log.warn("[fomenki] Расписание не найдено для $performanceUrl")
            schedules
        } catch (e: Exception) {
            log.error("[fomenki] Ошибка при парсинге расписания $performanceUrl: ${e.message}")
            null
        }
    }
}
