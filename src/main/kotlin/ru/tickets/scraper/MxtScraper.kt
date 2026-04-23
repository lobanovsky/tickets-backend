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
    private val ticketLinkSelector = "a[href]"
    private val sceneRegex = Regex("(Основная сцена|Малая сцена|Новая сцена|Дворец на Яузе|Театриум на Серпуховке|Портретное фойе)")
    private val scheduleRegex = Regex(
        "(\\d{1,2}\\s+[А-Яа-яЁё]+(?:,\\s*[А-Яа-яЁё]{2})?)(?:\\s+\\1)?(?:\\s*[∙·]\\s*|\\s+)?(\\d{1,2}:\\d{2})?"
    )

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
            val html = fetchHtmlWithSelenium(performanceUrl, WaitUntilState.NETWORKIDLE) ?: return emptyList()
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
        val schedules = linkedMapOf<Pair<String, String>, ScrapedSchedule>()

        doc.select(ticketLinkSelector)
            .filter { isTicketLink(it) }
            .forEach { ticketLink ->
                val scheduleContainer = findScheduleContainer(ticketLink)
                val context = scheduleContainer?.text()?.normalizeWhitespace().orEmpty()
                if (context.isBlank()) return@forEach

                val matches = scheduleRegex.findAll(context).toList()
                if (matches.isEmpty()) return@forEach

                matches.forEach matchesLoop@{ match ->
                    val date = match.groupValues[1].normalizeWhitespace().trim()
                    val time = match.groupValues.getOrNull(2)?.normalizeWhitespace()?.trim().orEmpty()
                    if (date.isBlank()) return@matchesLoop
                    val key = date to time
                    schedules.putIfAbsent(
                        key,
                        ScrapedSchedule(
                            date = date,
                            time = time,
                            ticketsAvailable = context.contains("Купить билет", ignoreCase = true)
                        )
                    )
                }
            }

        if (schedules.isEmpty()) {
            val candidateCount = doc.select(ticketLinkSelector).count { isTicketLink(it) }
            log.warn(
                "[mxt] Расписание не распознано. Найдено ссылок на билеты: $candidateCount. " +
                    "Фрагмент HTML:\n${doc.body().html().take(1500)}"
            )
        }

        return schedules.values.toList()
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

    private fun findScheduleContainer(link: Element): Element? {
        return link.parents().firstOrNull { parent ->
            val text = parent.text().normalizeWhitespace()
            text.length in 10..250 &&
                scheduleRegex.containsMatchIn(text) &&
                text.contains("билет", ignoreCase = true)
        }
    }

    private fun isTicketLink(link: Element): Boolean {
        val text = link.text().normalizeWhitespace()
        val href = link.attr("href")
        return text.contains("Купить билет", ignoreCase = true) ||
            text.equals("Билеты", ignoreCase = true) ||
            href.contains("profticket", ignoreCase = true)
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
