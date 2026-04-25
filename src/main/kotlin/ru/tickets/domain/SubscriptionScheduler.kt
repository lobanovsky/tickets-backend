package ru.tickets.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds

class SubscriptionScheduler(
    private val paidSubscriptionService: PaidSubscriptionService,
    private val telegramSenderService: TelegramSenderService,
    private val notificationService: NotificationService
) {
    private val log = LoggerFactory.getLogger(SubscriptionScheduler::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        scope.launch { runDeactivationLoop() }
        scope.launch { runExpiringWarningLoop() }
        scope.launch { runNotificationCleanupLoop() }
        log.info("SubscriptionScheduler started")
    }

    private suspend fun runDeactivationLoop() {
        delay(millisUntilNextTime(9, 0).milliseconds)
        while (true) {
            try {
                val deactivated = paidSubscriptionService.deactivateExpired()
                if (deactivated.isNotEmpty()) {
                    log.info("Деактивировано истёкших подписок: ${deactivated.size}")
                    deactivated.forEach { telegramId ->
                        telegramSenderService.sendToUser(
                            telegramId = telegramId,
                            text = """
                                ❌ Ваша подписка на сервис отслеживания билетов <b>закончилась</b>.

                                Уведомления о появлении билетов больше не будут приходить.

                                Чтобы продолжить получать уведомления — необходимо оплатить сервис (кнопка "Оплата").

                                Стоимость 1000₽ на полгода за все 6 ботов.
                            """.trimIndent(),
                            parseMode = "HTML"
                        )
                    }
                }
            } catch (e: Exception) {
                log.error("Ошибка при деактивации подписок: ${e.message}")
            }
            delay(millisUntilNextTime(9, 0).milliseconds)
        }
    }

    private suspend fun runExpiringWarningLoop() {
        delay(millisUntilNextTime(20, 0).milliseconds)
        while (true) {
            try {
                val expiring = paidSubscriptionService.findExpiringTomorrow()
                expiring.forEach { telegramId ->
                    telegramSenderService.sendToUser(
                        telegramId = telegramId,
                        text = """
                            ⏰ Ваша подписка на сервис отслеживания билетов истекает <b>завтра</b>.

                            После окончания подписки уведомления о появлении билетов перестанут приходить.

                            Подписка действует во всех 6 ботах: РАМТ, Театр Наций, Вахтангов, Фоменко, Ленсовет, МХТ.

                            Стоимость 1000₽ на полгода за все 6 ботов.
                        """.trimIndent(),
                        parseMode = "HTML"
                    )
                }
                if (expiring.isNotEmpty()) log.info("Отправлено уведомлений об истечении подписки: ${expiring.size}")
            } catch (e: Exception) {
                log.error("Ошибка при отправке уведомлений об истечении подписок: ${e.message}")
            }
            delay(millisUntilNextTime(20, 0).milliseconds)
        }
    }

    private suspend fun runNotificationCleanupLoop() {
        delay(millisUntilNextTime(3, 0).milliseconds)
        while (true) {
            try {
                val deleted = notificationService.deleteOldSent()
                if (deleted > 0) log.info("Очищено устаревших уведомлений: $deleted")
            } catch (e: Exception) {
                log.error("Ошибка при очистке уведомлений: ${e.message}")
            }
            delay(millisUntilNextTime(3, 0).milliseconds)
        }
    }

    private fun millisUntilNextTime(hour: Int, minute: Int): Long {
        val moscow = ZoneId.of("Europe/Moscow")
        val now = ZonedDateTime.now(moscow)
        var next = now.toLocalDate().atTime(hour, minute).atZone(moscow)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return java.time.Duration.between(now, next).toMillis()
    }
}
