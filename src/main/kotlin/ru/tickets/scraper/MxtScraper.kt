package ru.tickets.scraper

import com.microsoft.playwright.options.WaitUntilState
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import ru.tickets.domain.ScrapedPerformance

class MxtScraper : BaseWebScraper() {

    override val theatreSlug = "mxt"
    private val log = LoggerFactory.getLogger(MxtScraper::class.java)
    private val baseUrl = "https://mxat.ru"
    private val repertoireUrls = listOf("$baseUrl/repertuar/current/", "$baseUrl/repertuar/soon/")
    private val performanceLinkSelector = "a[href*=/repertuar/show/]"
    private val sceneRegex = Regex("(Основная сцена|Малая сцена|Новая сцена|Дворец на Яузе|Театриум на Серпуховке|Портретное фойе)")

    override fun scrapeRepertoire(): List<ScrapedPerformance> {
        val performances = mutableListOf<ScrapedPerformance>()
        val seen = mutableSetOf<String>()
        for (repertoireUrl in repertoireUrls) {
            try {
                val pageUrls = fetchRepertoirePageUrls(repertoireUrl)
                log.info("[mxt] Для $repertoireUrl найдено ${pageUrls.size} страниц репертуара")

                pageUrls.forEach { pageUrl ->
                    val html = Jsoup.connect(pageUrl)
                        .userAgent("Mozilla/5.0 (compatible; bot)")
                        .get()
                        .outerHtml()
                    val parsed = parseRepertoireHtml(html)
                    log.info("[mxt] Для $pageUrl найдено ${parsed.size} карточек спектаклей")
                    parsed.forEach { performance ->
                        if (seen.add(performance.url)) {
                            performances.add(performance)
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("[mxt] Ошибка при парсинге репертуара $repertoireUrl: ${e.message}")
            }
        }
        log.info("[mxt] Найдено ${performances.size} спектаклей")
        return performances
    }

    override fun scrapeSchedule(performanceUrl: String): List<ScrapedSchedule> {
        try {
            val html = fetchHtmlWithPlaywright(performanceUrl, WaitUntilState.NETWORKIDLE) ?: return emptyList()
            val schedules = parseScheduleHtml(html)
            if (schedules.isEmpty()) log.warn("[mxt] Расписание не найдено для $performanceUrl")
            return schedules
        } catch (e: Exception) {
            log.error("[mxt] Ошибка при парсинге расписания $performanceUrl: ${e.message}")
        }
        return emptyList()
    }

    internal fun parseScheduleHtml(html: String): List<ScrapedSchedule> {
        val doc = Jsoup.parse(html)
        val schedules = mutableListOf<ScrapedSchedule>()

        doc.select("[data-tickets-button]").forEach { button ->
            val container = button.parents().firstOrNull { it.selectFirst("time[datetime]") != null }
                ?: return@forEach
            val timeEl = container.selectFirst("time[datetime]") ?: return@forEach

            val datetime = timeEl.attr("datetime") // "2026-05-14 19:00"
            val timeStr = datetime.substringAfter(" ", "")
            val dateStr = timeEl.select("span").firstOrNull { it.attr("aria-hidden") != "true" }
                ?.text()?.trim() ?: datetime.substringBefore(" ")

            val desktopSpan = button.selectFirst("[data-tickets-desktop-button-text]")
            val ticketsAvailable = desktopSpan?.text()?.trim() == desktopSpan?.attr("data-has-tickets-text")

            schedules.add(ScrapedSchedule(date = dateStr, time = timeStr, ticketsAvailable = ticketsAvailable))
        }

        if (schedules.isEmpty()) {
            log.warn(
                "[mxt] Расписание не распознано. Найдено кнопок: ${doc.select("[data-tickets-button]").size}. " +
                    "Фрагмент HTML:\n${doc.body().html().take(1500)}"
            )
        }
        return schedules
    }

    internal fun parseRepertoireHtml(html: String): List<ScrapedPerformance> {
        val doc = Jsoup.parse(html, baseUrl)
        val seen = mutableSetOf<String>()
        return doc.select(performanceLinkSelector).mapNotNull { link ->
            val url = link.absUrl("href").trim().ifEmpty { return@mapNotNull null }
            if (!seen.add(url)) return@mapNotNull null

            val title = extractTitle(link) ?: return@mapNotNull null
            val scene = extractScene(link)
            ScrapedPerformance(title = title, url = url, scene = scene)
        }
    }

    internal fun parseRepertoirePageUrls(indexUrl: String, html: String): List<String> {
        val doc = Jsoup.parse(html, baseUrl)
        return listOf(indexUrl)
            .plus(
                doc.select("a[href]")
                    .mapNotNull { link ->
                        link.absUrl("href")
                            .trim()
                            .takeIf { href -> isRepertoirePageUrl(indexUrl, href) }
                    }
            )
            .distinct()
            .sortedWith(compareBy({ pageNumber(it) }, { it }))
    }

    private fun extractTitle(link: Element): String? {
        val directTitle = link.text().normalizeWhitespace().trim()
        if (directTitle.isNotBlank()) return directTitle

        val container = findPerformanceContainer(link) ?: return null
        return container.select("h1, h2, h3, h4").firstNotNullOfOrNull { heading ->
            heading.text().normalizeWhitespace().trim().takeIf { it.isNotBlank() }
        }
    }

    private fun extractScene(link: Element): String? {
        val container = findPerformanceContainer(link) ?: return null
        return sceneRegex.find(container.text().normalizeWhitespace())?.value
    }

    private fun findPerformanceContainer(link: Element): Element? {
        return link.parents().firstOrNull { parent ->
            val text = parent.text().normalizeWhitespace()
            text.isNotBlank() && text.length <= 500 && sceneRegex.containsMatchIn(text)
        } ?: link.parents().firstOrNull { parent ->
            val headings = parent.select("h1, h2, h3, h4")
            headings.any { it.text().normalizeWhitespace().isNotBlank() } && parent.text().length <= 500
        }
    }

    private fun fetchRepertoirePageUrls(indexUrl: String): List<String> {
        val doc = loadDocument(indexUrl)
        return parseRepertoirePageUrls(indexUrl, doc.outerHtml())
    }

    private fun loadDocument(url: String): Document {
        return Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (compatible; bot)")
            .get()
    }

    private fun isRepertoirePageUrl(indexUrl: String, href: String): Boolean {
        return href.startsWith(indexUrl) && href.contains("PAGEN_")
    }

    private fun pageNumber(url: String): Int {
        return Regex("""[?&]PAGEN_\d+=(\d+)""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 1
    }

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ")
}
