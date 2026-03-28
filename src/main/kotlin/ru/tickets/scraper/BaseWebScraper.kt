package ru.tickets.scraper

import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions

abstract class BaseWebScraper : WebScraper {

    protected fun fetchHtmlWithSelenium(url: String): String? {
        val driver = createDriver()
        return try {
            driver[url]
            driver.pageSource
        } finally {
            driver.quit()
        }
    }

    private fun createDriver(): WebDriver {
        val chromedriverPath = System.getenv("CHROMEDRIVER_PATH")
        if (!chromedriverPath.isNullOrBlank()) {
            System.setProperty("webdriver.chrome.driver", chromedriverPath)
        }
        val options = ChromeOptions().apply {
            addArguments("--headless=new")
            addArguments("--disable-gpu")
            addArguments("--no-sandbox")
            addArguments("--disable-setuid-sandbox")
            addArguments("--disable-dev-shm-usage")
            addArguments("--no-zygote")
        }
        return ChromeDriver(options)
    }
}
