package ru.tickets.scraper

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import ru.tickets.domain.ScrapedPerformance
import java.time.LocalDate

class MxtScraper : BaseWebScraper() {

    override val theatreSlug = "mxt"
    private val log = LoggerFactory.getLogger(MxtScraper::class.java)
    private val baseUrl = "https://mxat.ru"
    private val repertoireUrls = listOf("$baseUrl/repertuar/current/", "$baseUrl/repertuar/soon/")
    private val performanceLinkSelector = "a[href*=/repertuar/show/]"
    private val sceneRegex = Regex("(Основная сцена|Малая сцена|Новая сцена|Дворец на Яузе|Театриум на Серпуховке|Портретное фойе)")
    private val buyTicketLabels = setOf("купить билет", "билеты")
    private val unavailableTicketLabels = setOf("оставить заявку", "заявка")

    override fun scrapeRepertoire(): List<ScrapedPerformance> {
        val performances = mutableListOf<ScrapedPerformance>()
        val seen = mutableSetOf<String>()
        for (repertoireUrl in repertoireUrls) {
            try {
                val pageUrls = fetchRepertoirePageUrls(repertoireUrl)
                log.info("[mxt] Для $repertoireUrl найдено ${pageUrls.size} страниц репертуара")

                pageUrls.forEach { pageUrl ->
                    val html = loadDocument(pageUrl).outerHtml()
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

    override fun scrapeSchedule(performanceUrl: String): List<ScrapedSchedule>? {
        return try {
            val schedules = parseScheduleHtml(loadDocument(performanceUrl).outerHtml()) ?: return null
            if (schedules.isEmpty()) log.warn("[mxt] Расписание не найдено для $performanceUrl")
            schedules
        } catch (e: Exception) {
            log.error(
                "[mxt] Ошибка при загрузке или парсинге расписания $performanceUrl " +
                    "(${e.javaClass.simpleName}): ${e.message}",
                e
            )
            null
        }
    }

    internal fun parseScheduleHtml(html: String): List<ScrapedSchedule>? {
        val doc = Jsoup.parse(html)
        if (!isPerformancePage(doc)) {
            log.warn("[mxt] Получена нераспознанная страница вместо страницы спектакля")
            return null
        }

        val ticketsSection = doc.getElementById("tickets") ?: return emptyList()
        val timeElements = ticketsSection.select("time[datetime]")
        if (timeElements.isEmpty()) {
            log.warn("[mxt] Найдена секция расписания без элементов time[datetime]")
            return null
        }

        val schedules = mutableListOf<ScrapedSchedule>()
        for (timeEl in timeElements) {
            val datetime = timeEl.attr("datetime").trim() // "2026-05-14 19:00"
            val eventDate = runCatching { LocalDate.parse(datetime.substringBefore(" ")) }.getOrNull()
            if (eventDate == null) {
                log.warn("[mxt] Не удалось распознать дату показа: '$datetime'")
                return null
            }
            if (eventDate.isBefore(LocalDate.now())) continue

            val container = timeEl.parent()
            if (container == null) {
                log.warn("[mxt] Не найден контейнер показа для '$datetime'")
                return null
            }
            val timeStr = datetime.substringAfter(" ", "")
            val dateStr = timeEl.select("span").firstOrNull { it.attr("aria-hidden") != "true" }
                ?.text()?.trim() ?: datetime.substringBefore(" ")

            val ticketsAvailable = parseAvailability(container, datetime) ?: return null

            schedules.add(ScrapedSchedule(date = dateStr, time = timeStr, ticketsAvailable = ticketsAvailable))
        }
        return schedules
    }

    private fun isPerformancePage(doc: Document): Boolean {
        val pageUrl = doc.selectFirst("meta[property=og:url]")?.attr("content").orEmpty()
        val title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        return pageUrl.contains("/repertuar/show/") && title.isNotBlank()
    }

    private fun parseAvailability(container: Element, datetime: String): Boolean? {
        val controls = container.select(
            "button, a[data-tickets-button], a[href*=ticket], a[href*=bilet]"
        )
        if (controls.isEmpty()) return false

        if (controls.any { control ->
                val text = control.text().normalizeWhitespace().lowercase()
                unavailableTicketLabels.any { label -> text.contains(label) }
            }
        ) {
            return false
        }

        if (controls.any { control ->
                val text = control.text().normalizeWhitespace().lowercase()
                buyTicketLabels.any { label -> text.contains(label) } ||
                    control.attr("onclick").contains("sessionId")
            }
        ) {
            return true
        }

        log.warn("[mxt] Не удалось распознать билетный элемент для '$datetime': ${controls.text().take(200)}")
        return null
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
            .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/126 Safari/537.36")
            .timeout(15_000)
            .maxBodySize(5 * 1024 * 1024)
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
