package ru.tickets.scraper

import com.microsoft.playwright.options.WaitUntilState
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import ru.tickets.domain.ScrapedPerformance

class SatirikonScraper : BaseWebScraper() {

    override val theatreSlug = "satirikon"
    private val log = LoggerFactory.getLogger(SatirikonScraper::class.java)
    private val baseUrl = "https://www.satirikon.ru"
    private val repertoireUrl = "$baseUrl/spektakli/repertuar/"
    private val dateRegex = Regex("""\d{2}\.\d{2}""")
    private val timeRegex = Regex("""\d{2}:\d{2}""")

    override fun scrapeRepertoire(): List<ScrapedPerformance> {
        val performances = mutableListOf<ScrapedPerformance>()
        val seen = mutableSetOf<String>()
        try {
            val pageUrls = fetchRepertoirePageUrls()
            log.info("[satirikon] Найдено ${pageUrls.size} страниц репертуара")
            for (pageUrl in pageUrls) {
                val html = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (compatible; bot)")
                    .get().outerHtml()
                parseRepertoireHtml(html).forEach { p ->
                    if (seen.add(p.url)) performances.add(p)
                }
            }
        } catch (e: Exception) {
            log.error("[satirikon] Ошибка при парсинге репертуара: ${e.message}")
        }
        log.info("[satirikon] Найдено ${performances.size} спектаклей")
        return performances
    }

    override fun scrapeSchedule(performanceUrl: String): List<ScrapedSchedule>? {
        return try {
            val html = fetchHtmlWithPlaywright(performanceUrl, WaitUntilState.LOAD) ?: return null
            val schedules = parseScheduleHtml(html)
            if (schedules.isEmpty()) log.warn("[satirikon] Расписание не найдено для $performanceUrl")
            schedules
        } catch (e: Exception) {
            log.error("[satirikon] Ошибка при парсинге расписания $performanceUrl: ${e.message}")
            null
        }
    }

    internal fun parseRepertoireHtml(html: String): List<ScrapedPerformance> {
        val doc = Jsoup.parse(html, baseUrl)
        val seen = mutableSetOf<String>()
        return doc.select("h3 a[href]").mapNotNull { a ->
            val href = a.attr("href").trim()
            if (!href.contains("/spektakli/repertuar/") || href.trimEnd('/') == "/spektakli/repertuar") return@mapNotNull null
            val url = if (href.startsWith("http")) href else "$baseUrl$href"
            val title = a.text().trim().ifBlank { return@mapNotNull null }
            if (seen.add(url)) ScrapedPerformance(title = title, url = url) else null
        }
    }

    internal fun parseScheduleHtml(html: String): List<ScrapedSchedule> {
        val doc = Jsoup.parse(html)
        val schedules = mutableListOf<ScrapedSchedule>()

        doc.select("button[onclick*=\"afishaWidget.openModal\"]").forEach { btn ->
            val container = btn.parents().firstOrNull { el ->
                val text = el.ownText() + " " + el.children().joinToString(" ") { it.ownText() }
                dateRegex.containsMatchIn(text) && timeRegex.containsMatchIn(text)
            } ?: btn.parent() ?: return@forEach

            val containerText = container.text()
            val date = dateRegex.find(containerText)?.value ?: return@forEach
            val time = timeRegex.find(containerText)?.value ?: ""
            schedules.add(ScrapedSchedule(date = date, time = time, ticketsAvailable = true))
        }

        if (schedules.isEmpty()) {
            log.warn(
                "[satirikon] Расписание не распознано. Кнопок afishaWidget: " +
                    "${doc.select("button[onclick*=\"afishaWidget\"]").size}. " +
                    "HTML фрагмент:\n${doc.body().html().take(1500)}"
            )
        }
        return schedules
    }

    private fun fetchRepertoirePageUrls(): List<String> {
        val doc = Jsoup.connect(repertoireUrl)
            .userAgent("Mozilla/5.0 (compatible; bot)")
            .get()
        val extraPages = doc.select("a[href*=PAGEN_1]").mapNotNull { a ->
            val href = a.attr("href").trim()
            if (href.isBlank()) null else "$baseUrl/spektakli/repertuar/$href"
        }.distinct()
        return listOf(repertoireUrl) + extraPages
    }
}
