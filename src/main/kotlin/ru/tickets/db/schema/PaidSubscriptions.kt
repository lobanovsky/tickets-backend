package ru.tickets.db.schema

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object PaidSubscriptions : Table("paid_subscriptions") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val userId = uuid("user_id").references(Users.id)
    val startDate = date("start_date")
    val endDate = date("end_date")
    val amountPaid = integer("amount_paid")
    val comment = text("comment").nullable()
    val isActive = bool("is_active").default(true)
    val createdBy = varchar("created_by", 50)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)
}
