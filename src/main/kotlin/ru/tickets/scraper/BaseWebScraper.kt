package ru.tickets.scraper

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.WaitForSelectorState
import com.microsoft.playwright.options.WaitUntilState

abstract class BaseWebScraper : WebScraper {

    protected fun fetchHtmlWithPlaywright(
        url: String,
        waitUntil: WaitUntilState = WaitUntilState.DOMCONTENTLOADED,
        waitForSelector: String? = null,
        navigationTimeoutMs: Double = 60_000.0,
        selectorTimeoutMs: Double = 15_000.0
    ): String? {
        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(
                BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(listOf("--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage", "--no-zygote"))
            )
            browser.use {
                val page = browser.newPage()
                val blockedTypes = setOf("image", "media", "font")
                page.route("**/*") { route ->
                    if (route.request().resourceType() in blockedTypes) route.abort()
                    else route.resume()
                }
                page.navigate(url, Page.NavigateOptions()
                    .setWaitUntil(waitUntil)
                    .setTimeout(navigationTimeoutMs))
                if (waitForSelector != null) {
                    try {
                        page.waitForSelector(waitForSelector, Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.ATTACHED)
                            .setTimeout(selectorTimeoutMs))
                    } catch (_: Exception) {
                        // element not present on this page — return content as-is (0 slots)
                    }
                }
                return page.content()
            }
        }
    }
}
