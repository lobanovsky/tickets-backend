package ru.tickets.db.schema

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object Subscriptions : Table("subscriptions") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val performanceId = uuid("performance_id").references(Performances.id, onDelete = ReferenceOption.CASCADE)
    val subscribedAt = datetime("subscribed_at").defaultExpression(CurrentDateTime)
    val isActive = bool("is_active").default(true)
    val notificationCount = integer("notification_count").default(0)

    override val primaryKey = PrimaryKey(id)
}
