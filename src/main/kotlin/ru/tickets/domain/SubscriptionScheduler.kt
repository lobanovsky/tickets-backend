package ru.tickets.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours

class SubscriptionScheduler(
    private val paidSubscriptionService: PaidSubscriptionService
) {
    private val log = LoggerFactory.getLogger(SubscriptionScheduler::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        scope.launch { runDailyLoop() }
        log.info("SubscriptionScheduler started")
    }

    private suspend fun runDailyLoop() {
        while (true) {
            try {
                val deactivated = paidSubscriptionService.deactivateExpired()
                if (deactivated > 0) log.info("Деактивировано истёкших подписок: $deactivated")
            } catch (e: Exception) {
                log.error("Ошибка при деактивации подписок: ${e.message}")
            }
            delay(24.hours)
        }
    }
}
