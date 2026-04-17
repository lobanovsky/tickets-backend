package ru.tickets.db.schema

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Performances : Table("performances") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val theatreId = uuid("theatre_id").references(Theatres.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 500)
    val url = varchar("url", 1000)
    val scene = varchar("scene", 200).nullable()
    val isActive = bool("is_active").default(true)

    override val primaryKey = PrimaryKey(id)
}
