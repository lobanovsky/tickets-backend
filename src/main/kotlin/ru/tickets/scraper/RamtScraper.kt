package ru.tickets.scraper

import com.microsoft.playwright.Browser
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import ru.tickets.domain.ScrapedPerformance

class RamtScraper : BaseWebScraper() {

    override val theatreSlug = "ramt"
    private val log = LoggerFactory.getLogger(RamtScraper::class.java)

    override fun scrapeRepertoire(): List<ScrapedPerformance> {
        val performances = mutableListOf<ScrapedPerformance>()
        try {
            val doc = Jsoup.connect("https://ramt.ru/plays/repertuar/").get()
            for (card in doc.select(".performances-card")) {
                val title = card.selectFirst(".performances-card__title")?.text()?.trim() ?: continue
                val url = card.attr("abs:href").trim()
                if (url.isNotBlank()) performances.add(ScrapedPerformance(title = title, url = url))
            }
            log.info("[ramt] Найдено ${performances.size} спектаклей")
        } catch (e: Exception) {
            log.error("[ramt] Ошибка при парсинге репертуара: ${e.message}")
        }
        return performances
    }

    override fun scrapeSchedule(performanceUrl: String, browser: Browser): List<ScrapedSchedule> {
        val schedules = mutableListOf<ScrapedSchedule>()
        try {
            val html = fetchHtmlWithSelenium(browser, performanceUrl) ?: return schedules
            val doc = Jsoup.parse(html)
            for (item in doc.select(".afisha-list__item")) {
                val date = item.selectFirst(".afisha-card__date")?.text()?.trim() ?: continue
                val time = item.select(".afisha-card__date").getOrNull(1)?.text()?.trim() ?: ""
                val ticketsAvailable = item.selectFirst(".afisha-card__tickets-mobile a:contains(Билеты)") != null
                schedules.add(ScrapedSchedule(date = date, time = time, ticketsAvailable = ticketsAvailable))
            }
        } catch (e: Exception) {
            log.warn("[ramt] Ошибка при парсинге расписания $performanceUrl: ${e.message}")
        }
        return schedules
    }
}
