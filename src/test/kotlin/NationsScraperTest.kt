import ru.tickets.scraper.NationsPlace
import ru.tickets.scraper.NationsScraper
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NationsScraperTest {
    private val scraper = NationsScraper()

    @Test
    fun parseEventIdentity_extractsHashAndNombilkn() {
        val identity = scraper.parseEventIdentity(
            """
            <script>
                var event_hash = "0f8d78def3c6d8cfe75f3fae455546f9";
                var nombilkn = "6635";
            </script>
            """.trimIndent()
        )

        assertNotNull(identity)
        assertEquals("0f8d78def3c6d8cfe75f3fae455546f9", identity.eventHash)
        assertEquals("6635", identity.nombilkn)
    }

    @Test
    fun parseAvailablePlacesJson_extractsAvailablePlaces() {
        val places = scraper.parseAvailablePlacesJson(
            """
            {
              "EvailPlaceList": [
                {"name_sec": "Партер", "row": "3", "seat": "7", "PriceSell": "6500.00"},
                {"name_sec": "Балкон (неудобное место)", "row": "6", "seat": "1", "PriceSell": "1000.00"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, places.size)
        assertEquals("Партер", places[0].section)
        assertEquals("3", places[0].row)
        assertEquals("7", places[0].seat)
        assertEquals("6500", places[0].price.toPlainString())
    }

    @Test
    fun parseScheduleHtml_marksBuyButtonUnavailableWhenPlacesAreEmpty() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(
                """
                <a class="btn" href="/event/empty/">Купить билет</a>
                """.trimIndent()
            )
        ) { emptyList() }

        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
        assertEquals(null, schedules.single().details)
    }

    @Test
    fun parseScheduleHtml_marksBuyButtonAvailableWhenPlacesExist() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(
                """
                <a class="btn" href="/event/with-places/">Купить билет</a>
                """.trimIndent()
            )
        ) { eventUrl ->
            assertEquals("https://theatreofnations.ru/event/with-places/", eventUrl)
            listOf(
                NationsPlace("Партер", "3", "7", BigDecimal("6500")),
                NationsPlace("Партер", "3", "8", BigDecimal("6500"))
            )
        }

        assertEquals(1, schedules.size)
        assertTrue(schedules.single().ticketsAvailable)
        assertEquals(
            "• Чт 30 апр 20:00: мест 2, цены 6500 руб.\n" +
                "Партер, ряд 3, место 7 - 6500 руб.; Партер, ряд 3, место 8 - 6500 руб.",
            schedules.single().details
        )
    }

    @Test
    fun buildPlacesSummary_limitsExamplesAndShowsPriceRange() {
        val summary = scraper.buildPlacesSummary(
            date = "Чт 07 май",
            time = "19:00",
            places = listOf(
                NationsPlace("Партер", "1", "7", BigDecimal("18000")),
                NationsPlace("Балкон", "2", "3", BigDecimal("3000")),
                NationsPlace("Балкон", "2", "4", BigDecimal("3000")),
                NationsPlace("Балкон", "4", "15", BigDecimal("1500")),
                NationsPlace("Балкон (неудобное место)", "6", "1", BigDecimal("1000")),
                NationsPlace("Балкон (неудобное место)", "6", "2", BigDecimal("1000"))
            )
        )

        assertTrue(summary.startsWith("• Чт 07 май 19:00: мест 6, цены 1000-18000 руб."))
        assertTrue(summary.contains("Балкон (неудобное место), ряд 6, место 1 - 1000 руб."))
        assertTrue(summary.endsWith("; и еще 1"))
    }

    private fun scheduleHtml(buttonHtml: String): String = """
        <div class="play-info__meta-item">
          <span>Чт 30 апр - 20:00</span>
          <span>Малая сцена</span>
          $buttonHtml
        </div>
    """.trimIndent()
}
