package ru.tickets.scraper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import ru.tickets.domain.ScrapedPerformance
import java.math.BigDecimal

class NationsScraper : BaseWebScraper() {

    override val theatreSlug = "nations"
    private val log = LoggerFactory.getLogger(NationsScraper::class.java)
    private val baseUrl = "https://theatreofnations.ru"
    private val json = Json { ignoreUnknownKeys = true }

    override fun scrapeRepertoire(): List<ScrapedPerformance> {
        val performances = mutableListOf<ScrapedPerformance>()
        try {
            val doc = Jsoup.connect("$baseUrl/performances/").get()
            for (link in doc.select("a.sidebar-item")) {
                val title = link.text().trim()
                val relativeUrl = link.attr("href").trim()
                if (title.isNotBlank() && relativeUrl.isNotBlank()) {
                    performances.add(ScrapedPerformance(title = title, url = "$baseUrl$relativeUrl"))
                }
            }
            log.info("[nations] Найдено ${performances.size} спектаклей")
        } catch (e: Exception) {
            log.error("[nations] Ошибка при парсинге репертуара: ${e.message}")
        }
        return performances
    }

    override fun scrapeSchedule(performanceUrl: String): List<ScrapedSchedule>? {
        return try {
            val html = fetchHtmlWithPlaywright(performanceUrl, waitForSelector = ".play-info__meta-item") ?: return null
            parseScheduleHtml(html) { eventUrl ->
                fetchAvailablePlaces(eventUrl)
            }
        } catch (e: Exception) {
            log.warn("[nations] Ошибка при парсинге расписания $performanceUrl: ${e.message}")
            null
        }
    }

    fun parseScheduleHtml(
        html: String,
        placesProvider: (String) -> List<NationsPlace>? = { emptyList() }
    ): List<ScrapedSchedule> {
        val doc = Jsoup.parse(html, baseUrl)
        return doc.select(".play-info__meta-item").mapNotNull { item ->
            parseScheduleItem(item, placesProvider)
        }.distinctBy { it.date + it.time }
    }

    private fun parseScheduleItem(
        item: Element,
        placesProvider: (String) -> List<NationsPlace>?
    ): ScrapedSchedule? {
        val spans = item.select("span")
        if (spans.size < 2) return null
        val parts = spans[0].text().trim().split(" - ")
        if (parts.size != 2) return null

        val date = parts[0].trim()
        val time = parts[1].trim()
        val buyButton = item.select("a.btn")
            .firstOrNull { it.text().contains("Купить билет", ignoreCase = true) }
            ?: return ScrapedSchedule(date = date, time = time, ticketsAvailable = false)

        val eventUrl = buyButton.absUrl("href").ifBlank { buyButton.attr("href").absoluteUrl() }
        if (eventUrl.isBlank()) {
            log.warn("[nations] Кнопка покупки без ссылки на событие: $date $time")
            return ScrapedSchedule(date = date, time = time, ticketsAvailable = false)
        }

        val places = try {
            placesProvider(eventUrl)
        } catch (e: Exception) {
            log.warn("[nations] Ошибка проверки мест $eventUrl: ${e.message}")
            null
        }

        if (places.isNullOrEmpty()) {
            return ScrapedSchedule(date = date, time = time, ticketsAvailable = false)
        }

        return ScrapedSchedule(
            date = date,
            time = time,
            ticketsAvailable = true,
            details = buildPlacesSummary(date, time, places)
        )
    }

    private fun String.absoluteUrl(): String {
        if (isBlank()) return ""
        return if (startsWith("http://") || startsWith("https://")) this else "$baseUrl$this"
    }

    private fun fetchAvailablePlaces(eventUrl: String): List<NationsPlace>? {
        val eventHtml = Jsoup.connect(eventUrl).get().html()
        val identity = parseEventIdentity(eventHtml) ?: run {
            log.warn("[nations] Не найдены event_hash/nombilkn для $eventUrl")
            return null
        }

        val tokenResponse = Jsoup.connect("$baseUrl/api/token_places/")
            .ignoreContentType(true)
            .execute()
        val token = tokenResponse.body().trim()
        if (token.isBlank()) return null

        val placesResponse = Jsoup.connect("$baseUrl/api/places/")
            .ignoreContentType(true)
            .cookies(tokenResponse.cookies())
            .header("Authorization", token)
            .data("event_hash", identity.eventHash)
            .data("cmd", "get_hall_and_places")
            .data("early_access", "")
            .data("nombilkn", identity.nombilkn)
            .execute()

        return parseAvailablePlacesJson(placesResponse.body())
    }

    fun parseEventIdentity(html: String): NationsEventIdentity? {
        val eventHash = Regex("var\\s+event_hash\\s*=\\s*\"([^\"]+)\"")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
        val nombilkn = Regex("var\\s+nombilkn\\s*=\\s*\"([^\"]+)\"")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)

        if (eventHash.isNullOrBlank() || nombilkn.isNullOrBlank()) return null
        return NationsEventIdentity(eventHash = eventHash, nombilkn = nombilkn)
    }

    fun parseAvailablePlacesJson(rawJson: String): List<NationsPlace> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        return root["EvailPlaceList"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            val section = obj.stringValue("name_sec") ?: return@mapNotNull null
            val row = obj.stringValue("row").orEmpty()
            val seat = obj.stringValue("seat").orEmpty()
            val price = obj.stringValue("PriceSell")
                ?.toBigDecimalOrNull()
                ?.stripTrailingZeros()
                ?: return@mapNotNull null

            NationsPlace(section = section, row = row, seat = seat, price = price)
        }
    }

    fun buildPlacesSummary(date: String, time: String, places: List<NationsPlace>): String {
        val sorted = places.sortedWith(compareBy<NationsPlace> { it.price }.thenBy { it.section }.thenBy { it.row.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.seat.toIntOrNull() ?: Int.MAX_VALUE })
        val prices = sorted.map { it.price }
        val minPrice = prices.minOrNull()
        val maxPrice = prices.maxOrNull()
        val priceText = if (minPrice == maxPrice) {
            "${minPrice?.rubles()} руб."
        } else {
            "${minPrice?.rubles()}-${maxPrice?.rubles()} руб."
        }
        val examples = sorted.take(5).joinToString("; ") { place ->
            buildString {
                append(place.section)
                if (place.row.isNotBlank()) append(", ряд ${place.row}")
                if (place.seat.isNotBlank()) append(", место ${place.seat}")
                append(" - ${place.price.rubles()} руб.")
            }
        }
        val more = if (sorted.size > 5) "; и еще ${sorted.size - 5}" else ""

        return buildString {
            append("• $date")
            if (time.isNotBlank()) append(" $time")
            append(": мест ${sorted.size}, цены $priceText")
            append("\n")
            append(examples)
            append(more)
        }
    }

    private fun JsonObject.stringValue(key: String): String? {
        return (this[key] as? JsonPrimitive)?.contentOrNull
    }

    private fun BigDecimal.rubles(): String = toPlainString()
}

data class NationsEventIdentity(
    val eventHash: String,
    val nombilkn: String
)

data class NationsPlace(
    val section: String,
    val row: String,
    val seat: String,
    val price: BigDecimal
)
