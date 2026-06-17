package ru.tickets.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import ru.tickets.domain.ScrapedPerformance

class VakhtangovScraper : BaseWebScraper() {

    override val theatreSlug = "vakhtangov"
    private val log = LoggerFactory.getLogger(VakhtangovScraper::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override fun scrapeRepertoire(): List<ScrapedPerformance> {
        val performances = mutableListOf<ScrapedPerformance>()
        try {
            val doc = Jsoup.connect("https://vakhtangov.ru/shows/now/").get()
            val allowedScenes = setOf("Основная сцена", "Новая сцена", "Симоновская сцена")
            for (section in doc.select("section.shows-stage")) {
                val scene = section.selectFirst("header.shows-stage-header h2")?.text() ?: continue
                if (scene !in allowedScenes) continue
                for (show in section.select("article.shows-item")) {
                    val link = show.selectFirst("a") ?: continue
                    val title = link.selectFirst("h1")?.text() ?: continue
                    val href = link.attr("href")
                    if (href.isNotBlank()) {
                        performances.add(ScrapedPerformance(title = title, url = href, scene = scene))
                    }
                }
            }
            log.info("[vakhtangov] Найдено ${performances.size} спектаклей")
        } catch (e: Exception) {
            log.error("[vakhtangov] Ошибка при парсинге репертуара: ${e.message}")
        }
        return performances
    }

    override fun scrapeSchedule(performanceUrl: String): List<ScrapedSchedule>? {
        return try {
            val html = fetchHtmlWithPlaywright(performanceUrl) ?: return null
            val ticketlandAvailability = fetchTicketlandAvailability() ?: return null
            parseScheduleHtml(html, ticketlandAvailability)
        } catch (e: Exception) {
            log.warn("[vakhtangov] Ошибка при парсинге расписания $performanceUrl: ${e.message}")
            null
        }
    }

    internal fun parseScheduleHtml(
        html: String,
        ticketlandAvailability: Map<String, Map<String, Boolean>> = emptyMap()
    ): List<ScrapedSchedule> {
        val schedules = mutableListOf<ScrapedSchedule>()
        val doc = Jsoup.parse(html)
        for (li in doc.select("ul.show-afisha > li")) {
            val dateElem = li.selectFirst("span.date > span.date")
                ?: li.selectFirst("p.info span.date")
            val rawDate = dateElem?.text()?.trim().orEmpty()
            val date = rawDate.split(",").firstOrNull()?.trim()?.removeSuffix(",").orEmpty()
            val time = li.select("span.time").firstOrNull()?.text()?.trim() ?: ""
            val ticketButton = li.select("a.js-buy-tickets-btn").firstOrNull()
            val href = ticketButton?.attr("href").orEmpty()
            val stage = ticketButton?.attr("data-stage")
                ?.takeIf { it.isNotBlank() && !it.contains("data-datetime") }
                ?: queryParam(href, "stageuid")
                ?: ""
            val datetime = ticketButton?.attr("data-datetime")
                ?.takeIf { it.isNotBlank() }
                ?: queryParam(href, "datetime")
                ?: ""
            val ticketsAvailable = ticketlandAvailability[stage]?.get(datetime) == true
            if (date.isNotBlank() && time.isNotBlank()) {
                schedules.add(ScrapedSchedule(date = date, time = time, ticketsAvailable = ticketsAvailable))
            }
        }
        return schedules
    }

    internal fun parseTicketlandAvailability(payload: String): Map<String, Map<String, Boolean>> {
        val data = extractTicketlandData(payload) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, Map<String, TicketlandEvent>>>(data)
                .mapValues { (_, events) ->
                    events.mapValues { (_, event) -> event.hasTickets }
                }
        }.getOrElse { e ->
            log.warn("[vakhtangov] Не удалось распарсить данные Ticketland: ${e.message}")
            emptyMap()
        }
    }

    private fun fetchTicketlandAvailability(): Map<String, Map<String, Boolean>>? {
        var loaded = false
        val dataJson = fetchTicketlandPayload("https://vakhtangov.ru/ticketland_afisha/data.json")
        if (dataJson != null) {
            loaded = true
            val parsed = parseTicketlandAvailability(dataJson)
            if (parsed.isNotEmpty()) return parsed
        }

        val script = fetchTicketlandPayload("https://vakhtangov.ru/ticketland_afisha/script.php")
        if (script != null) {
            loaded = true
            return parseTicketlandAvailability(script)
        }
        return if (loaded) emptyMap() else null
    }

    private fun fetchTicketlandPayload(url: String): String? {
        return runCatching {
            Jsoup.connect(url)
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0 (compatible; bot)")
                .execute()
                .body()
        }.getOrElse { e ->
            log.warn("[vakhtangov] Не удалось загрузить данные Ticketland $url: ${e.message}")
            null
        }
    }

    private fun extractTicketlandData(payload: String): String? {
        val trimmed = payload.trim()
        if (trimmed.isBlank()) return null

        return runCatching {
            when (val element = json.parseToJsonElement(trimmed)) {
                is JsonObject -> {
                    val data = element["data"] as? JsonPrimitive
                    data?.contentOrNull ?: trimmed
                }
                is JsonPrimitive -> element.contentOrNull
                else -> trimmed
            }
        }.getOrElse {
            trimmed
        }
    }

    private fun queryParam(url: String, name: String): String? {
        return Regex("""[?&]$name=([^&#]+)""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
    }

    @Serializable
    private data class TicketlandEvent(
        @SerialName("has_tickets")
        val hasTickets: Boolean = false
    )
}
