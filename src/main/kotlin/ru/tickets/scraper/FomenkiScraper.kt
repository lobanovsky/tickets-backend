package ru.tickets.scraper

import com.microsoft.playwright.Browser
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

    override fun scrapeSchedule(performanceUrl: String, browser: Browser): List<ScrapedSchedule> {
        val schedules = mutableListOf<ScrapedSchedule>()
        try {
            val html = fetchHtmlWithSelenium(browser, performanceUrl) ?: return schedules
            val doc = Jsoup.parse(html)
            for (block in doc.select("div.event")) {
                val dateText = block.selectFirst("p.date")?.text()?.trim() ?: continue
                val ticketsAvailable = block.select("a[title=Купить билет], a[href*=/boxoffice/]").isNotEmpty()
                schedules.add(ScrapedSchedule(date = dateText, time = "", ticketsAvailable = ticketsAvailable))
            }
            if (schedules.isEmpty()) {
                log.warn("[fomenki] Расписание не найдено для $performanceUrl")
            }
        } catch (e: Exception) {
            log.error("[fomenki] Ошибка при парсинге расписания $performanceUrl: ${e.message}")
        }
        return schedules
    }
}
