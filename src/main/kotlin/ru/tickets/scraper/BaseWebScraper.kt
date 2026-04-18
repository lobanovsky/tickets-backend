package ru.tickets.scraper

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright

abstract class BaseWebScraper : WebScraper {

    protected fun fetchHtmlWithSelenium(url: String): String? {
        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(
                BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(listOf("--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage", "--no-zygote"))
            )
            browser.use {
                val page = browser.newPage()
                page.navigate(url)
                return page.content()
            }
        }
    }
}
