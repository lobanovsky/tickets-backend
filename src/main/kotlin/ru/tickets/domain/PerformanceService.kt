package ru.tickets.domain

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import ru.tickets.db.schema.PaidSubscriptions
import ru.tickets.db.schema.Performances
import ru.tickets.db.schema.Subscriptions
import ru.tickets.db.schema.Theatres
import ru.tickets.db.schema.Users
import ru.tickets.models.responses.PerformanceWithStatusResponse
import java.time.LocalDate
import java.util.*

data class PerformanceRow(val id: UUID, val theatreId: UUID, val title: String, val url: String, val scene: String?, val isActive: Boolean = true, val ticketsAvailable: Boolean = false)

class PerformanceService(private val database: Database) {

    private val logger = LoggerFactory.getLogger(PerformanceService::class.java)

    suspend fun findTheatreIdBySlug(slug: String): UUID? = dbQuery(database) {
        Theatres.selectAll().where { Theatres.slug eq slug }.singleOrNull()?.get(Theatres.id)
    }

    suspend fun findByTheatreWithSubscriptionStatus(
        theatreSlug: String,
        telegramId: Long?
    ): List<PerformanceWithStatusResponse> = dbQuery(database) {
        logger.info("performances DB transaction started: theatre=$theatreSlug")
        var queryStartedAt = System.nanoTime()
        val theatreRow = Theatres.selectAll().where { Theatres.slug eq theatreSlug }.singleOrNull()
        logger.info(
            "performances DB query completed: stage=theatre, theatre=$theatreSlug, found=${theatreRow != null}, durationMs=${elapsedMs(queryStartedAt)}"
        )
        if (theatreRow == null) return@dbQuery emptyList()
        val theatreId = theatreRow[Theatres.id]

        val subscribedPerformanceIds: Set<UUID> = if (telegramId != null) {
            queryStartedAt = System.nanoTime()
            val userRow = Users.selectAll().where { Users.telegramId eq telegramId }.singleOrNull()
            logger.info(
                "performances DB query completed: stage=user, theatre=$theatreSlug, found=${userRow != null}, durationMs=${elapsedMs(queryStartedAt)}"
            )
            if (userRow != null) {
                val userId = userRow[Users.id]
                queryStartedAt = System.nanoTime()
                val ids = Subscriptions.selectAll()
                    .where { (Subscriptions.userId eq userId) and (Subscriptions.isActive eq true) }
                    .map { it[Subscriptions.performanceId] }
                logger.info(
                    "performances DB query completed: stage=subscriptions, theatre=$theatreSlug, count=${ids.size}, durationMs=${elapsedMs(queryStartedAt)}"
                )
                ids.toSet()
            } else emptySet()
        } else emptySet()

        queryStartedAt = System.nanoTime()
        val result = Performances.selectAll()
            .where { (Performances.theatreId eq theatreId) and (Performances.isActive eq true) }
            .orderBy(Performances.title, SortOrder.ASC)
            .map { row ->
                val perfId = row[Performances.id]
                PerformanceWithStatusResponse(
                    id = perfId.toString(),
                    theatreId = theatreId.toString(),
                    title = row[Performances.title],
                    url = row[Performances.url],
                    scene = row[Performances.scene],
                    isSubscribed = perfId in subscribedPerformanceIds
                )
            }
        logger.info(
            "performances DB query completed: stage=performances, theatre=$theatreSlug, count=${result.size}, durationMs=${elapsedMs(queryStartedAt)}"
        )
        result
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    suspend fun findWithActiveSubscribers(theatreId: UUID): List<PerformanceRow> = dbQuery(database) {
        val today = LocalDate.now()
        Performances
            .join(Subscriptions, JoinType.INNER, Performances.id, Subscriptions.performanceId)
            .select(Performances.id, Performances.theatreId, Performances.title, Performances.url, Performances.scene, Performances.isActive, Performances.ticketsAvailable)
            .where {
                (Performances.theatreId eq theatreId) and
                (Subscriptions.isActive eq true) and
                (Performances.isActive eq true) and
                (Subscriptions.userId inSubQuery PaidSubscriptions
                    .select(PaidSubscriptions.userId)
                    .where {
                        (PaidSubscriptions.isActive eq true) and
                        (PaidSubscriptions.endDate greaterEq today)
                    })
            }
            .groupBy(Performances.id, Performances.theatreId, Performances.title, Performances.url, Performances.scene, Performances.isActive, Performances.ticketsAvailable)
            .map { row ->
                PerformanceRow(
                    id = row[Performances.id],
                    theatreId = row[Performances.theatreId],
                    title = row[Performances.title],
                    url = row[Performances.url],
                    scene = row[Performances.scene],
                    isActive = row[Performances.isActive],
                    ticketsAvailable = row[Performances.ticketsAvailable]
                )
            }
    }

    suspend fun updateTicketsAvailable(performanceId: UUID, available: Boolean, summary: String? = null) = dbQuery(database) {
        Performances.update({ Performances.id eq performanceId }) {
            it[ticketsAvailable] = available
            if (summary != null) it[lastScheduleSummary] = summary
        }
    }

    fun upsertPerformances(theatreId: UUID, scraped: List<ScrapedPerformance>) {
        transaction(database) {
            for (perf in scraped) {
                val existing = Performances.selectAll()
                    .where { (Performances.theatreId eq theatreId) and (Performances.url eq perf.url) }
                    .singleOrNull()
                if (existing == null) {
                    Performances.insert {
                        it[Performances.theatreId] = theatreId
                        it[title] = perf.title
                        it[url] = perf.url
                        it[scene] = perf.scene
                        it[isActive] = true
                    }
                } else {
                    Performances.update({ (Performances.theatreId eq theatreId) and (Performances.url eq perf.url) }) {
                        it[title] = perf.title
                        it[scene] = perf.scene
                        it[isActive] = true
                    }
                }
            }

            val scrapedUrls = scraped.map { it.url }
            if (scrapedUrls.isNotEmpty()) {
                Performances.update({
                    (Performances.theatreId eq theatreId) and
                    (Performances.url notInList scrapedUrls) and
                    (Performances.isActive eq true)
                }) { it[isActive] = false }
            }
        }
    }

}

data class ScrapedPerformance(val title: String, val url: String, val scene: String? = null)
