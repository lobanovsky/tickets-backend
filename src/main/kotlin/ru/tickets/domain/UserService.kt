package ru.tickets.domain

import org.jetbrains.exposed.sql.*
import ru.tickets.db.schema.PaidSubscriptions
import ru.tickets.db.schema.Subscriptions
import ru.tickets.db.schema.UserBotLinks
import ru.tickets.db.schema.Users
import ru.tickets.models.NotFoundException
import ru.tickets.models.requests.SyncUserRequest
import ru.tickets.models.responses.UserBotLinkResponse
import ru.tickets.models.responses.UserResponse
import java.time.LocalDate
import java.time.LocalDateTime

class UserService(private val database: Database) {

    suspend fun findAll(hasSubscriptions: Boolean? = null): List<UserResponse> = dbQuery(database) {
        val query = when (hasSubscriptions) {
            true -> Users.selectAll().where {
                Users.id inSubQuery Subscriptions
                    .select(Subscriptions.userId)
                    .where { Subscriptions.isActive eq true }
                    .withDistinct()
            }

            false -> Users.selectAll().where {
                Users.id notInSubQuery Subscriptions
                    .select(Subscriptions.userId)
                    .where { Subscriptions.isActive eq true }
                    .withDistinct()
            }

            null -> Users.selectAll()
        }
        val today = LocalDate.now()
        val paidUserIds = PaidSubscriptions.selectAll()
            .where { PaidSubscriptions.isActive eq true }
            .filter {
                !it[PaidSubscriptions.startDate].isAfter(today) &&
                        !it[PaidSubscriptions.endDate].isBefore(today)
            }
            .map { it[PaidSubscriptions.userId] }
            .toSet()

        val botLinksByUserId = UserBotLinks.selectAll()
            .groupBy { it[UserBotLinks.userId] }
            .mapValues { (_, rows) ->
                rows.map { UserBotLinkResponse(it[UserBotLinks.botSlug], it[UserBotLinks.isSubscribed]) }
            }

        query.orderBy(Users.createdAt, SortOrder.DESC).map { row ->
            UserResponse(
                id = row[Users.id].toString(),
                telegramId = row[Users.telegramId],
                firstName = row[Users.firstName],
                lastName = row[Users.lastName],
                username = row[Users.username],
                isActive = row[Users.isActive],
                isVip = row[Users.isVip],
                createdAt = row[Users.createdAt].toString(),
                hasPaidSubscription = row[Users.id] in paidUserIds,
                botLinks = botLinksByUserId[row[Users.id]] ?: emptyList()
            )
        }
    }

    suspend fun syncUser(req: SyncUserRequest, botSlug: String): UserResponse = dbQuery(database) {
        val existing = Users.selectAll().where { Users.telegramId eq req.telegramId }.singleOrNull()

        if (existing == null) {
            val id = Users.insert {
                it[telegramId] = req.telegramId
                it[firstName] = req.firstName
                it[lastName] = req.lastName
                it[username] = req.username
                it[isActive] = true
            }[Users.id]

            val today = LocalDate.now()
            PaidSubscriptions.insert {
                it[userId] = id
                it[startDate] = today
                it[endDate] = today.plusDays(7)
                it[amountPaid] = 0
                it[comment] = "Пробный период"
                it[createdBy] = "system"
            }

            upsertBotLink(id, botSlug)
            UserResponse(
                id = id.toString(),
                telegramId = req.telegramId,
                firstName = req.firstName,
                lastName = req.lastName,
                username = req.username,
                isActive = true,
                isVip = false,
                createdAt = LocalDateTime.now().toString(),
                hasPaidSubscription = true
            )
        } else {
            Users.update({ Users.telegramId eq req.telegramId }) {
                it[firstName] = req.firstName
                it[lastName] = req.lastName
                it[username] = req.username
                it[isActive] = true
            }
            upsertBotLink(existing[Users.id], botSlug)
            UserResponse(
                id = existing[Users.id].toString(),
                telegramId = existing[Users.telegramId],
                firstName = req.firstName,
                lastName = req.lastName,
                username = req.username,
                isActive = true,
                isVip = existing[Users.isVip],
                createdAt = existing[Users.createdAt].toString(),
                hasPaidSubscription = false
            )
        }
    }

    private fun upsertBotLink(userId: java.util.UUID, botSlug: String) {
        val exists = UserBotLinks.selectAll()
            .where { (UserBotLinks.userId eq userId) and (UserBotLinks.botSlug eq botSlug) }
            .count() > 0
        if (exists) {
            UserBotLinks.update({ (UserBotLinks.userId eq userId) and (UserBotLinks.botSlug eq botSlug) }) {
                it[UserBotLinks.isSubscribed] = true
                it[UserBotLinks.checkedAt] = LocalDateTime.now()
            }
        } else {
            UserBotLinks.insert {
                it[UserBotLinks.userId] = userId
                it[UserBotLinks.botSlug] = botSlug
                it[UserBotLinks.isSubscribed] = true
                it[checkedAt] = LocalDateTime.now()
            }
        }
    }

    suspend fun setVip(telegramId: Long, isVip: Boolean) = dbQuery(database) {
        val updated = Users.update({ Users.telegramId eq telegramId }) {
            it[Users.isVip] = isVip
        }
        if (updated == 0) throw NotFoundException("User not found")
    }
}
