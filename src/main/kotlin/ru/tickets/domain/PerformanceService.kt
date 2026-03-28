package ru.tickets.domain

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.tickets.db.schema.Performances
import ru.tickets.db.schema.Subscriptions
import ru.tickets.db.schema.Theatres
import ru.tickets.db.schema.Users
import ru.tickets.models.responses.PerformanceResponse
import ru.tickets.models.responses.PerformanceWithStatusResponse
import java.util.*

data class PerformanceRow(val id: UUID, val theatreId: UUID, val title: String, val url: String, val scene: String?)

class PerformanceService(private val database: Database) {

    suspend fun findTheatreIdBySlug(slug: String): UUID? = dbQuery(database) {
        Theatres.selectAll().where { Theatres.slug eq slug }.singleOrNull()?.get(Theatres.id)
    }

    suspend fun findByTheatreWithSubscriptionStatus(
        theatreSlug: String,
        telegramId: Long?
    ): List<PerformanceWithStatusResponse> = dbQuery(database) {
        val theatreRow = Theatres.selectAll().where { Theatres.slug eq theatreSlug }.singleOrNull()
            ?: return@dbQuery emptyList()
        val theatreId = theatreRow[Theatres.id]

        val subscribedPerformanceIds: Set<UUID> = if (telegramId != null) {
            val userRow = Users.selectAll().where { Users.telegramId eq telegramId }.singleOrNull()
            if (userRow != null) {
                val userId = userRow[Users.id]
                Subscriptions.selectAll()
                    .where { (Subscriptions.userId eq userId) and (Subscriptions.isActive eq true) }
                    .map { it[Subscriptions.performanceId] }
                    .toSet()
            } else emptySet()
        } else emptySet()

        Performances.selectAll()
            .where { Performances.theatreId eq theatreId }
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
    }

    suspend fun findWithActiveSubscribers(theatreId: UUID): List<PerformanceRow> = dbQuery(database) {
        Performances
            .join(Subscriptions, JoinType.INNER, Performances.id, Subscriptions.performanceId)
            .select(Performances.id, Performances.theatreId, Performances.title, Performances.url, Performances.scene)
            .where { (Performances.theatreId eq theatreId) and (Subscriptions.isActive eq true) }
            .groupBy(Performances.id, Performances.theatreId, Performances.title, Performances.url, Performances.scene)
            .map { row ->
                PerformanceRow(
                    id = row[Performances.id],
                    theatreId = row[Performances.theatreId],
                    title = row[Performances.title],
                    url = row[Performances.url],
                    scene = row[Performances.scene]
                )
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
                    }
                } else {
                    Performances.update({ (Performances.theatreId eq theatreId) and (Performances.url eq perf.url) }) {
                        it[title] = perf.title
                        it[scene] = perf.scene
                    }
                }
            }
        }
    }

    fun toResponse(row: PerformanceRow): PerformanceResponse = PerformanceResponse(
        id = row.id.toString(),
        theatreId = row.theatreId.toString(),
        title = row.title,
        url = row.url,
        scene = row.scene
    )
}

data class ScrapedPerformance(val title: String, val url: String, val scene: String? = null)
