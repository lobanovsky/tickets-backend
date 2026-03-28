package ru.tickets.db.schema

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object PendingNotifications : Table("pending_notifications") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val performanceId = uuid("performance_id").references(Performances.id, onDelete = ReferenceOption.CASCADE)
    val scheduleSummary = text("schedule_summary")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val sentAt = datetime("sent_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
