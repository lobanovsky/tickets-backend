package ru.tickets.scraper

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import ru.tickets.domain.ScrapedPerformance

class LensovScraper : BaseWebScraper() {

    override val theatreSlug = "lensov"
    private val log = LoggerFactory.getLogger(LensovScraper::class.java)
    private val baseUrl = "https://lensov-theatre.spb.ru"
    private val repertoireUrl = "$baseUrl/repertoire/"

    override fun scrapeRepertoire(): List<ScrapedPerformance> {
        val performances = mutableListOf<ScrapedPerformance>()
        try {
            val doc = Jsoup.connect(repertoireUrl)
                .userAgent("Mozilla/5.0 (compatible; bot)")
                .get()
            val seen = mutableSetOf<String>()
            for (a in doc.select("div.newsbox a[href*=/repertoire/]")) {
                val title = a.selectFirst("h2")?.text()?.trim() ?: continue
                val href = a.attr("href").trim()
                val url = if (href.startsWith("http")) href else "$baseUrl$href"
                if (seen.add(url)) performances.add(ScrapedPerformance(title = title, url = url))
            }
            log.info("[lensov] Найдено ${performances.size} спектаклей")
        } catch (e: Exception) {
            log.error("[lensov] Ошибка при парсинге репертуара: ${e.message}")
        }
        return performances
    }

    override fun scrapeSchedule(performanceUrl: String): List<ScrapedSchedule> {
        try {
            val html = fetchHtmlWithSelenium(performanceUrl) ?: return emptyList()
            val schedules = parseScheduleHtml(html)
            if (schedules.isEmpty()) log.warn("[lensov] Расписание не найдено для $performanceUrl")
            return schedules
        } catch (e: Exception) {
            log.error("[lensov] Ошибка при парсинге расписания $performanceUrl: ${e.message}")
        }
        return emptyList()
    }

    internal fun parseScheduleHtml(html: String): List<ScrapedSchedule> {
        val doc = Jsoup.parse(html)
        return doc.select(".restaurantBookTable .colorbox-items li").mapNotNull { li ->
            val dateText = li.selectFirst("div")?.text()?.trim() ?: return@mapNotNull null
            val ticketsAvailable = li.select(".wb-button-root, .wb-button").any { node ->
                val text = node.text().trim()
                val classes = node.classNames()
                text.equals("Купить билет", ignoreCase = true) && !classes.contains("waitlist")
            }
            ScrapedSchedule(date = dateText, time = "", ticketsAvailable = ticketsAvailable)
        }
    }
}
