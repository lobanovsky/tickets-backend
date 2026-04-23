package ru.tickets.scraper

import com.microsoft.playwright.options.WaitUntilState
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import ru.tickets.domain.ScrapedPerformance

class MxtScraper : BaseWebScraper() {

    override val theatreSlug = "mxt"
    private val log = LoggerFactory.getLogger(MxtScraper::class.java)
    private val baseUrl = "https://mxat.ru"
    private val repertoireUrls = listOf("$baseUrl/repertuar/current/", "$baseUrl/repertuar/soon/")

    override fun scrapeRepertoire(): List<ScrapedPerformance> {
        val performances = mutableListOf<ScrapedPerformance>()
        val seen = mutableSetOf<String>()
        for (repertoireUrl in repertoireUrls) {
            try {
                val doc = Jsoup.connect(repertoireUrl)
                    .userAgent("Mozilla/5.0 (compatible; bot)")
                    .get()
                for (a in doc.select("div.performance-item h3 a[href*=/repertuar/show/]")) {
                    val title = a.text().trim().ifEmpty { continue }
                    val href = a.attr("href").trim()
                    val url = if (href.startsWith("http")) href else "$baseUrl$href"
                    val scene = a.closest("div.performance-item")
                        ?.selectFirst("div.scene-label")?.text()?.trim()
                    if (seen.add(url)) performances.add(ScrapedPerformance(title = title, url = url, scene = scene))
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
        // Ищем блоки с датами показа. Структура mxat.ru (уточнить по реальному рендеру):
        // .schedule-item или .afisha-item — контейнер одного сеанса
        // Кнопки — <a>-элементы: "Купить билет" или "Оставить заявку"
        val schedules = mutableListOf<ScrapedSchedule>()
        for (selector in listOf(".schedule-item", ".afisha-item", ".seance", ".timetable-item")) {
            val items = doc.select(selector)
            if (items.isEmpty()) continue
            for (item in items) {
                val dateText = item.select(".date, .day, time, [class*=date], [class*=day]")
                    .firstOrNull()?.text()?.trim() ?: continue
                val timeText = item.select(".time, [class*=time]").firstOrNull()?.text()?.trim() ?: ""
                val ticketsAvailable = item.select("a").any { a ->
                    a.text().contains("купить", ignoreCase = true)
                }
                schedules.add(ScrapedSchedule(date = dateText, time = timeText, ticketsAvailable = ticketsAvailable))
            }
            log.info("[mxt] Использован селектор '$selector', найдено ${schedules.size} сеансов")
            return schedules
        }
        // Если ни один селектор не сработал — логируем для отладки
        log.warn("[mxt] Ни один селектор расписания не сработал. Фрагмент HTML:\n${html.take(2000)}")
        return emptyList()
    }
}
