package ru.tickets.models.responses

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val id: String,
    val telegramId: Long,
    val firstName: String,
    val lastName: String?,
    val username: String?,
    val isActive: Boolean,
    val isVip: Boolean,
    val createdAt: String,
    val hasPaidSubscription: Boolean
)

@Serializable
data class TheatreResponse(
    val id: String,
    val slug: String,
    val name: String,
    val websiteUrl: String
)

@Serializable
data class PerformanceResponse(
    val id: String,
    val theatreId: String,
    val title: String,
    val url: String,
    val scene: String?
)

@Serializable
data class PerformanceWithStatusResponse(
    val id: String,
    val theatreId: String,
    val title: String,
    val url: String,
    val scene: String?,
    val isSubscribed: Boolean
)

@Serializable
data class SubscriptionResponse(
    val id: String,
    val performance: PerformanceResponse,
    val theatre: TheatreResponse,
    val subscribedAt: String,
    val notificationCount: Int
)

@Serializable
data class PendingNotificationResponse(
    val id: String,
    val telegramId: Long,
    val performanceTitle: String,
    val performanceUrl: String,
    val theatreSlug: String,
    val scheduleSummary: String,
    val createdAt: String
)

@Serializable
data class SubscriptionsByTheatreResponse(
    val theatre: TheatreResponse,
    val subscriptions: List<SubscriptionResponse>
)

@Serializable
data class AdminSubscriberInfo(
    val telegramId: Long,
    val firstName: String,
    val username: String?,
    val subscribedAt: String,
    val notificationCount: Int
)

@Serializable
data class AdminSubscriptionGroupResponse(
    val performance: PerformanceResponse,
    val subscribers: List<AdminSubscriberInfo>
)

@Serializable
data class TheatreStats(
    val slug: String,
    val name: String,
    val performanceCount: Long,
    val activeSubscriptionCount: Long
)

@Serializable
data class PaidSubscriptionResponse(
    val id: String,
    val startDate: String,
    val endDate: String,
    val amountPaid: Int,
    val comment: String?,
    val isActive: Boolean,
    val createdBy: String,
    val createdAt: String
)

@Serializable
data class PaidSubscriptionStatusResponse(
    val hasActiveSubscription: Boolean,
    val subscription: PaidSubscriptionResponse?
)

@Serializable
data class MessageSendResult(
    val botSlug: String,
    val telegramId: Long,
    val success: Boolean,
    val error: String? = null
)

@Serializable
data class MessageSendResponse(
    val total: Int,
    val succeeded: Int,
    val results: List<MessageSendResult>
)

@Serializable
data class StatsResponse(
    val totalUsers: Long,
    val activeUsers: Long,
    val totalSubscriptions: Long,
    val activeSubscriptions: Long,
    val pendingNotifications: Long,
    val byTheatre: List<TheatreStats>
)
