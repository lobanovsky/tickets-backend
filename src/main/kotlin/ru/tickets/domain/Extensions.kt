package ru.tickets.domain

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

suspend fun <T> dbQuery(database: Database, block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO, database) { block() }
