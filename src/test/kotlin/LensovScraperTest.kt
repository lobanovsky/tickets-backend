import ru.tickets.scraper.LensovScraper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LensovScraperTest {
    private val scraper = LensovScraper()

    @Test
    fun parseScheduleHtml_marksSlotAvailable_whenBuyTicketButtonExists() {
        val schedules = scraper.parseScheduleHtml(
            """
            <div class="restaurantBookTable">
              <ul class="colorbox-items">
                <li>
                  <div>09 мая (сб) - 12:00</div>
                  <div class="wb-button-root button button-primary" data-performance_id="20819949">
                    Купить билет
                  </div>
                </li>
              </ul>
            </div>
            """.trimIndent()
        )

        assertEquals(1, schedules.size)
        assertTrue(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_marksSlotUnavailable_whenOnlyLeaveRequestButtonExists() {
        val schedules = scraper.parseScheduleHtml(
            """
            <div class="restaurantBookTable">
              <ul class="colorbox-items">
                <li>
                  <div>26 апреля (вс) - 18:00</div>
                  <div class="wb-button-root button button-primary">
                    Оставить заявку
                  </div>
                </li>
              </ul>
            </div>
            """.trimIndent()
        )

        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_marksSlotUnavailable_whenOnlyPurchaseSectionTitleExists() {
        val schedules = scraper.parseScheduleHtml(
            """
            <div>
              <h3>Купить билет</h3>
              <div class="restaurantBookTable">
                <ul class="colorbox-items">
                  <li>
                    <div>26 апреля (вс) - 18:00</div>
                  </li>
                </ul>
              </div>
            </div>
            """.trimIndent()
        )

        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
    }
}
