package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.DataBalanceLogEntity
import com.example.data.model.DataPlanEntity
import com.example.data.repository.DataPlanRepository
import com.example.domain.DataForecastCalculator
import com.example.domain.DataForecastResult
import com.example.domain.UssdParser
import com.example.ui.components.DataFormatters
import com.example.ussd.UssdCaptureHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class SingleLineState(
    val plan: DataPlanEntity,
    val forecast: DataForecastResult,
    val displayUnit: String = "GB",
    val simulatedDailyMB: Double? = null,
    val expirationDateFormatted: String = ""
)

/** Result of an automatically interpreted USSD response. */
data class UssdAutoReading(
    val id: Long = System.currentTimeMillis(),
    val lineId: Int,
    val remainingMB: Double?,
    val daysRemaining: Int?,
    val newCycleDay: Int?,
    val rawText: String
)

data class DataForecastUiState(
    val line1: SingleLineState = SingleLineState(
        plan = DataPlanEntity(id = 1),
        forecast = DataForecastCalculator.calculate(10240.0, 10240.0, 30, 1)
    ),
    val line2: SingleLineState = SingleLineState(
        plan = DataPlanEntity(id = 2, totalCycleDays = 35, planType = "PREPAID_35", operatorName = "Línea 2 (Prepago)"),
        forecast = DataForecastCalculator.calculate(6144.0, 6144.0, 35, 1)
    ),
    val logs: List<DataBalanceLogEntity> = emptyList(),
    val calendarCurrentDay: Int = 1,
    val calendarTotalDays: Int = 30,
    val lastAutoReading: UssdAutoReading? = null
)

