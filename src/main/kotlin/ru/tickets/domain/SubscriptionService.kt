package ru.tickets.domain

import org.jetbrains.exposed.sql.*
import ru.tickets.db.schema.Performances
import ru.tickets.db.schema.Subscriptions
import ru.tickets.db.schema.Theatres
import ru.tickets.db.schema.Users
import ru.tickets.models.NotFoundException
import ru.tickets.models.responses.*
import java.util.*

class SubscriptionService(private val database: Database) {

    suspend fun subscribe(telegramId: Long, performanceId: UUID) = dbQuery(database) {
        val userRow = Users.selectAll().where { Users.telegramId eq telegramId }.singleOrNull()
            ?: throw NotFoundException("User not found")
        val userId = userRow[Users.id]

        Performances.selectAll().where { Performances.id eq performanceId }.singleOrNull()
            ?: throw NotFoundException("Performance not found")

        val existing = Subscriptions.selectAll()
            .where { (Subscriptions.userId eq userId) and (Subscriptions.performanceId eq performanceId) }
            .singleOrNull()

        if (existing == null) {
            Subscriptions.insert {
                it[Subscriptions.userId] = userId
                it[Subscriptions.performanceId] = performanceId
                it[isActive] = true
            }
        } else {
            Subscriptions.update({
                (Subscriptions.userId eq userId) and (Subscriptions.performanceId eq performanceId)
            }) {
                it[isActive] = true
            }
        }
    }

    suspend fun unsubscribe(telegramId: Long, performanceId: UUID) = dbQuery(database) {
        val userRow = Users.selectAll().where { Users.telegramId eq telegramId }.singleOrNull()
            ?: throw NotFoundException("User not found")
        val userId = userRow[Users.id]

        Subscriptions.update({
            (Subscriptions.userId eq userId) and (Subscriptions.performanceId eq performanceId)
        }) {
            it[isActive] = false
        }
    }

    suspend fun getUserSubscriptions(telegramId: Long): List<SubscriptionResponse> = dbQuery(database) {
        val userRow = Users.selectAll().where { Users.telegramId eq telegramId }.singleOrNull()
            ?: return@dbQuery emptyList()
        val userId = userRow[Users.id]

        Subscriptions
            .join(Performances, JoinType.INNER, Subscriptions.performanceId, Performances.id)
            .join(Theatres, JoinType.INNER, Performances.theatreId, Theatres.id)
            .selectAll()
            .where { (Subscriptions.userId eq userId) and (Subscriptions.isActive eq true) }
            .map { row ->
                SubscriptionResponse(
                    id = row[Subscriptions.id].toString(),
                    performance = PerformanceResponse(
                        id = row[Performances.id].toString(),
                        theatreId = row[Performances.theatreId].toString(),
                        title = row[Performances.title],
                        url = row[Performances.url],
                        scene = row[Performances.scene]
                    ),
                    theatre = TheatreResponse(
                        id = row[Theatres.id].toString(),
                        slug = row[Theatres.slug],
                        name = row[Theatres.name],
                        websiteUrl = row[Theatres.websiteUrl]
                    ),
                    subscribedAt = row[Subscriptions.subscribedAt].toString(),
                    notificationCount = row[Subscriptions.notificationCount]
                )
            }
    }

    suspend fun getAdminSubscriptionGroups(theatreSlug: String): List<AdminSubscriptionGroupResponse> = dbQuery(database) {
        val theatreRow = Theatres.selectAll().where { Theatres.slug eq theatreSlug }.singleOrNull()
            ?: return@dbQuery emptyList()
        val theatreId = theatreRow[Theatres.id]

        val performances = Performances.selectAll()
            .where { Performances.theatreId eq theatreId }
            .associate { row ->
                row[Performances.id] to PerformanceResponse(
                    id = row[Performances.id].toString(),
                    theatreId = row[Performances.theatreId].toString(),
                    title = row[Performances.title],
                    url = row[Performances.url],
                    scene = row[Performances.scene]
                )
            }

        val groups = Subscriptions
            .join(Users, JoinType.INNER, Subscriptions.userId, Users.id)
            .join(Performances, JoinType.INNER, Subscriptions.performanceId, Performances.id)
            .selectAll()
            .where { (Performances.theatreId eq theatreId) and (Subscriptions.isActive eq true) }
            .groupBy { it[Subscriptions.performanceId] }
            .map { (perfId, rows) ->
                AdminSubscriptionGroupResponse(
                    performance = performances[perfId]!!,
                    subscribers = rows.map { row ->
                        AdminSubscriberInfo(
                            telegramId = row[Users.telegramId],
                            firstName = row[Users.firstName],
                            username = row[Users.username],
                            subscribedAt = row[Subscriptions.subscribedAt].toString(),
                            notificationCount = row[Subscriptions.notificationCount]
                        )
                    }
                )
            }

        groups
    }
}
