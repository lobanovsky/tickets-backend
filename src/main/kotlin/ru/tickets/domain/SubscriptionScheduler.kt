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
    private val telegramSenderService: TelegramSenderService
) {
    private val log = LoggerFactory.getLogger(SubscriptionScheduler::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        scope.launch { runDailyLoop() }
        log.info("SubscriptionScheduler started")
    }

    private suspend fun runDailyLoop() {
        delay(millisUntilNext9AM().milliseconds)
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

            delay(millisUntilNext9AM().milliseconds)
        }
    }

    private fun millisUntilNext9AM(): Long {
        val moscow = ZoneId.of("Europe/Moscow")
        val now = ZonedDateTime.now(moscow)
        var next9AM = now.toLocalDate().atTime(9, 0).atZone(moscow)
        if (!next9AM.isAfter(now)) next9AM = next9AM.plusDays(1)
        return java.time.Duration.between(now, next9AM).toMillis()
    }
}
