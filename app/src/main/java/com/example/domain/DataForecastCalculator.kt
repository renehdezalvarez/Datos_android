package com.example.domain

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class DataPaceStatus {
    SURPLUS_SAFE,       // Will comfortably reach month-end (> 20% remaining at end)
    ON_TRACK,          // Will reach month-end with tight margin
    RISK_EXHAUSTION,   // Will run out 1 to 5 days before month-end
    CRITICAL_DEPLETED  // Will run out quickly (> 5 days before month-end) or already depleted
}

data class DataActivityAllowance(
    val title: String,
    val iconType: String,
    val mbPerHour: Double,
    val hoursAvailableTotal: Double,
    val minutesPerDayRemaining: Int
)

data class DataForecastResult(
    val packageTotalMB: Double,
    val remainingBalanceMB: Double,
    val consumedMB: Double,
    val totalCycleDays: Int,
    val daysElapsed: Int,
    val daysRemaining: Int,
    // Daily rates
    val dailyBurnRateMB: Double,
    val safeDailyAllowanceMB: Double,
    // Projection
    val willLastWholeMonth: Boolean,
    val daysOfDataLeft: Double,
    val exhaustionCycleDay: Int?,
    val projectedDeficitOrSurplusMB: Double, // positive = surplus left at end, negative = shortage
    val percentDataRemaining: Float,
    val percentTimeElapsed: Float,
    val paceStatus: DataPaceStatus,
    val simulatedDailyMB: Double? = null,
    val simulatedDaysLeft: Double? = null,
    val simulatedEndBalanceMB: Double? = null,
    val activities: List<DataActivityAllowance> = emptyList()
)

object DataForecastCalculator {

    /** Sentinel value used when there is remaining balance but no consumption yet. */
    const val DAYS_INDEFINITE = 999.0

    fun calculate(
        packageTotalMB: Double,
        remainingBalanceMB: Double,
        totalCycleDays: Int,
        daysElapsed: Int,
        simulatedDailyMB: Double? = null
    ): DataForecastResult {
        val safeTotalDays = max(1, totalCycleDays)
        val safeElapsed = min(max(1, daysElapsed), safeTotalDays)
        val safeRemainingDays = max(0, safeTotalDays - safeElapsed)

        val safePackageMB = max(100.0, packageTotalMB)
        val safeRemainingMB = min(safePackageMB, max(0.0, remainingBalanceMB))
        val consumedMB = max(0.0, safePackageMB - safeRemainingMB)

        // Consumo diario promedio actual
        val dailyBurnRateMB = if (safeElapsed > 0) consumedMB / safeElapsed else 0.0

        // Cuota diaria segura para que alcance exactamente los días restantes
        val safeDailyAllowanceMB = if (safeRemainingDays > 0) {
            safeRemainingMB / safeRemainingDays
        } else {
            safeRemainingMB
        }

        // ¿Cuántos días rinde el saldo restante al ritmo actual?
        val daysOfDataLeft = if (dailyBurnRateMB > 0) {
            safeRemainingMB / dailyBurnRateMB
        } else if (safeRemainingMB > 0) {
            DAYS_INDEFINITE // No consumption yet, lasts indefinitely
        } else {
            0.0
        }

        // ¿Alcanzará para el mes completo?
        val willLastWholeMonth = daysOfDataLeft >= safeRemainingDays

        // Proyección de saldo a fin de mes
        val projectedRemainingSpendMB = dailyBurnRateMB * safeRemainingDays
        val projectedDeficitOrSurplusMB = safeRemainingMB - projectedRemainingSpendMB

        // Día del ciclo en que se agotará
        val exhaustionCycleDay = if (willLastWholeMonth) {
            null
        } else {
            val day = safeElapsed + floor(daysOfDataLeft).toInt()
            min(day, safeTotalDays)
        }

        // Porcentajes
        val percentDataRemaining = (safeRemainingMB / safePackageMB).toFloat().coerceIn(0f, 1f)
        val percentTimeElapsed = (safeElapsed.toFloat() / safeTotalDays.toFloat()).coerceIn(0f, 1f)

        // Estado del semáforo
        val paceStatus = when {
            safeRemainingMB <= 0.0 -> DataPaceStatus.CRITICAL_DEPLETED
            willLastWholeMonth && projectedDeficitOrSurplusMB >= (safePackageMB * 0.15) -> DataPaceStatus.SURPLUS_SAFE
            willLastWholeMonth -> DataPaceStatus.ON_TRACK
            daysOfDataLeft >= (safeRemainingDays - 4) -> DataPaceStatus.RISK_EXHAUSTION
            else -> DataPaceStatus.CRITICAL_DEPLETED
        }

        // Simulación what-if
        val (simDaysLeft, simEndBalance) = if (simulatedDailyMB != null && simulatedDailyMB > 0.0) {
            val dLeft = safeRemainingMB / simulatedDailyMB
            val endBal = safeRemainingMB - (simulatedDailyMB * safeRemainingDays)
            Pair(dLeft, endBal)
        } else {
            Pair(null, null)
        }

        // Estimación de actividades con el saldo restante y cuota diaria
        val activities = listOf(
            DataActivityAllowance(
                title = "Video HD (YouTube / Netflix)",
                iconType = "video",
                mbPerHour = 1500.0,
                hoursAvailableTotal = safeRemainingMB / 1500.0,
                minutesPerDayRemaining = if (safeRemainingDays > 0) ((safeDailyAllowanceMB / 1500.0) * 60).roundToInt() else 0
            ),
            DataActivityAllowance(
                title = "Redes Sociales (TikTok / Reels / IG)",
                iconType = "social",
                mbPerHour = 250.0,
                hoursAvailableTotal = safeRemainingMB / 250.0,
                minutesPerDayRemaining = if (safeRemainingDays > 0) ((safeDailyAllowanceMB / 250.0) * 60).roundToInt() else 0
            ),
            DataActivityAllowance(
                title = "Música Online (Spotify / Deezer)",
                iconType = "music",
                mbPerHour = 60.0,
                hoursAvailableTotal = safeRemainingMB / 60.0,
                minutesPerDayRemaining = if (safeRemainingDays > 0) ((safeDailyAllowanceMB / 60.0) * 60).roundToInt() else 0
            ),
            DataActivityAllowance(
                title = "WhatsApp y Navegación Web",
                iconType = "chat",
                mbPerHour = 20.0,
                hoursAvailableTotal = safeRemainingMB / 20.0,
                minutesPerDayRemaining = if (safeRemainingDays > 0) ((safeDailyAllowanceMB / 20.0) * 60).roundToInt() else 0
            )
        )

        return DataForecastResult(
            packageTotalMB = safePackageMB,
            remainingBalanceMB = safeRemainingMB,
            consumedMB = consumedMB,
            totalCycleDays = safeTotalDays,
            daysElapsed = safeElapsed,
            daysRemaining = safeRemainingDays,
            dailyBurnRateMB = dailyBurnRateMB,
            safeDailyAllowanceMB = safeDailyAllowanceMB,
            willLastWholeMonth = willLastWholeMonth,
            daysOfDataLeft = daysOfDataLeft,
            exhaustionCycleDay = exhaustionCycleDay,
            projectedDeficitOrSurplusMB = projectedDeficitOrSurplusMB,
            percentDataRemaining = percentDataRemaining,
            percentTimeElapsed = percentTimeElapsed,
            paceStatus = paceStatus,
            simulatedDailyMB = simulatedDailyMB,
            simulatedDaysLeft = simDaysLeft,
            simulatedEndBalanceMB = simEndBalance,
            activities = activities
        )
    }
}
