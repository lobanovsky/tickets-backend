package ru.tickets.domain

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import ru.tickets.db.schema.Performances
import ru.tickets.db.schema.Subscriptions
import ru.tickets.db.schema.Theatres
import ru.tickets.models.responses.StatsResponse
import ru.tickets.models.responses.TheatreResponse
import ru.tickets.models.responses.TheatreStats
import java.util.*

class TheatreService(private val database: Database) {

    suspend fun findAll(): List<TheatreResponse> = dbQuery(database) {
        Theatres.selectAll().map { row ->
            TheatreResponse(
                id = row[Theatres.id].toString(),
                slug = row[Theatres.slug],
                name = row[Theatres.name],
                websiteUrl = row[Theatres.websiteUrl]
            )
        }
    }

    suspend fun findIdBySlug(slug: String): UUID? = dbQuery(database) {
        Theatres.selectAll().where { Theatres.slug eq slug }.singleOrNull()?.get(Theatres.id)
    }

    suspend fun getStats(): StatsResponse = dbQuery(database) {
        val totalUsers = ru.tickets.db.schema.Users.selectAll().count()
        val activeUsers = ru.tickets.db.schema.Users.selectAll()
            .where { ru.tickets.db.schema.Users.isActive eq true }.count()
        val totalSubscriptions = Subscriptions.selectAll().count()
        val activeSubscriptions = Subscriptions.selectAll()
            .where { Subscriptions.isActive eq true }.count()
        val pendingNotifications = ru.tickets.db.schema.PendingNotifications.selectAll()
            .where { ru.tickets.db.schema.PendingNotifications.sentAt.isNull() }.count()

        val byTheatre = Theatres.selectAll().map { theatreRow ->
            val theatreId = theatreRow[Theatres.id]
            val performanceCount = Performances.selectAll()
                .where { Performances.theatreId eq theatreId }.count()
            val activeSubCount = Subscriptions
                .join(Performances, org.jetbrains.exposed.sql.JoinType.INNER, Subscriptions.performanceId, Performances.id)
                .selectAll()
                .where { (Performances.theatreId eq theatreId) and (Subscriptions.isActive eq true) }
                .count()
            TheatreStats(
                slug = theatreRow[Theatres.slug],
                name = theatreRow[Theatres.name],
                performanceCount = performanceCount,
                activeSubscriptionCount = activeSubCount
            )
        }

        StatsResponse(
            totalUsers = totalUsers,
            activeUsers = activeUsers,
            totalSubscriptions = totalSubscriptions,
            activeSubscriptions = activeSubscriptions,
            pendingNotifications = pendingNotifications,
            byTheatre = byTheatre
        )
    }
}
