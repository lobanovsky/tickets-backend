package ru.tickets.db.schema

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object Users : Table("users") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val telegramId = long("telegram_id").uniqueIndex()
    val firstName = varchar("first_name", 100)
    val lastName = varchar("last_name", 100).nullable()
    val username = varchar("username", 100).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)
}
