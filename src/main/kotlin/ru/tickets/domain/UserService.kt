package ru.tickets.domain

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import ru.tickets.db.schema.Subscriptions
import ru.tickets.db.schema.Users
import ru.tickets.models.requests.SyncUserRequest
import ru.tickets.models.responses.UserResponse

class UserService(private val database: Database) {

    suspend fun findAll(hasSubscriptions: Boolean? = null): List<UserResponse> = dbQuery(database) {
        val query = when (hasSubscriptions) {
            true -> Users.selectAll().where {
                Users.id inSubQuery Subscriptions.select(Subscriptions.userId).withDistinct()
            }

            false -> Users.selectAll().where {
                Users.id notInSubQuery Subscriptions.select(Subscriptions.userId).withDistinct()
            }

            null -> Users.selectAll()
        }
        query.map { row ->
            UserResponse(
                id = row[Users.id].toString(),
                telegramId = row[Users.telegramId],
                firstName = row[Users.firstName],
                lastName = row[Users.lastName],
                username = row[Users.username],
                isActive = row[Users.isActive]
            )
        }
    }

    suspend fun syncUser(req: SyncUserRequest): UserResponse = dbQuery(database) {
        val existing = Users.selectAll().where { Users.telegramId eq req.telegramId }.singleOrNull()

        if (existing == null) {
            val id = Users.insert {
                it[telegramId] = req.telegramId
                it[firstName] = req.firstName
                it[lastName] = req.lastName
                it[username] = req.username
                it[isActive] = true
            }[Users.id]

            UserResponse(
                id = id.toString(),
                telegramId = req.telegramId,
                firstName = req.firstName,
                lastName = req.lastName,
                username = req.username,
                isActive = true
            )
        } else {
            Users.update({ Users.telegramId eq req.telegramId }) {
                it[firstName] = req.firstName
                it[lastName] = req.lastName
                it[username] = req.username
                it[isActive] = true
            }
            UserResponse(
                id = existing[Users.id].toString(),
                telegramId = existing[Users.telegramId],
                firstName = req.firstName,
                lastName = req.lastName,
                username = req.username,
                isActive = true
            )
        }
    }
}
