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
        val schedules = mutableListOf<ScrapedSchedule>()
        try {
            val html = fetchHtmlWithSelenium(performanceUrl) ?: return schedules
            val doc = Jsoup.parse(html)
            val items = doc.select(".restaurantBookTable .colorbox-items li")
            log.info("[lensov] Найдено DOM-слотов расписания: ${items.size} для $performanceUrl")
            for (li in items) {
                val dateText = li.selectFirst("div")?.text()?.trim() ?: continue
                val actionNodes = li.select(".wb-button-root, .wb-button")
                val ticketsAvailable = actionNodes.any { node ->
                    val classes = node.classNames()
                    val text = node.text().trim()
                    !classes.contains("waitlist") &&
                        (
                            text.equals("Купить билет", ignoreCase = true) ||
                                (classes.contains("button-primary") &&
                                    text.equals("Купить билет", ignoreCase = true))
                        ) &&
                        !text.contains("Оставить заявку", ignoreCase = true)
                }
                val actionClasses = li.select("a, button, div").eachAttr("class").filter { it.isNotBlank() }.distinct()
                val slotHtml = li.html()
                    .replace(Regex("\\s+"), " ")
                    .take(600)
                log.info("[lensov] Слот: date=\"$dateText\", ticketsAvailable=$ticketsAvailable, url=$performanceUrl")
                log.info("[lensov] Слот debug: classes=$actionClasses, html=$slotHtml")
                schedules.add(ScrapedSchedule(date = dateText, time = "", ticketsAvailable = ticketsAvailable))
            }
            if (schedules.isEmpty()) log.warn("[lensov] Расписание не найдено для $performanceUrl")
        } catch (e: Exception) {
            log.error("[lensov] Ошибка при парсинге расписания $performanceUrl: ${e.message}")
        }
        return schedules
    }
}