class DataForecastViewModel(
    private val repository: DataPlanRepository
) : ViewModel() {

    companion object {
        const val PREPAID_PLAN_TYPE = "PREPAID_35"
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val USSD_CONSULTATION_TIMEOUT_MS = 90_000L

        fun provideFactory(repository: DataPlanRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DataForecastViewModel(repository) as T
                }
            }
    }

    // Computed on each access so the app survives month changes while open.
    private fun currentDayOfMonth(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    private fun currentMonthTotalDays(): Int = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)

    /** Cycle day implied by the plan's cycle-start timestamp, 1..totalDays. */
    private fun cycleDayOf(plan: DataPlanEntity, totalDays: Int): Int {
        val fromStart = ((System.currentTimeMillis() - plan.purchaseTimestamp) / DAY_MS).toInt() + 1
        return fromStart.coerceIn(1, totalDays)
    }

    private val _simulatedDailyMBLine1 = MutableStateFlow<Double?>(null)
    private val _simulatedDailyMBLine2 = MutableStateFlow<Double?>(null)

    private val _customTotalDaysLine1 = MutableStateFlow<Int?>(null)

    private val _customTotalDaysLine2 = MutableStateFlow<Int?>(null)

    private val _lastAutoReading = MutableStateFlow<UssdAutoReading?>(null)

    /** USSD consultation the user started and is still waiting for. */
    private data class PendingUssd(val lineId: Int, val startedAtMs: Long)

    @Volatile
    private var pendingUssd: PendingUssd? = null

    init {
        viewModelScope.launch {
            val existingPlan1 = repository.getDataPlan(1).firstOrNull()
            val existingPlan2 = repository.getDataPlan(2).firstOrNull()
            if (existingPlan1 == null || existingPlan2 == null) {
                repository.seedInitialDataIfEmpty()
            }
        }

        // Apply every captured USSD response to the line that started the
        // consultation. Captures are ignored when nobody is waiting for them.
        viewModelScope.launch {
            UssdCaptureHolder.lastCapture.collect { capture ->
                val pending = pendingUssd ?: return@collect
                if (capture == null || capture.timestampMs < pending.startedAtMs) return@collect
                pendingUssd = null
                UssdCaptureHolder.disarm()
                applyUssdCapture(pending.lineId, capture.text)
            }
        }
    }

    /**
     * Starts an automatic USSD consultation for [lineId]: arms the catcher,
     * remembers which line is waiting and times out after 90 seconds.
     * The UI is expected to dial the USSD code right after calling this.
     */
    fun startUssdConsultation(lineId: Int) {
        val pending = PendingUssd(lineId, System.currentTimeMillis())
        pendingUssd = pending
        UssdCaptureHolder.clear()
        UssdCaptureHolder.arm()
        viewModelScope.launch {
            delay(USSD_CONSULTATION_TIMEOUT_MS)
            if (pendingUssd?.startedAtMs == pending.startedAtMs) {
                pendingUssd = null
                UssdCaptureHolder.disarm()
            }
        }
    }

    /** Parses and saves a captured USSD response onto its line. */
    private suspend fun applyUssdCapture(lineId: Int, text: String) {
        val mb = UssdParser.parseDataBalanceMB(text)
        val daysRemaining = UssdParser.parseDaysRemaining(text)
        if (mb == null && daysRemaining == null) return

        val current = repository.getDataPlan(lineId).firstOrNull() ?: return
        var updated = current
        var newCycleDay: Int? = null

        if (mb != null) {
            updated = updated.copy(remainingBalanceMB = mb.coerceIn(0.0, updated.packageTotalMB))
        }

        if (daysRemaining != null && updated.planType == PREPAID_PLAN_TYPE) {
            // "quedan N días" of a totalCycleDays package -> day = total - remaining + 1.
            newCycleDay = (updated.totalCycleDays - daysRemaining + 1)
                .coerceIn(1, updated.totalCycleDays)
            updated = updated.copy(
                customCurrentDay = newCycleDay,
                // Keep the purchase timestamp consistent with the reading so
                // the countdown keeps flowing on its own afterwards.
                purchaseTimestamp = System.currentTimeMillis() - (newCycleDay - 1) * DAY_MS
            )
        }

        repository.saveOrUpdatePlan(updated)

        val day = cycleDayOf(updated, updated.totalCycleDays)
        repository.addLog(
            lineId,
            updated.remainingBalanceMB,
            day,
            "Lectura USSD automática: ${text.replace('\n', ' ').take(120)}"
        )

        _lastAutoReading.value = UssdAutoReading(
            lineId = lineId,
            remainingMB = mb?.let { it.coerceIn(0.0, updated.packageTotalMB) },
            daysRemaining = daysRemaining,
            newCycleDay = newCycleDay,
            rawText = text.trim()
        )
    }

    val uiState: StateFlow<DataForecastUiState> = combine(
        repository.getDataPlan(1),
        repository.getDataPlan(2),
        repository.getAllLogs(),
        _lastAutoReading,
        combine(_simulatedDailyMBLine1, _simulatedDailyMBLine2) { s1, s2 -> Pair(s1, s2) }
    ) { p1, p2, logs, lastAutoReading, simulations ->
        val (sim1, sim2) = simulations
        val plan1 = p1 ?: DataPlanEntity(
            id = 1,
            totalCycleDays = currentMonthTotalDays(),
            // Anchored to day 1 of the month so the computed day matches
            // today's calendar day.
            purchaseTimestamp = System.currentTimeMillis() - (currentDayOfMonth() - 1) * DAY_MS
        )
        val plan2 = p2 ?: DataPlanEntity(id = 2, totalCycleDays = 35, packageTotalMB = 6144.0, remainingBalanceMB = 6144.0, planType = PREPAID_PLAN_TYPE, operatorName = "Línea 2 (Prepago)")

        // Line 1 calculation
        val totalDays1 = _customTotalDaysLine1.value ?: plan1.totalCycleDays
        // Days elapsed flow from the cycle start timestamp (updated whenever
        // the user adjusts the cycle), so the day advances on its own on
        // later days. Falls back to the calendar day for fresh installs.
        val daysFromCycleStart1 = ((System.currentTimeMillis() - plan1.purchaseTimestamp) / DAY_MS).toInt() + 1
        val currentDay1 = daysFromCycleStart1.coerceIn(1, totalDays1)

        val forecastLine1 = DataForecastCalculator.calculate(
            packageTotalMB = plan1.packageTotalMB,
            remainingBalanceMB = plan1.remainingBalanceMB,
            totalCycleDays = totalDays1,
            daysElapsed = currentDay1,
            simulatedDailyMB = sim1
        )

        // Line 2 (Prepago 35 días) calculation:
        val totalDays2 = _customTotalDaysLine2.value ?: plan2.totalCycleDays
        // Same as line 1: the day always flows from the purchase timestamp;
        // customCurrentDay is ignored so it can never freeze the cycle.
        val daysFromPurchase = maxOf(1, ((System.currentTimeMillis() - plan2.purchaseTimestamp) / DAY_MS).toInt() + 1)
        val currentDay2 = daysFromPurchase.coerceIn(1, totalDays2)

        val forecastLine2 = DataForecastCalculator.calculate(
            packageTotalMB = plan2.packageTotalMB,
            remainingBalanceMB = plan2.remainingBalanceMB,
            totalCycleDays = totalDays2,
            daysElapsed = currentDay2,
            simulatedDailyMB = sim2
        )

        val expireCal = Calendar.getInstance().apply {
            timeInMillis = plan2.purchaseTimestamp + (totalDays2.toLong() * 24L * 60L * 60L * 1000L)
        }
        val expDateFormat = java.text.SimpleDateFormat("dd 'de' MMMM", java.util.Locale("es", "ES"))
        val expirationFormatted = expDateFormat.format(expireCal.time)

        DataForecastUiState(
            line1 = SingleLineState(
                plan = plan1.copy(customCurrentDay = currentDay1, totalCycleDays = totalDays1),
                forecast = forecastLine1,
                displayUnit = plan1.displayUnit,
                simulatedDailyMB = sim1,
                expirationDateFormatted = "Fin de mes"
            ),
            line2 = SingleLineState(
                plan = plan2.copy(customCurrentDay = currentDay2, totalCycleDays = totalDays2),
                forecast = forecastLine2,
                displayUnit = plan2.displayUnit,
                simulatedDailyMB = sim2,
                expirationDateFormatted = expirationFormatted
            ),
            logs = logs,
            calendarCurrentDay = currentDayOfMonth(),
            calendarTotalDays = currentMonthTotalDays(),
            lastAutoReading = lastAutoReading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DataForecastUiState()
    )

    fun updatePackageConfiguration(
        lineId: Int,
        packageTotalMB: Double,
        remainingBalanceMB: Double? = null,
        totalCycleDays: Int? = null,
        operatorName: String? = null
    ) {
        viewModelScope.launch {
            val current = repository.getDataPlan(lineId).firstOrNull() ?: DataPlanEntity(id = lineId)
            val safePackage = packageTotalMB.coerceAtLeast(100.0)
            val safeRemaining = (remainingBalanceMB ?: current.remainingBalanceMB).coerceIn(0.0, safePackage)
            val defaultDays = if (lineId == 2) 35 else 30
            val safeDays = (totalCycleDays ?: current.totalCycleDays.takeIf { it > 0 } ?: defaultDays).coerceAtLeast(1)

            if (lineId == 1) {
                _customTotalDaysLine1.value = safeDays
            } else {
                _customTotalDaysLine2.value = safeDays
            }

            val updated = current.copy(
                packageTotalMB = safePackage,
                remainingBalanceMB = safeRemaining,
                totalCycleDays = safeDays,
                operatorName = operatorName ?: current.operatorName,
                planName = "Paquete ${DataFormatters.format(safePackage, current.displayUnit)}"
            )
            repository.saveOrUpdatePlan(updated)
        }
    }

    fun registerNewPrepaidPurchase(
        lineId: Int = 2,
        packageTotalMB: Double,
        totalDays: Int = 35,
        purchaseDateMillis: Long? = null
    ) {
        viewModelScope.launch {
            val current = repository.getDataPlan(lineId).firstOrNull()
                ?: DataPlanEntity(id = lineId, planType = PREPAID_PLAN_TYPE)
            val safePackage = packageTotalMB.coerceAtLeast(100.0)

            // The purchase date defaults to today; when a past date is given,
            // the cycle starts on that day and elapsed days are accounted for.
            val purchaseTs = purchaseDateMillis ?: System.currentTimeMillis()
            val daysSincePurchase = ((System.currentTimeMillis() - purchaseTs) / DAY_MS).toInt().coerceAtLeast(0)
            val cycleDay = (daysSincePurchase + 1).coerceIn(1, totalDays)

            _customTotalDaysLine2.value = totalDays

            // The purchased capacity is ADDED to whatever balance was left,
            // and the package total grows by the same amount so the
            // consumption history stays coherent.
            val updated = current.copy(
                packageTotalMB = current.packageTotalMB + safePackage,
                remainingBalanceMB = current.remainingBalanceMB + safePackage,
                totalCycleDays = totalDays,
                customCurrentDay = cycleDay,
                purchaseTimestamp = purchaseTs,
                planName = "Paquete ${DataFormatters.format(current.packageTotalMB + safePackage, current.displayUnit)} ($totalDays días)"
            )
            repository.saveOrUpdatePlan(updated)
            repository.addLog(
                lineId,
                updated.remainingBalanceMB,
                cycleDay,
                "Compra nuevo paquete: ${DataFormatters.format(safePackage, current.displayUnit)}" +
                    (if (purchaseDateMillis != null) " (comprado el ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(purchaseDateMillis))})" else "")
            )
        }
    }

    fun updateDays(lineId: Int, currentDay: Int, totalCycleDays: Int) {
        if (lineId == 1) {
            _customTotalDaysLine1.value = totalCycleDays
        } else {
            _customTotalDaysLine2.value = totalCycleDays
        }
        viewModelScope.launch {
            val current = repository.getDataPlan(lineId).firstOrNull() ?: DataPlanEntity(id = lineId)
            repository.saveOrUpdatePlan(
                current.copy(
                    customCurrentDay = currentDay,
                    totalCycleDays = totalCycleDays,
                    // Anchor the cycle start so the day keeps advancing on its
                    // own on later days instead of staying frozen at the
                    // manually chosen value.
                    purchaseTimestamp = System.currentTimeMillis() - (currentDay - 1) * DAY_MS
                )
            )
        }
    }

    fun resetToRealCalendar(lineId: Int) {
        if (lineId == 1) {
            _customTotalDaysLine1.value = null
            viewModelScope.launch {
                val current = repository.getDataPlan(1).firstOrNull() ?: return@launch
                repository.saveOrUpdatePlan(
                    current.copy(
                        customCurrentDay = null,
                        totalCycleDays = currentMonthTotalDays(),
                        // Re-anchor the cycle to the real calendar: day 1 of
                        // the month, so the computed day matches today.
                        purchaseTimestamp = System.currentTimeMillis() - (currentDayOfMonth() - 1) * DAY_MS
                    )
                )
            }
        } else {
            // For Line 2 prepaid: reset to 35 days from purchase
            _customTotalDaysLine2.value = null
            viewModelScope.launch {
                val current = repository.getDataPlan(2).firstOrNull() ?: return@launch
                repository.saveOrUpdatePlan(
                    current.copy(
                        customCurrentDay = null,
                        totalCycleDays = 35
                    )
                )
            }
        }
    }

    fun toggleDisplayUnit(lineId: Int) {
        viewModelScope.launch {
            val current = repository.getDataPlan(lineId).firstOrNull() ?: return@launch
            val newUnit = if (current.displayUnit == "GB") "MB" else "GB"
            repository.saveOrUpdatePlan(current.copy(displayUnit = newUnit))
        }
    }

    fun setSimulatedDailyMB(lineId: Int, dailyMB: Double?) {
        if (lineId == 1) {
            _simulatedDailyMBLine1.value = dailyMB
        } else {
            _simulatedDailyMBLine2.value = dailyMB
        }
    }

    fun addManualLog(lineId: Int, remainingMB: Double, cycleDay: Int, note: String) {
        viewModelScope.launch {
            repository.addLog(lineId, remainingMB, cycleDay, note)
            val current = repository.getDataPlan(lineId).firstOrNull() ?: return@launch
            // Only the balance is updated: a backfilled reading for a past
            // day must not move the cycle anchor.
            repository.saveOrUpdatePlan(current.copy(remainingBalanceMB = remainingMB))
        }
    }

    fun updateUssdCode(lineId: Int, ussdCode: String) {
        viewModelScope.launch {
            val current = repository.getDataPlan(lineId).firstOrNull() ?: return@launch
            repository.saveOrUpdatePlan(current.copy(ussdCode = ussdCode.trim()))
        }
    }

    fun recordUssdReading(lineId: Int, remainingMB: Double, note: String = "Lectura por USSD") {
        viewModelScope.launch {
            val current = repository.getDataPlan(lineId).firstOrNull() ?: return@launch
            val safeMB = remainingMB.coerceIn(0.0, current.packageTotalMB)
            val day = cycleDayOf(current, current.totalCycleDays)
            repository.saveOrUpdatePlan(current.copy(remainingBalanceMB = safeMB))
            repository.addLog(lineId, safeMB, day, note)
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            repository.deleteLog(id)
        }
    }
}
