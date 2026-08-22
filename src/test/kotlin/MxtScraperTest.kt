import ru.tickets.scraper.MxtScraper
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun parseScheduleHtml_extractsCurrentSberAfishaMarkup() {
        val schedules = assertNotNull(scraper.parseScheduleHtml(
            scheduleHtml(
                sberSlot("${LocalDate.now().plusDays(10)} 19:00", "09 сен, Ср", hasTickets = true),
                sberSlot("${LocalDate.now().plusDays(20)} 19:00", "30 окт, Пт", hasTickets = false)
            )
        ))

        assertEquals(2, schedules.size)
        assertEquals("09 сен, Ср", schedules[0].date)
        assertEquals("19:00", schedules[0].time)
        assertTrue(schedules[0].ticketsAvailable)
        assertFalse(schedules[1].ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_supportsLegacyUnavailableButton() {
        val schedules = assertNotNull(scraper.parseScheduleHtml(
            scheduleHtml(slot("${LocalDate.now().plusDays(30)} 19:00", "24 июн, Ср", "19:00", hasTickets = false))
        ))

        assertEquals(1, schedules.size)
        assertFalse(schedules.single().ticketsAvailable)
    }

    @Test
    fun parseScheduleHtml_returnsEmptyListWhenScheduleMissing() {
        val schedules = assertNotNull(scraper.parseScheduleHtml(performanceHtml("<p>О спектакле</p>")))

        assertTrue(schedules.isEmpty())
    }

    @Test
    fun parseScheduleHtml_returnsNullForUnexpectedPage() {
        assertNull(scraper.parseScheduleHtml("<html><body>Error</body></html>"))
    }

    @Test
    fun parseScheduleHtml_returnsNullForMalformedScheduleSection() {
        assertNull(scraper.parseScheduleHtml(scheduleHtml("<p>Неизвестная разметка</p>")))
    }

    @Test
    fun parseScheduleHtml_returnsNullForUnknownTicketControl() {
        val datetime = "${LocalDate.now().plusDays(10)} 19:00"
        assertNull(
            scraper.parseScheduleHtml(
                scheduleHtml(
                    """
                    <div>
                      <time datetime="$datetime"><span>09 сен, Ср</span><span>19:00</span></time>
                      <button>Неизвестное действие</button>
                    </div>
                    """.trimIndent()
                )
            )
        )
    }

    @Test
    fun parseScheduleHtml_ignoresPastSlots() {
        val schedules = assertNotNull(scraper.parseScheduleHtml(
            scheduleHtml(
                sberSlot("${LocalDate.now().minusDays(1)} 19:00", "Вчера", hasTickets = true),
                sberSlot("${LocalDate.now().plusDays(1)} 19:00", "Завтра", hasTickets = true)
            )
        ))

        assertEquals(listOf("Завтра"), schedules.map { it.date })
    }

    private fun repertoireHtml(vararg cards: String): String = cards.joinToString("\n")

    private fun card(scene: String, title: String, href: String): String = """
        <div class="production-card">
          <div>$scene</div>
          <h2><a href="$href">$title</a></h2>
          <div>Купить билет</div>
        </div>
    """.trimIndent()

    private fun performanceHtml(body: String): String = """
        <html>
          <head><meta property="og:url" content="https://mxat.ru/repertuar/show/womb/"></head>
          <body><h1>Чрево</h1>$body</body>
        </html>
    """.trimIndent()

    private fun scheduleHtml(vararg slots: String): String = performanceHtml(
        "<div id=\"tickets\">${slots.joinToString("\n")}</div>"
    )

    private fun sberSlot(datetime: String, dateDisplay: String, hasTickets: Boolean): String {
        val ticketControl = if (hasTickets) {
            """
            <button onclick="widgetManager.appendWidget({ sessionId: 132306330 })">
              <span>Купить билет</span><span>Билеты</span>
            </button>
            """.trimIndent()
        } else {
            ""
        }
        return """
            <div class="grid items-center gap-x-4 grid-cols-2">
              <time datetime="$datetime">
                <span class="lg:hidden">$dateDisplay</span>
                <span class="hidden lg:inline">$dateDisplay</span>
                <span aria-hidden="true"> ∙ </span>
                <span>19:00</span>
              </time>
              <div>$ticketControl</div>
            </div>
        """.trimIndent()
    }

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
