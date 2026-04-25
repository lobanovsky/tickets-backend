package ru.tickets.scraper

import ru.tickets.domain.ScrapedPerformance

data class ScrapedSchedule(
    val date: String,
    val time: String,
    val ticketsAvailable: Boolean
)

interface WebScraper {
    val theatreSlug: String
    fun scrapeRepertoire(): List<ScrapedPerformance>
    fun scrapeSchedule(performanceUrl: String): List<ScrapedSchedule>?
}
