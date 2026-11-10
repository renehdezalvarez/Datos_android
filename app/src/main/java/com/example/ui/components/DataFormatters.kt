package com.example.ui.components

import com.example.domain.DataForecastCalculator
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

object DataFormatters {
    private val gbFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.getDefault()))
    private val compactGbFormat = DecimalFormat("#,##0.0", DecimalFormatSymbols(Locale.getDefault()))
    private val mbFormat = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.getDefault()))

    /**
     * Formats an amount of MB for display.
     *
     * @param forcedUnit When null (the default) the unit is chosen
     * automatically: MB below 1000, GB above. Pass "MB" to force megabytes.
     */
    fun format(mb: Double, forcedUnit: String? = null): String {
        return if (forcedUnit == "MB" || (forcedUnit == null && mb < 1000.0)) {
            "${mbFormat.format(mb)} MB"
        } else {
            val gb = mb / 1024.0
            if (gb >= 10.0) {
                "${compactGbFormat.format(gb)} GB"
            } else {
                "${gbFormat.format(gb)} GB"
            }
        }
    }

    fun formatOnlyNumber(mb: Double, forcedUnit: String? = null): String {
        return if (forcedUnit == "MB" || (forcedUnit == null && mb < 1000.0)) {
            mbFormat.format(mb)
        } else {
            val gb = mb / 1024.0
            gbFormat.format(gb)
        }
    }

    fun getUnit(mb: Double, forcedUnit: String? = null): String {
        return if (forcedUnit == "MB" || (forcedUnit == null && mb < 1000.0)) {
            "MB"
        } else {
            "GB"
        }
    }

    fun formatDays(days: Double): String {
        return if (days >= DataForecastCalculator.DAYS_INDEFINITE - 9.0) {
            // Anything close to the "indefinite" sentinel means the balance
            // comfortably outlasts the cycle.
            "Todo el ciclo"
        } else if (days >= 10.0) {
            "${days.roundToInt()} días"
        } else {
            val formatted = DecimalFormat("#0.0", DecimalFormatSymbols(Locale.getDefault())).format(days)
            "$formatted días"
        }
    }

    fun percent(ratio: Float): String {
        val pct = (ratio * 100).toInt()
        return "$pct%"
    }
}
