package ru.tickets.db

import io.ktor.server.application.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import ru.tickets.db.schema.*

val DatabaseKey = AttributeKey<Database>("TicketsDatabase")

fun Application.configureDatabases() {
    val url = environment.config.property("postgres.url").getString()
    val user = environment.config.property("postgres.user").getString()
    val password = environment.config.property("postgres.password").getString()

    val database = Database.connect(
        url = url,
        driver = "org.postgresql.Driver",
        user = user,
        password = password
    )
    attributes.put(DatabaseKey, database)

    transaction(database) {
        arrayOf(Theatres, Performances, Users, Subscriptions, PendingNotifications)
        exec("CREATE UNIQUE INDEX IF NOT EXISTS performances_theatre_url_idx ON performances (theatre_id, url)")
        exec("CREATE UNIQUE INDEX IF NOT EXISTS subscriptions_user_perf_idx ON subscriptions (user_id, performance_id)")
        exec("UPDATE performances SET is_active = TRUE WHERE is_active IS NULL")
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS is_vip BOOLEAN NOT NULL DEFAULT FALSE")
    }

    seedTheatres(database)

    log.info("Database connected and schema created: $url")
}
