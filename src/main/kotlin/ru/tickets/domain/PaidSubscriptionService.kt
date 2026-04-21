package ru.tickets.domain

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import ru.tickets.db.schema.PaidSubscriptions
import ru.tickets.db.schema.Users
import ru.tickets.models.BadRequestException
import ru.tickets.models.NotFoundException
import ru.tickets.models.requests.CreatePaidSubscriptionRequest
import ru.tickets.models.requests.UpdatePaidSubscriptionRequest
import ru.tickets.models.responses.PaidSubscriptionResponse
import ru.tickets.models.responses.PaidSubscriptionStatusResponse
import java.time.LocalDate
import java.util.UUID

class PaidSubscriptionService(private val database: Database) {

    suspend fun create(telegramId: Long, req: CreatePaidSubscriptionRequest, createdBy: String): PaidSubscriptionResponse = dbQuery(database) {
        val userRow = Users.selectAll().where { Users.telegramId eq telegramId }.singleOrNull()
            ?: throw NotFoundException("User not found")

        val startDate = runCatching { LocalDate.parse(req.startDate) }.getOrElse { throw BadRequestException("Invalid startDate format, expected YYYY-MM-DD") }
        val endDate = runCatching { LocalDate.parse(req.endDate) }.getOrElse { throw BadRequestException("Invalid endDate format, expected YYYY-MM-DD") }

        val id = PaidSubscriptions.insert {
            it[userId] = userRow[Users.id]
            it[PaidSubscriptions.startDate] = startDate
            it[PaidSubscriptions.endDate] = endDate
            it[amountPaid] = req.amountPaid
            it[comment] = req.comment
            it[PaidSubscriptions.createdBy] = createdBy
        }[PaidSubscriptions.id]

        PaidSubscriptionResponse(
            id = id.toString(),
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            amountPaid = req.amountPaid,
            comment = req.comment,
            isActive = true,
            createdBy = createdBy,
            createdAt = java.time.LocalDateTime.now().toString()
        )
    }

    suspend fun getHistory(telegramId: Long): List<PaidSubscriptionResponse> = dbQuery(database) {
        val userRow = Users.selectAll().where { Users.telegramId eq telegramId }.singleOrNull()
            ?: throw NotFoundException("User not found")

        PaidSubscriptions.selectAll()
            .where { PaidSubscriptions.userId eq userRow[Users.id] }
            .orderBy(PaidSubscriptions.createdAt, SortOrder.DESC)
            .map { it.toPaidSubscriptionResponse() }
    }

    suspend fun update(id: UUID, req: UpdatePaidSubscriptionRequest): PaidSubscriptionResponse = dbQuery(database) {
        val row = PaidSubscriptions.selectAll().where { PaidSubscriptions.id eq id }.singleOrNull()
            ?: throw NotFoundException("Paid subscription not found")

        val newEndDate = req.endDate?.let {
            runCatching { LocalDate.parse(it) }.getOrElse { throw BadRequestException("Invalid endDate format, expected YYYY-MM-DD") }
        }

        PaidSubscriptions.update({ PaidSubscriptions.id eq id }) {
            if (req.isActive != null) it[isActive] = req.isActive
            if (newEndDate != null) it[endDate] = newEndDate
            if (req.comment != null) it[comment] = req.comment
        }

        PaidSubscriptions.selectAll().where { PaidSubscriptions.id eq id }.single().toPaidSubscriptionResponse()
    }

    suspend fun getStatus(telegramId: Long): PaidSubscriptionStatusResponse = dbQuery(database) {
        val userRow = Users.selectAll().where { Users.telegramId eq telegramId }.singleOrNull()
            ?: throw NotFoundException("User not found")

        val active = PaidSubscriptions.selectAll()
            .where { PaidSubscriptions.userId eq userRow[Users.id] }
            .orderBy(PaidSubscriptions.endDate, SortOrder.DESC)
            .firstOrNull {
                it[PaidSubscriptions.isActive] && !it[PaidSubscriptions.endDate].isBefore(LocalDate.now())
            }

        PaidSubscriptionStatusResponse(
            hasActiveSubscription = active != null,
            subscription = active?.toPaidSubscriptionResponse()
        )
    }

    suspend fun findExpiringTomorrow(): List<Long> = dbQuery(database) {
        val tomorrow = LocalDate.now().plusDays(1)
        PaidSubscriptions.join(Users, JoinType.INNER, PaidSubscriptions.userId, Users.id)
            .selectAll()
            .where { (PaidSubscriptions.isActive eq true) and (PaidSubscriptions.endDate eq tomorrow) }
            .map { it[Users.telegramId] }
    }

    suspend fun deactivateExpired(): Int = dbQuery(database) {
        PaidSubscriptions.update({
            (PaidSubscriptions.isActive eq true) and (PaidSubscriptions.endDate less LocalDate.now())
        }) {
            it[isActive] = false
        }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toPaidSubscriptionResponse() = PaidSubscriptionResponse(
        id = this[PaidSubscriptions.id].toString(),
        startDate = this[PaidSubscriptions.startDate].toString(),
        endDate = this[PaidSubscriptions.endDate].toString(),
        amountPaid = this[PaidSubscriptions.amountPaid],
        comment = this[PaidSubscriptions.comment],
        isActive = this[PaidSubscriptions.isActive],
        createdBy = this[PaidSubscriptions.createdBy],
        createdAt = this[PaidSubscriptions.createdAt].toString()
    )
}
