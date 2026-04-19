package ru.tickets.domain

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.tickets.db.schema.PaidSubscriptions
import ru.tickets.db.schema.PendingNotifications
import ru.tickets.db.schema.Performances
import ru.tickets.db.schema.Subscriptions
import ru.tickets.db.schema.Theatres
import ru.tickets.db.schema.Users
import java.time.LocalDate
import ru.tickets.models.NotFoundException
import ru.tickets.models.responses.PendingNotificationResponse
import java.time.LocalDateTime
import java.util.*

class NotificationService(private val database: Database) {

    fun createNotifications(performanceId: UUID, scheduleSummary: String): List<PendingNotificationResponse> {
        val created = mutableListOf<PendingNotificationResponse>()
        transaction(database) {
            val perfRow = Performances
                .join(Theatres, JoinType.INNER, Performances.theatreId, Theatres.id)
                .selectAll()
                .where { Performances.id eq performanceId }
                .singleOrNull() ?: return@transaction

            val today = LocalDate.now()
            val subscribers = Subscriptions
                .join(Users, JoinType.INNER, Subscriptions.userId, Users.id)
                .selectAll()
                .where {
                    (Subscriptions.performanceId eq performanceId) and
                    (Subscriptions.isActive eq true) and
                    (Subscriptions.userId inSubQuery PaidSubscriptions
                        .select(PaidSubscriptions.userId)
                        .where {
                            (PaidSubscriptions.isActive eq true) and
                            (PaidSubscriptions.endDate greaterEq today)
                        })
                }

            for (row in subscribers) {
                val userId = row[Subscriptions.userId]

                val alreadyPending = PendingNotifications.selectAll()
                    .where {
                        (PendingNotifications.userId eq userId) and
                        (PendingNotifications.performanceId eq performanceId) and
                        PendingNotifications.sentAt.isNull()
                    }.count() > 0

                if (!alreadyPending) {
                    val notifId = PendingNotifications.insert {
                        it[PendingNotifications.userId] = userId
                        it[PendingNotifications.performanceId] = performanceId
                        it[PendingNotifications.scheduleSummary] = scheduleSummary
                    }[PendingNotifications.id]

                    Subscriptions.update({
                        (Subscriptions.userId eq userId) and (Subscriptions.performanceId eq performanceId)
                    }) {
                        with(SqlExpressionBuilder) {
                            it[notificationCount] = Subscriptions.notificationCount + 1
                        }
                    }

                    created.add(
                        PendingNotificationResponse(
                            id = notifId.toString(),
                            telegramId = row[Users.telegramId],
                            performanceTitle = perfRow[Performances.title],
                            performanceUrl = perfRow[Performances.url],
                            theatreSlug = perfRow[Theatres.slug],
                            scheduleSummary = scheduleSummary,
                            createdAt = LocalDateTime.now().toString()
                        )
                    )
                }
            }
        }
        return created
    }

    suspend fun getPendingForPerformance(performanceId: UUID): List<PendingNotificationResponse> = dbQuery(database) {
        PendingNotifications
            .join(Users, JoinType.INNER, PendingNotifications.userId, Users.id)
            .join(Performances, JoinType.INNER, PendingNotifications.performanceId, Performances.id)
            .join(Theatres, JoinType.INNER, Performances.theatreId, Theatres.id)
            .selectAll()
            .where {
                (PendingNotifications.sentAt.isNull()) and
                (PendingNotifications.performanceId eq performanceId)
            }
            .map { row ->
                PendingNotificationResponse(
                    id = row[PendingNotifications.id].toString(),
                    telegramId = row[Users.telegramId],
                    performanceTitle = row[Performances.title],
                    performanceUrl = row[Performances.url],
                    theatreSlug = row[Theatres.slug],
                    scheduleSummary = row[PendingNotifications.scheduleSummary],
                    createdAt = row[PendingNotifications.createdAt].toString()
                )
            }
    }

    suspend fun getPendingForTheatre(theatreSlug: String): List<PendingNotificationResponse> = dbQuery(database) {
        PendingNotifications
            .join(Users, JoinType.INNER, PendingNotifications.userId, Users.id)
            .join(Performances, JoinType.INNER, PendingNotifications.performanceId, Performances.id)
            .join(Theatres, JoinType.INNER, Performances.theatreId, Theatres.id)
            .selectAll()
            .where { (PendingNotifications.sentAt.isNull()) and (Theatres.slug eq theatreSlug) }
            .map { row ->
                PendingNotificationResponse(
                    id = row[PendingNotifications.id].toString(),
                    telegramId = row[Users.telegramId],
                    performanceTitle = row[Performances.title],
                    performanceUrl = row[Performances.url],
                    theatreSlug = row[Theatres.slug],
                    scheduleSummary = row[PendingNotifications.scheduleSummary],
                    createdAt = row[PendingNotifications.createdAt].toString()
                )
            }
    }

    suspend fun acknowledge(notificationId: UUID) = dbQuery(database) {
        PendingNotifications.selectAll().where { PendingNotifications.id eq notificationId }.singleOrNull()
            ?: throw NotFoundException("Notification not found")

        PendingNotifications.update({ PendingNotifications.id eq notificationId }) {
            it[sentAt] = LocalDateTime.now()
        }
    }
}
