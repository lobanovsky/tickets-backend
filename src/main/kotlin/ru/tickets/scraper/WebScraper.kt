package ru.tickets.scraper

import com.microsoft.playwright.Browser
import ru.tickets.domain.ScrapedPerformance

data class ScrapedSchedule(
    val date: String,
    val time: String,
    val ticketsAvailable: Boolean
)

interface WebScraper {
    val theatreSlug: String
    fun scrapeRepertoire(): List<ScrapedPerformance>
    fun scrapeSchedule(performanceUrl: String, browser: Browser): List<ScrapedSchedule>
}
