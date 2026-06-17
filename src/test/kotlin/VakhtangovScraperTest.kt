import ru.tickets.scraper.VakhtangovScraper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VakhtangovScraperTest {
    private val scraper = VakhtangovScraper()

    @Test
    fun parseScheduleHtml_marksSlotsUnavailableWhenTicketlandEntryMissing() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(
                slot("21 июня,", "воскресенье,", "19:00", "main", "2026-06-21-19-00-00"),
                slot("2 сентября,", "среда,", "19:00", "main", "2026-09-02-19-00-00"),
                slot("8 сентября,", "вторник,", "19:00", "main", "2026-09-08-19-00-00"),
                slot("30 сентября,", "среда,", "19:00", "main", "2026-09-30-19-00-00"),
            ),
            ticketlandAvailability = mapOf("main" to emptyMap())
        )

        assertEquals(4, schedules.size)
        assertEquals(listOf("21 июня", "2 сентября", "8 сентября", "30 сентября"), schedules.map { it.date })
        assertEquals(listOf("19:00", "19:00", "19:00", "19:00"), schedules.map { it.time })
        assertTrue(schedules.none { it.ticketsAvailable })
    }

    @Test
    fun parseScheduleHtml_marksSlotAvailableWhenTicketlandHasTickets() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(slot("21 июня,", "воскресенье,", "19:00", "main", "2026-06-21-19-00-00")),
            ticketlandAvailability = mapOf(
                "main" to mapOf("2026-06-21-19-00-00" to true)
            )
        )

        assertEquals(1, schedules.size)
        assertTrue(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_marksSlotUnavailableWhenTicketlandHasNoTickets() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(slot("21 июня,", "воскресенье,", "19:00", "main", "2026-06-21-19-00-00")),
            ticketlandAvailability = mapOf(
                "main" to mapOf("2026-06-21-19-00-00" to false)
            )
        )

        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_fallsBackToHrefWhenDataAttributesAreMalformed() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(
                slotWithMalformedDataAttributes(
                    "21 июня,",
                    "воскресенье,",
                    "19:00",
                    "main",
                    "2026-06-21-19-00-00"
                )
            ),
            ticketlandAvailability = mapOf(
                "main" to mapOf("2026-06-21-19-00-00" to true)
            )
        )

        assertEquals(1, schedules.size)
        assertTrue(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseTicketlandAvailability_decodesDataJsonPayload() {
        val availability = scraper.parseTicketlandAvailability(
            """
            {
              "createdAt": "2026-06-17T22:12:46+03:00",
              "data": "{\"main\":{\"2026-06-21-19-00-00\":{\"has_tickets\":true},\"2026-09-02-19-00-00\":{\"has_tickets\":false}}}"
            }
            """.trimIndent()
        )

        assertTrue(availability.getValue("main").getValue("2026-06-21-19-00-00"))
        assertFalse(availability.getValue("main").getValue("2026-09-02-19-00-00"))
    }

    @Test
    fun parseTicketlandAvailability_decodesScriptPhpPayload() {
        val availability = scraper.parseTicketlandAvailability(
            """
            "{\"main\":{\"2026-06-21-19-00-00\":{\"has_tickets\":true}}}"
            """.trimIndent()
        )

        assertTrue(availability.getValue("main").getValue("2026-06-21-19-00-00"))
    }

    private fun scheduleHtml(vararg slots: String): String = """
        <html><body>
          <ul class="show-afisha">
            ${slots.joinToString("\n")}
          </ul>
        </body></html>
    """.trimIndent()

    private fun slot(date: String, weekday: String, time: String, stage: String, datetime: String): String = """
        <li>
          <p class="info">
            <span class="date">
              <span class="date">$date</span>
              <span class="weekday">$weekday</span>
              <span class="time">$time</span>
            </span>
          </p>
          <ul class="btn-list">
            <li>
              <a href="/tickets/buy/?stageuid=$stage&datetime=$datetime"
                 class="btn btn-primary js-buy-tickets-btn"
                 data-stage="$stage"
                 data-datetime="$datetime">Купить билеты</a>
            </li>
          </ul>
        </li>
    """.trimIndent()

    private fun slotWithMalformedDataAttributes(
        date: String,
        weekday: String,
        time: String,
        stage: String,
        datetime: String
    ): String = """
        <li>
          <p class="info">
            <span class="date">
              <span class="date">$date</span>
              <span class="weekday">$weekday</span>
              <span class="time">$time</span>
            </span>
          </p>
          <ul class="btn-list">
            <li>
              <a href="/tickets/buy/?stageuid=$stage&datetime=$datetime"
                 class="btn btn-primary js-buy-tickets-btn"
                 data-stage="$stage"data-datetime="$datetime">Купить билеты</a>
            </li>
          </ul>
        </li>
    """.trimIndent()
}
