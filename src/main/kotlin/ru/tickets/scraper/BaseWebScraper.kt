package ru.tickets.scraper

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.WaitUntilState

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
                page.navigate(url, Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(60_000.0))
                return page.content()
            }
        }
    }
}
