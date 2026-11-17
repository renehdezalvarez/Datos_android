package com.example.domain

/** Minimal, Android-free representation of a stored balance reading. */
data class BalanceReadingSample(
    val planId: Int,
    val timestampMs: Long
)

/**
 * Decides which of the two fixed lines still need today's balance reading.
 *
 * The interval is [dayStartMs, dayEndMs): the end is exclusive so a reading
 * stored exactly at midnight belongs to the next day, not to today.
 */
object BalanceReadingReminder {

    const val MONTHLY_LINE_ID = 1
    const val PREPAID_LINE_ID = 2

    val TRACKED_LINE_IDS = listOf(MONTHLY_LINE_ID, PREPAID_LINE_ID)

    fun missingLineIds(
        readings: List<BalanceReadingSample>,
        dayStartMs: Long,
        dayEndMs: Long
    ): List<Int> = TRACKED_LINE_IDS.filterNot { lineId ->
        readings.any { reading ->
            reading.planId == lineId &&
                reading.timestampMs >= dayStartMs &&
                reading.timestampMs < dayEndMs
        }
    }
}
