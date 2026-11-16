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
    val lastAutoReading: UssdAutoReading? = null,
    /** Line whose USSD consultation is currently in flight, if any. */
    val consultingLineId: Int? = null
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

    /**
     * Cycle day for the monthly line: today's calendar day plus the
     * correction implied by the user's last "Ajustar Días". The correction
     * is recovered from the stored pair (customCurrentDay,
     * purchaseTimestamp) — the anchor was chosen so the chosen day fell on
     * the adjustment date — and survives month changes without ever
     * freezing the day. A null customCurrentDay means "follow the calendar".
     */
    private fun line1CycleDay(plan: DataPlanEntity): Int {
        val correction = plan.customCurrentDay?.let { chosen ->
            val adjustedOn = Calendar.getInstance().apply {
                timeInMillis = plan.purchaseTimestamp + (chosen - 1) * DAY_MS
            }
            chosen - adjustedOn.get(Calendar.DAY_OF_MONTH)
        } ?: 0
        return (currentDayOfMonth() + correction).coerceIn(1, currentMonthTotalDays())
    }

    /** The day the UI currently shows for a line, used when logging readings. */
    private fun displayedCycleDay(lineId: Int, plan: DataPlanEntity): Int =
        if (lineId == 1) line1CycleDay(plan) else cycleDayOf(plan, plan.totalCycleDays)

    private val _simulatedDailyMBLine1 = MutableStateFlow<Double?>(null)
    private val _simulatedDailyMBLine2 = MutableStateFlow<Double?>(null)

    private val _customTotalDaysLine2 = MutableStateFlow<Int?>(null)

    private val _lastAutoReading = MutableStateFlow<UssdAutoReading?>(null)

    /** Line with a consultation in flight (drives the button progress state). */
    private val _consultingLineId = MutableStateFlow<Int?>(null)

    /** USSD consultation the user started and is still waiting for. */
    private data class PendingUssd(
        val lineId: Int,
        val startedAtMs: Long,
        val sessionId: Long
    )

    @Volatile
    private var pendingUssd: PendingUssd? = null
    private var nextUssdSessionId = 0L

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
     * Starts an automatic USSD consultation for [lineId] and returns its
     * session id. When [armAccessibility] is false the system telephony API is
     * being tried first; call [enableAccessibilityFallback] if it fails.
     * The UI is expected to dial the USSD code when no system API is used.
     */
    fun startUssdConsultation(lineId: Int, armAccessibility: Boolean = true): Long {
        val pending = PendingUssd(
            lineId = lineId,
            startedAtMs = System.currentTimeMillis(),
            sessionId = ++nextUssdSessionId
        )
        pendingUssd = pending
        UssdCaptureHolder.clear()
        if (armAccessibility) UssdCaptureHolder.arm() else UssdCaptureHolder.disarm()
        // Only the automatic API wait is shown as progress; once the dialer /
        // accessibility fallback takes over, the UI goes back to idle.
        if (!armAccessibility) _consultingLineId.value = lineId
        viewModelScope.launch {
            delay(USSD_CONSULTATION_TIMEOUT_MS)
            if (pendingUssd?.sessionId == pending.sessionId) {
                pendingUssd = null
                UssdCaptureHolder.disarm()
                _consultingLineId.compareAndSet(lineId, null)
            }
        }
        return pending.sessionId
    }

    /**
     * Switches an in-flight consultation to the accessibility catcher after
     * the system telephony API reports that it cannot handle the request.
     */
    fun enableAccessibilityFallback(lineId: Int, sessionId: Long) {
        val pending = pendingUssd ?: return
        if (pending.lineId != lineId || pending.sessionId != sessionId) return
        UssdCaptureHolder.clear()
        UssdCaptureHolder.arm()
        // The automatic attempt is over: the dialer is now in charge.
        _consultingLineId.compareAndSet(lineId, null)
    }

    /**
     * Applies a response received directly from the system telephony API.
     * Stale callbacks from previous consultations are ignored.
     */
    fun applyUssdReading(lineId: Int, text: String, sessionId: Long) {
        val pending = pendingUssd ?: return
        if (pending.lineId != lineId || pending.sessionId != sessionId) return
        pendingUssd = null
        UssdCaptureHolder.disarm()
        viewModelScope.launch { applyUssdCapture(lineId, text) }
    }

    /** Cancels an in-flight consultation (e.g. silent auto-read that cannot proceed). */
    fun cancelUssdConsultation(lineId: Int, sessionId: Long) {
        val pending = pendingUssd ?: return
        if (pending.lineId != lineId || pending.sessionId != sessionId) return
        pendingUssd = null
        UssdCaptureHolder.disarm()
        _consultingLineId.compareAndSet(lineId, null)
    }

    /** Parses and saves a captured USSD response onto its line. */
    private suspend fun applyUssdCapture(lineId: Int, text: String) {
        val mb = UssdParser.parseDataBalanceMB(text)
        val daysRemaining = UssdParser.parseDaysRemaining(text)
        if (mb == null && daysRemaining == null) return
        _consultingLineId.compareAndSet(lineId, null)

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

        val day = displayedCycleDay(lineId, updated)
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
        combine(_simulatedDailyMBLine1, _simulatedDailyMBLine2, _consultingLineId) { s1, s2, consulting ->
            Triple(s1, s2, consulting)
        }
    ) { p1, p2, logs, lastAutoReading, simulations ->
        val sim1 = simulations.first
        val sim2 = simulations.second
        val consultingLineId = simulations.third
        val plan1 = p1 ?: DataPlanEntity(id = 1, totalCycleDays = currentMonthTotalDays())
        val plan2 = p2 ?: DataPlanEntity(id = 2, totalCycleDays = 35, packageTotalMB = 6144.0, remainingBalanceMB = 6144.0, planType = PREPAID_PLAN_TYPE, operatorName = "Línea 2 (Prepago)")

        // Line 1 calculation — the monthly cycle follows the CALENDAR month,
        // so its length automatically matches the real month (28/29/30/31
        // days, February and leap years included).
        val totalDays1 = currentMonthTotalDays()
        // The day is today's calendar day plus the correction the user last
        // set with "Ajustar Días" (if any). It advances on its own and never
        // freezes.
        val currentDay1 = line1CycleDay(plan1)

        val forecastLine1 = DataForecastCalculator.calculate(
            packageTotalMB = plan1.packageTotalMB,
            remainingBalanceMB = plan1.remainingBalanceMB,
            totalCycleDays = totalDays1,
            daysElapsed = currentDay1,
            simulatedDailyMB = sim1
        )

        // Line 2 (Prepago 35 días) calculation:
        val totalDays2 = _customTotalDaysLine2.value ?: plan2.totalCycleDays
        // The prepaid day flows from the purchase timestamp; customCurrentDay
        // is ignored so it can never freeze the cycle. No upper clamp: once
        // the purchase is more than totalDays2 old the calculator reports
        // daysRemaining <= 0 so "Vencido" surfaces.
        val daysFromPurchase = maxOf(1, ((System.currentTimeMillis() - plan2.purchaseTimestamp) / DAY_MS).toInt() + 1)
        val currentDay2 = daysFromPurchase

        val forecastLine2 = DataForecastCalculator.calculate(
            packageTotalMB = plan2.packageTotalMB,
            remainingBalanceMB = plan2.remainingBalanceMB,
            totalCycleDays = totalDays2,
            daysElapsed = currentDay2,
            simulatedDailyMB = sim2
        )

        val expireCal = Calendar.getInstance().apply {
            // Day 1 is the purchase day itself, so the last day of the cycle
            // falls (totalDays - 1) days after it. Keeps the "Vence:" date in
            // sync with the operator's countdown ("validos 14 dias" -> the
            // package dies 13 days from today, not 14).
            timeInMillis = plan2.purchaseTimestamp + ((totalDays2 - 1).coerceAtLeast(0).toLong() * DAY_MS)
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
                plan = plan2.copy(customCurrentDay = currentDay2.coerceIn(1, totalDays2), totalCycleDays = totalDays2),
                forecast = forecastLine2,
                displayUnit = plan2.displayUnit,
                simulatedDailyMB = sim2,
                expirationDateFormatted = expirationFormatted
            ),
            logs = logs,
            calendarCurrentDay = currentDayOfMonth(),
            calendarTotalDays = currentMonthTotalDays(),
            lastAutoReading = lastAutoReading,
            consultingLineId = consultingLineId
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

            if (lineId == 2) {
                _customTotalDaysLine2.value = safeDays
            }

            val updated = current.copy(
                packageTotalMB = safePackage,
                remainingBalanceMB = safeRemaining,
                // The monthly line's length is always the real calendar
                // month; only the prepaid line keeps a configurable length.
                totalCycleDays = if (lineId == 1) currentMonthTotalDays() else safeDays,
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
            // Sensible empty state for a prepaid line: nothing accumulated
            // yet, so a first purchase starts from a clean slate.
            val current = repository.getDataPlan(lineId).firstOrNull()
                ?: DataPlanEntity(
                    id = lineId,
                    planType = PREPAID_PLAN_TYPE,
                    packageTotalMB = 0.0,
                    remainingBalanceMB = 0.0,
                    totalCycleDays = totalDays
                )
            val safePackage = packageTotalMB.coerceAtLeast(100.0)

            // The purchase date defaults to today; when a past date is given,
            // the cycle starts on that day and elapsed days are accounted for.
            val purchaseTs = purchaseDateMillis ?: System.currentTimeMillis()
            val daysSincePurchase = ((System.currentTimeMillis() - purchaseTs) / DAY_MS).toInt().coerceAtLeast(0)
            val cycleDay = (daysSincePurchase + 1).coerceIn(1, totalDays)

            // Day 35 is the deadline: if the old cycle already ran out, the
            // leftover balance was forfeited and the new cycle starts fresh.
            val oldCycleDay = ((System.currentTimeMillis() - current.purchaseTimestamp) / DAY_MS).toInt() + 1
            val previousExpired = current.totalCycleDays > 0 && oldCycleDay > current.totalCycleDays

            _customTotalDaysLine2.value = totalDays

            val updated = if (previousExpired) {
                current.copy(
                    packageTotalMB = safePackage,
                    remainingBalanceMB = safePackage,
                    totalCycleDays = totalDays,
                    customCurrentDay = cycleDay,
                    purchaseTimestamp = purchaseTs,
                    planName = "Paquete ${DataFormatters.format(safePackage, current.displayUnit)} ($totalDays días)"
                )
            } else {
                // The purchased capacity is ADDED to whatever balance was
                // left, and the package total grows by the same amount so
                // the consumption history stays coherent.
                current.copy(
                    packageTotalMB = current.packageTotalMB + safePackage,
                    remainingBalanceMB = current.remainingBalanceMB + safePackage,
                    totalCycleDays = totalDays,
                    customCurrentDay = cycleDay,
                    purchaseTimestamp = purchaseTs,
                    planName = "Paquete ${DataFormatters.format(current.packageTotalMB + safePackage, current.displayUnit)} ($totalDays días)"
                )
            }
            repository.saveOrUpdatePlan(updated)
            repository.addLog(
                lineId,
                updated.remainingBalanceMB,
                cycleDay,
                "Compra nuevo paquete: ${DataFormatters.format(safePackage, current.displayUnit)}" +
                    (if (previousExpired) " (el paquete anterior venció: el saldo restante se perdió)" else "") +
                    (if (purchaseDateMillis != null) " (comprado el ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(purchaseDateMillis))})" else "")
            )
        }
    }

    fun updateDays(lineId: Int, currentDay: Int, totalCycleDays: Int) {
        if (lineId == 2) {
            _customTotalDaysLine2.value = totalCycleDays
        }
        viewModelScope.launch {
            val current = repository.getDataPlan(lineId).firstOrNull() ?: DataPlanEntity(id = lineId)
            repository.saveOrUpdatePlan(
                current.copy(
                    customCurrentDay = currentDay,
                    // The monthly line's length is always the real calendar
                    // month; only the prepaid line keeps a configurable length.
                    totalCycleDays = if (lineId == 1) currentMonthTotalDays() else totalCycleDays,
                    // Anchor the adjustment so the implied calendar-day
                    // correction (chosen day vs. the adjustment date) can be
                    // recovered later; the day still advances on its own.
                    purchaseTimestamp = System.currentTimeMillis() - (currentDay - 1) * DAY_MS
                )
            )
        }
    }

    fun resetToRealCalendar(lineId: Int) {
        if (lineId == 1) {
            viewModelScope.launch {
                val current = repository.getDataPlan(1).firstOrNull() ?: return@launch
                repository.saveOrUpdatePlan(
                    current.copy(
                        // Clearing the correction puts the monthly line back
                        // in pure calendar mode: day = today's calendar day,
                        // length = the real current month (28/29/30/31).
                        customCurrentDay = null,
                        totalCycleDays = currentMonthTotalDays(),
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

    /**
     * Stores the physical SIM slot (0-based) that should answer the USSD
     * consultation for [lineId]. Null means "system default".
     */
    fun updateSimSlot(lineId: Int, simSlotIndex: Int?) {
        viewModelScope.launch {
            val current = repository.getDataPlan(lineId).firstOrNull() ?: return@launch
            repository.saveOrUpdatePlan(
                current.copy(simSlotIndex = simSlotIndex?.takeIf { it >= 0 })
            )
        }
    }

    fun recordUssdReading(lineId: Int, remainingMB: Double, note: String = "Lectura por USSD") {
        viewModelScope.launch {
            val current = repository.getDataPlan(lineId).firstOrNull() ?: return@launch
            val safeMB = remainingMB.coerceIn(0.0, current.packageTotalMB)
            val day = displayedCycleDay(lineId, current)
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
