import ru.tickets.scraper.LensovScraper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LensovScraperTest {
    private val scraper = LensovScraper()

    // Real HTML structure: each date has its own <div class="colorbox-items"> inside a <ul>.
    // Availability is determined by CSS class, not text:
    //   wb-button             → available ("Купить билет")
    //   wb-button waitlist    → not available ("Оставить заявку")
    //   wb-button no-tickets  → not available ("БИЛЕТЫ ПРОДАНЫ")

    private fun html(vararg items: String) = """
        <div class="restaurantBookTable">
          <div class="bookTableAside">
            <div class="availibility">
              <ul>
                ${items.joinToString("\n") { """
                  <div class="colorbox-items">$it</div>
                """.trimIndent() }}
              </ul>
            </div>
          </div>
        </div>
    """.trimIndent()

    private fun li(date: String, btnClass: String, btnText: String) = """
        <li>
          <div>$date</div>
          <div class="button button-primary wb-button $btnClass">$btnText</div>
        </li>
    """.trimIndent()

    private fun liNoButton(date: String) = "<li><div>$date</div></li>"

    @Test
    fun parseScheduleHtml_marksSlotAvailable_whenBuyTicketButton() {
        val schedules = scraper.parseScheduleHtml(
            html(li("25 апреля (сб) - 18:00", "", "Купить билет"))
        )
        assertEquals(1, schedules.size)
        assertTrue(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_marksSlotUnavailable_whenWaitlistButton() {
        val schedules = scraper.parseScheduleHtml(
            html(li("26 апреля (вс) - 18:00", "waitlist", "Оставить заявку"))
        )
        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_marksSlotUnavailable_whenNoTicketsButton() {
        val schedules = scraper.parseScheduleHtml(
            html(li("31 мая (вс) - 18:00", "no-tickets", "БИЛЕТЫ ПРОДАНЫ"))
        )
        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_marksSlotUnavailable_whenNoButtons() {
        val schedules = scraper.parseScheduleHtml(
            html(liNoButton("01 июня (пн) - 12:00"))
        )
        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_marksAllBesySlotsAvailable() {
        val schedules = scraper.parseScheduleHtml(html(
            li("25 апреля (сб) - 18:00", "", "Купить билет"),
            li("02 мая (сб) - 18:00", "", "Купить билет"),
            li("20 июня (сб) - 18:00", "", "Купить билет"),
        ))
        assertEquals(3, schedules.size)
        assertTrue(schedules.all { it.ticketsAvailable })
    }

    @Test
    fun parseScheduleHtml_marksSlotUnavailable_whenSectionTitleOnly() {
        // The h3 "Купить билет" heading must not be mistaken for an available ticket button
        val schedules = scraper.parseScheduleHtml("""
            <div>
              <h3>Купить билет</h3>
              <div class="restaurantBookTable">
                <div class="colorbox-items">
                  <li><div>26 апреля (вс) - 18:00</div></li>
                </div>
              </div>
            </div>
        """.trimIndent())
        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_marksMixedAcademySlotsCorrectly() {
        val schedules = scraper.parseScheduleHtml(html(
            li("25 апреля (сб) - 19:00", "waitlist", "Оставить заявку"),
            li("03 мая (вс) - 19:00", "waitlist", "Оставить заявку"),
            li("16 мая (сб) - 19:00", "waitlist", "Оставить заявку"),
            li("27 июня (сб) - 19:00", "", "Купить билет"),
            li("14 июля (вт) - 19:30", "", "Купить билет"),
        ))
        assertEquals(5, schedules.size)
        assertEquals(
            listOf(false, false, false, true, true),
            schedules.map { it.ticketsAvailable }
        )
    }
}
