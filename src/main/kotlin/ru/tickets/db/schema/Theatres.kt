package ru.tickets.db.schema

import org.jetbrains.exposed.sql.Table

object Theatres : Table("theatres") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val slug = varchar("slug", 50).uniqueIndex()
    val name = varchar("name", 200)
    val websiteUrl = varchar("website_url", 500)

    override val primaryKey = PrimaryKey(id)
}
