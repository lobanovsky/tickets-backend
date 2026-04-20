package ru.tickets.db.schema

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object UserBotLinks : Table("user_bot_links") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val userId = uuid("user_id").references(Users.id)
    val botSlug = varchar("bot_slug", 50)
    val isSubscribed = bool("is_subscribed").default(true)
    val checkedAt = datetime("checked_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)
}
