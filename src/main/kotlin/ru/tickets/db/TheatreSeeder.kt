package ru.tickets.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import ru.tickets.db.schema.Theatres

fun seedTheatres(database: Database) {
    val theatres = listOf(
        Triple("ramt", "РАМТ", "https://ramt.ru"),
        Triple("nations", "Театр Наций", "https://theatreofnations.ru"),
        Triple("vakhtangov", "Театр им. Вахтангова", "https://vakhtangov.ru"),
        Triple("fomenki", "Мастерская Петра Фоменко", "https://fomenki.ru"),
        Triple("lensov", "Театр им. Ленсовета", "https://lensov-theatre.spb.ru"),
        Triple("mxt", "МХТ им. А.П. Чехова", "https://mxat.ru"),
        Triple("satirikon", "Сатирикон", "https://www.satirikon.ru")
    )

    transaction(database) {
        for ((slug, name, url) in theatres) {
            val exists = Theatres.selectAll().where { Theatres.slug eq slug }.count() > 0
            if (!exists) {
                Theatres.insert {
                    it[Theatres.slug] = slug
                    it[Theatres.name] = name
                    it[websiteUrl] = url
                }
            }
        }
    }
}
