package com.example

import com.example.notifications.BalanceReadingReminderWorker
import com.example.notifications.RechargeReminderWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Evita que vuelva a aparecer el retraso de un día del initialDelay periódico. */
class DailyReminderSchedulingTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(hour: Int, day: Int = 18): Long = Calendar.getInstance(utc).apply {
        clear()
        set(2026, Calendar.NOVEMBER, day, hour, 0, 0)
    }.timeInMillis

    @Test
    fun `aviso de saldo espera a las 22 de hoy si se abre antes`() {
        val now = at(10)

        assertEquals(
            at(22) - now,
            BalanceReadingReminderWorker.delayUntilNextReminder(now, timeZone = utc)
        )
    }

    @Test
    fun `aviso de saldo pasa a las 22 de manana si se abre despues`() {
        val now = at(23)

        assertEquals(
            at(22, day = 19) - now,
            BalanceReadingReminderWorker.delayUntilNextReminder(now, timeZone = utc)
        )
    }

    @Test
    fun `a las 22 en punto el aviso pasa al dia siguiente`() {
        val now = at(22)

        assertEquals(
            at(22, day = 19) - now,
            BalanceReadingReminderWorker.delayUntilNextReminder(now, timeZone = utc)
        )
    }

    @Test
    fun `aviso de recarga espera a las 9 de hoy si se abre antes`() {
        val now = at(8)

        assertEquals(
            at(9) - now,
            RechargeReminderWorker.delayUntilNextReminder(now, timeZone = utc)
        )
    }
}
