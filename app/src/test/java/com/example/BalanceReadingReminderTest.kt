package com.example

import com.example.domain.BalanceReadingReminder
import com.example.domain.BalanceReadingSample
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceReadingReminderTest {

    private val dayStart = 1_000_000L
    private val dayEnd = dayStart + 24L * 60L * 60L * 1000L

    @Test
    fun sinLecturas_devuelveAmbasLineas() {
        assertEquals(
            listOf(1, 2),
            BalanceReadingReminder.missingLineIds(emptyList(), dayStart, dayEnd)
        )
    }

    @Test
    fun lecturaDeHoy_soloFaltaLaOtraLinea() {
        val readings = listOf(BalanceReadingSample(planId = 1, timestampMs = dayStart + 100L))

        assertEquals(
            listOf(2),
            BalanceReadingReminder.missingLineIds(readings, dayStart, dayEnd)
        )
    }

    @Test
    fun lecturaDeAyer_noCuenta() {
        val readings = listOf(
            BalanceReadingSample(planId = 1, timestampMs = dayStart - 1L),
            BalanceReadingSample(planId = 2, timestampMs = dayStart - 60_000L)
        )

        assertEquals(
            listOf(1, 2),
            BalanceReadingReminder.missingLineIds(readings, dayStart, dayEnd)
        )
    }

    @Test
    fun limitesDelDia_inicioIncluidoFinExcluido() {
        val readings = listOf(
            BalanceReadingSample(planId = 1, timestampMs = dayStart),
            BalanceReadingSample(planId = 2, timestampMs = dayEnd)
        )

        assertEquals(
            listOf(2),
            BalanceReadingReminder.missingLineIds(readings, dayStart, dayEnd)
        )
    }

    @Test
    fun lecturasDeAmbasLineas_noFaltaNinguna() {
        val readings = listOf(
            BalanceReadingSample(planId = 1, timestampMs = dayStart + 10L),
            BalanceReadingSample(planId = 2, timestampMs = dayEnd - 10L)
        )

        assertEquals(
            emptyList<Int>(),
            BalanceReadingReminder.missingLineIds(readings, dayStart, dayEnd)
        )
    }
}
