package ru.tickets.scraper

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitUntilState

abstract class BaseWebScraper : WebScraper {

    protected fun fetchHtmlWithSelenium(
        browser: Browser,
        url: String,
        waitUntil: WaitUntilState = WaitUntilState.DOMCONTENTLOADED
    ): String? {
        val page = browser.newPage()
        return page.use {
            page.navigate(url, Page.NavigateOptions().setWaitUntil(waitUntil).setTimeout(60_000.0))
            page.content()
        }
    }
}
