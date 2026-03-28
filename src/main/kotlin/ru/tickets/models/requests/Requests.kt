package ru.tickets.models.requests

import kotlinx.serialization.Serializable

@Serializable
data class SyncUserRequest(
    val telegramId: Long,
    val firstName: String,
    val lastName: String? = null,
    val username: String? = null
)

@Serializable
data class SubscribeRequest(
    val telegramId: Long,
    val performanceId: String
)

@Serializable
data class UnsubscribeRequest(
    val telegramId: Long,
    val performanceId: String
)
