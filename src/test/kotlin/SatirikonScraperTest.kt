import ru.tickets.scraper.SatirikonScraper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SatirikonScraperTest {
    private val scraper = SatirikonScraper()

    // --- parseRepertoireHtml ---

    @Test
    fun parseRepertoireHtml_extractsShowsFromH3Links() {
        val html = """
            <html><body>
              <ul>
                <li>
                  <h3><a href="/spektakli/repertuar/dubrovskiy/">Дубровский</a></h3>
                </li>
                <li>
                  <h3><a href="/spektakli/repertuar/blizkie-druzya/">Близкие друзья</a></h3>
                </li>
              </ul>
            </body></html>
        """.trimIndent()
        val performances = scraper.parseRepertoireHtml(html)
        assertEquals(2, performances.size)
        assertEquals("Дубровский", performances[0].title)
        assertTrue(performances[0].url.endsWith("/spektakli/repertuar/dubrovskiy/"))
        assertEquals("Близкие друзья", performances[1].title)
    }

    @Test
    fun parseRepertoireHtml_skipsRepertoireRootLink() {
        val html = """
            <html><body>
              <nav><a href="/spektakli/repertuar/">Репертуар</a></nav>
              <h3><a href="/spektakli/repertuar/spektakl-1/">Спектакль 1</a></h3>
            </body></html>
        """.trimIndent()
        val performances = scraper.parseRepertoireHtml(html)
        assertEquals(1, performances.size)
        assertEquals("Спектакль 1", performances[0].title)
    }

    @Test
    fun parseRepertoireHtml_deduplicatesUrls() {
        val html = """
            <html><body>
              <h3><a href="/spektakli/repertuar/dubrovskiy/">Дубровский</a></h3>
              <h3><a href="/spektakli/repertuar/dubrovskiy/">Дубровский</a></h3>
            </body></html>
        """.trimIndent()
        val performances = scraper.parseRepertoireHtml(html)
        assertEquals(1, performances.size)
    }

    @Test
    fun parseRepertoireHtml_returnsEmptyForNoShows() {
        val html = "<html><body><p>Нет спектаклей</p></body></html>"
        assertTrue(scraper.parseRepertoireHtml(html).isEmpty())
    }

    @Test
    fun parseRepertoireHtml_returnsEmptyWhenOnlyPaginationLinksPresent() {
        // Страница с PAGEN_1 ссылками, но без h3>a спектаклей — сигнал "стоп" для цикла
        val html = """
            <html><body>
              <nav><a href="?PAGEN_1=2">2</a><a href="?PAGEN_1=3">3</a></nav>
            </body></html>
        """.trimIndent()
        assertTrue(scraper.parseRepertoireHtml(html).isEmpty())
    }

    // --- parseScheduleHtml ---

    private fun scheduleHtml(vararg items: String) = """
        <html><body>
          <div class="schedule-section">
            ${items.joinToString("\n")}
          </div>
        </body></html>
    """.trimIndent()

    private fun slideWithButton(date: String, time: String, eventId: Int) = """
        <div class="swiper-slide">
          <div class="event-date">$date</div>
          <div class="event-time">$time</div>
          <button onclick="afishaWidget.openModal({ event_id: $eventId })">Купить билет</button>
        </div>
    """.trimIndent()

    private fun slideNoButton(date: String, time: String) = """
        <div class="swiper-slide">
          <div class="event-date">$date</div>
          <div class="event-time">$time</div>
        </div>
    """.trimIndent()

    private fun slideWithLink(date: String, time: String, eventId: Int) = """
        <div class="swiper-slide">
          <div class="event-date">$date</div>
          <div class="event-time">$time</div>
          <a href="#" onclick="afishaWidget.openModal({ event_id: $eventId })">Купить билет</a>
        </div>
    """.trimIndent()

    @Test
    fun parseScheduleHtml_extractsDateAndTimeFromSlideWithButton() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(slideWithButton("15.05", "19:00", 1001))
        )
        assertEquals(1, schedules.size)
        assertEquals("15.05", schedules[0].date)
        assertEquals("19:00", schedules[0].time)
        assertTrue(schedules[0].ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_returnsEmptyWhenNoButtons() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(slideNoButton("20.05", "19:00"))
        )
        assertTrue(schedules.isEmpty())
    }

    @Test
    fun parseScheduleHtml_extractsMultipleSlides() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(
                slideWithButton("15.05", "19:00", 1001),
                slideWithButton("22.05", "18:00", 1002),
                slideWithButton("29.05", "20:00", 1003),
            )
        )
        assertEquals(3, schedules.size)
        assertTrue(schedules.all { it.ticketsAvailable })
        assertEquals(listOf("15.05", "22.05", "29.05"), schedules.map { it.date })
    }

    @Test
    fun parseScheduleHtml_extractsDateAndTimeFromLinkWithOnclick() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(slideWithLink("18.06", "20:00", 2001))
        )
        assertEquals(1, schedules.size)
        assertEquals("18.06", schedules[0].date)
        assertEquals("20:00", schedules[0].time)
        assertTrue(schedules[0].ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_extractsMixedOnclickElements() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(
                slideWithButton("15.05", "19:00", 1001),
                slideWithLink("18.06", "20:00", 2001),
            )
        )
        assertEquals(2, schedules.size)
        assertEquals(listOf("15.05", "18.06"), schedules.map { it.date })
        assertTrue(schedules.all { it.ticketsAvailable })
    }

    @Test
    fun parseScheduleHtml_marksAllEntriesAvailable() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(
                slideWithButton("15.05", "19:00", 1001),
                slideWithButton("22.05", "18:00", 1002),
            )
        )
        assertTrue(schedules.all { it.ticketsAvailable })
    }
}
