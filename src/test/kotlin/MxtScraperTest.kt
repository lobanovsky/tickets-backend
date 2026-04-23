import ru.tickets.scraper.MxtScraper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MxtScraperTest {
    private val scraper = MxtScraper()

    @Test
    fun parseRepertoireHtml_extractsPerformancesFromCurrentPage() {
        val performances = scraper.parseRepertoireHtml(
            repertoireHtml(
                card("Основная сцена", "8 разгневанных женщин", "/repertuar/show/8_women/"),
                card("Малая сцена", "Школа для дураков", "/repertuar/show/school_for_fools/")
            )
        )

        assertEquals(2, performances.size)
        assertEquals("8 разгневанных женщин", performances[0].title)
        assertEquals("https://mxat.ru/repertuar/show/8_women/", performances[0].url)
        assertEquals("Основная сцена", performances[0].scene)
    }

    @Test
    fun parseRepertoireHtml_extractsPerformancesFromSoonPage() {
        val performances = scraper.parseRepertoireHtml(
            repertoireHtml(
                card("Новая сцена", "Вий", "/repertuar/show/viy/"),
                card("Основная сцена", "Дон Кихот", "/repertuar/show/don_quixote/")
            )
        )

        assertEquals(2, performances.size)
        assertEquals(listOf("Вий", "Дон Кихот"), performances.map { it.title })
    }

    @Test
    fun parseRepertoireHtml_deduplicatesRepeatedPerformanceLinks() {
        val performances = scraper.parseRepertoireHtml(
            repertoireHtml(
                card("Основная сцена", "Дон Кихот", "/repertuar/show/don_quixote/"),
                card("Основная сцена", "Дон Кихот", "/repertuar/show/don_quixote/")
            )
        )

        assertEquals(1, performances.size)
        assertEquals("Дон Кихот", performances.single().title)
    }

    @Test
    fun parseRepertoireHtml_allowsMissingScene() {
        val performances = scraper.parseRepertoireHtml(
            """
            <div>
              <h2><a href="/repertuar/show/no_scene/">Без сцены</a></h2>
            </div>
            """.trimIndent()
        )

        assertEquals(1, performances.size)
        assertNull(performances.single().scene)
    }

    @Test
    fun parseRepertoirePageUrls_collectsPaginationForIndexPage() {
        val pageUrls = scraper.parseRepertoirePageUrls(
            "https://mxat.ru/repertuar/current/",
            """
            <div class="pagination">
              <a href="/repertuar/current/?PAGEN_1=3">3</a>
              <a href="/repertuar/current/?PAGEN_1=2">2</a>
              <a href="/repertuar/current/?PAGEN_1=2">2</a>
              <a href="/repertuar/soon/?PAGEN_1=2">foreign</a>
            </div>
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "https://mxat.ru/repertuar/current/",
                "https://mxat.ru/repertuar/current/?PAGEN_1=2",
                "https://mxat.ru/repertuar/current/?PAGEN_1=3"
            ),
            pageUrls
        )
    }

    @Test
    fun parseScheduleHtml_extractsDatesTimesAndAvailability() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(
                slot("2026-05-17 19:00", "17 мая, Вс", "19:00", hasTickets = true),
                slot("2026-05-18 19:00", "18 мая, Пн", "19:00", hasTickets = true)
            )
        )

        assertEquals(2, schedules.size)
        assertEquals("17 мая, Вс", schedules[0].date)
        assertEquals("19:00", schedules[0].time)
        assertTrue(schedules.all { it.ticketsAvailable })
    }

    @Test
    fun parseScheduleHtml_marksScheduleUnavailableWhenNoTickets() {
        val schedules = scraper.parseScheduleHtml(
            scheduleHtml(slot("2026-06-24 19:00", "24 июн, Ср", "19:00", hasTickets = false))
        )

        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_returnsEmptyListWhenScheduleMissing() {
        val schedules = scraper.parseScheduleHtml("<div><p>О спектакле</p></div>")

        assertTrue(schedules.isEmpty())
    }

    private fun repertoireHtml(vararg cards: String): String = cards.joinToString("\n")

    private fun card(scene: String, title: String, href: String): String = """
        <div class="production-card">
          <div>$scene</div>
          <h2><a href="$href">$title</a></h2>
          <div>Купить билет</div>
        </div>
    """.trimIndent()

    private fun scheduleHtml(vararg slots: String): String = slots.joinToString("\n")

    private fun slot(datetime: String, dateDisplay: String, time: String, hasTickets: Boolean): String {
        val buttonText = if (hasTickets) "Купить билет" else "Оставить заявку"
        val mobileText = if (hasTickets) "Билеты" else "Заявка"
        return """
            <div class="grid items-center gap-x-4 grid-cols-2">
              <time datetime="$datetime">
                <span class="lg:hidden">$dateDisplay</span>
                <span class="hidden lg:inline">$dateDisplay</span>
                <span aria-hidden="true"> ∙ </span>
                <span>$time</span>
              </time>
              <div data-tickets="">
                <a data-tickets-button="" href="javascript:;">
                  <span data-tickets-desktop-button-text=""
                        data-has-tickets-text="Купить билет"
                        data-no-tickets-text="Оставить заявку">$buttonText</span>
                  <span data-tickets-mobile-button-text=""
                        data-has-tickets-text="Билеты"
                        data-no-tickets-text="Заявка">$mobileText</span>
                </a>
              </div>
            </div>
        """.trimIndent()
    }
}
