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

    @Test
    fun parseScheduleHtml_marksAllBesySlotsAvailable_whenBuyTicketButtonsExist() {
        val schedules = scraper.parseScheduleHtml(
            """
            <div class="restaurantBookTable">
              <ul class="colorbox-items">
                <li>
                  <div>25 апреля (сб) - 18:00</div>
                  <div class="wb-button-root button button-primary">Купить билет</div>
                </li>
                <li>
                  <div>02 мая (сб) - 18:00</div>
                  <div class="wb-button-root button button-primary">Купить билет</div>
                </li>
                <li>
                  <div>20 июня (сб) - 18:00</div>
                  <div class="wb-button-root button button-primary">Купить билет</div>
                </li>
              </ul>
            </div>
            """.trimIndent()
        )

        assertEquals(3, schedules.size)
        assertTrue(schedules.all { it.ticketsAvailable })
    }

    @Test
    fun parseScheduleHtml_marksSlotUnavailable_whenTicketsAreSoldOut() {
        val schedules = scraper.parseScheduleHtml(
            """
            <div class="restaurantBookTable">
              <ul class="colorbox-items">
                <li>
                  <div>31 мая (вс) - 18:00</div>
                  <div class="wb-button-root button button-primary">Билеты проданы</div>
                </li>
              </ul>
            </div>
            """.trimIndent()
        )

        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_marksSlotUnavailable_whenTicketsAreSoldOutWithTypo() {
        val schedules = scraper.parseScheduleHtml(
            """
            <div class="restaurantBookTable">
              <ul class="colorbox-items">
                <li>
                  <div>31 мая (вс) - 18:00</div>
                  <div class="wb-button-root button button-primary">Белеты проданы</div>
                </li>
              </ul>
            </div>
            """.trimIndent()
        )

        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_marksSlotUnavailable_whenPurchaseBlockHasNoButtons() {
        val schedules = scraper.parseScheduleHtml(
            """
            <div class="restaurantBookTable">
              <ul class="colorbox-items">
                <li>
                  <div>01 июня (пн) - 12:00</div>
                </li>
              </ul>
            </div>
            """.trimIndent()
        )

        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_marksMixedAcademySlotsCorrectly() {
        val schedules = scraper.parseScheduleHtml(
            """
            <div class="restaurantBookTable">
              <ul class="colorbox-items">
                <li>
                  <div>25 апреля (сб) - 19:00</div>
                  <div class="wb-button-root button button-primary">Оставить заявку</div>
                </li>
                <li>
                  <div>03 мая (вс) - 19:00</div>
                  <div class="wb-button-root button button-primary">Оставить заявку</div>
                </li>
                <li>
                  <div>16 мая (сб) - 19:00</div>
                  <div class="wb-button-root button button-primary">Оставить заявку</div>
                </li>
                <li>
                  <div>27 июня (сб) - 19:00</div>
                  <div class="wb-button-root button button-primary">Купить билет</div>
                </li>
                <li>
                  <div>14 июля (вт) - 19:30</div>
                  <div class="wb-button-root button button-primary">Купить билет</div>
                </li>
              </ul>
            </div>
            """.trimIndent()
        )

        assertEquals(5, schedules.size)
        assertEquals(
            listOf(false, false, false, true, true),
            schedules.map { it.ticketsAvailable }
        )
    }
}
