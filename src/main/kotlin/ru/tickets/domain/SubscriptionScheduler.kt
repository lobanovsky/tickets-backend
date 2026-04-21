package ru.tickets.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours

class SubscriptionScheduler(
    private val paidSubscriptionService: PaidSubscriptionService,
    private val telegramSenderService: TelegramSenderService
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

            try {
                val expiring = paidSubscriptionService.findExpiringTomorrow()
                expiring.forEach { telegramId ->
                    telegramSenderService.sendToUser(
                        telegramId = telegramId,
                        text = """
                            ⏰ Ваша подписка на сервис отслеживания билетов истекает <b>завтра</b>.

                            После окончания подписки уведомления о появлении билетов перестанут приходить.

                            Подписка действует во всех 5 ботах: РАМТ, Театр Наций, Вахтангов, Фоменко, Ленсовет.

                            Стоимость 1000₽ на полгода за все 5 ботов.
                        """.trimIndent(),
                        parseMode = "HTML"
                    )
                }
                if (expiring.isNotEmpty()) log.info("Отправлено уведомлений об истечении подписки: ${expiring.size}")
            } catch (e: Exception) {
                log.error("Ошибка при отправке уведомлений об истечении подписок: ${e.message}")
            }

            delay(24.hours)
        }
    }
}
