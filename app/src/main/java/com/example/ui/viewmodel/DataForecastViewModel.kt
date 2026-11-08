package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.DataBalanceLogEntity
import com.example.data.model.DataPlanEntity
import com.example.data.repository.DataPlanRepository
import com.example.domain.DataForecastCalculator
import com.example.domain.DataForecastResult
import com.example.ui.components.DataFormatters
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

data class DataForecastUiState(
    val activeLineId: Int = 1, // 1 for Line 1, 2 for Line 2
    val line1: SingleLineState = SingleLineState(
        plan = DataPlanEntity(id = 1),
        forecast = DataForecastCalculator.calculate(10240.0, 3500.0, 30, 12)
    ),
    val line2: SingleLineState = SingleLineState(
        plan = DataPlanEntity(id = 2, totalCycleDays = 35, planType = "PREPAID_35", operatorName = "Línea 2 (Prepago)"),
        forecast = DataForecastCalculator.calculate(6144.0, 4200.0, 35, 8)
    ),
    val logs: List<DataBalanceLogEntity> = emptyList(),
    val calendarCurrentDay: Int = 1,
    val calendarTotalDays: Int = 30
)

class DataForecastViewModel(
    private val repository: DataPlanRepository
) : ViewModel() {

    private val calendar = Calendar.getInstance()
    private val realDay = calendar.get(Calendar.DAY_OF_MONTH)
    private val realTotalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    private val _activeLineId = MutableStateFlow(1)
    val activeLineId: StateFlow<Int> = _activeLineId

    private val _simulatedDailyMBLine1 = MutableStateFlow<Double?>(null)
    private val _simulatedDailyMBLine2 = MutableStateFlow<Double?>(null)

    private val _customDayLine1 = MutableStateFlow<Int?>(null)
    private val _customTotalDaysLine1 = MutableStateFlow<Int?>(null)

    private val _customDayLine2 = MutableStateFlow<Int?>(null)
    private val _customTotalDaysLine2 = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch {
            val existingPlan1 = repository.getDataPlan(1).firstOrNull()
            val existingPlan2 = repository.getDataPlan(2).firstOrNull()
            if (existingPlan1 == null || existingPlan2 == null) {
                repository.seedInitialDataIfEmpty()
            }
        }
    }

    val uiState: StateFlow<DataForecastUiState> = combine(
        repository.getDataPlan(1),
        repository.getDataPlan(2),
        repository.getAllLogs(),
        _activeLineId,
        combine(_simulatedDailyMBLine1, _simulatedDailyMBLine2) { s1, s2 -> Pair(s1, s2) }
    ) { p1, p2, logs, activeId, simulations ->
        val (sim1, sim2) = simulations
        val plan1 = p1 ?: DataPlanEntity(id = 1, totalCycleDays = realTotalDays, customCurrentDay = realDay)
        val plan2 = p2 ?: DataPlanEntity(id = 2, totalCycleDays = 35, packageTotalMB = 6144.0, remainingBalanceMB = 4200.0, planType = "PREPAID_35", operatorName = "Línea 2 (Prepago)")

        // Line 1 calculation
        val totalDays1 = _customTotalDaysLine1.value ?: plan1.totalCycleDays
        val currentDay1 = (_customDayLine1.value ?: plan1.customCurrentDay ?: realDay).coerceIn(1, totalDays1)

        val forecastLine1 = DataForecastCalculator.calculate(
            packageTotalMB = plan1.packageTotalMB,
            remainingBalanceMB = plan1.remainingBalanceMB,
            totalCycleDays = totalDays1,
            daysElapsed = currentDay1,
            simulatedDailyMB = sim1
        )

        // Line 2 (Prepago 35 días) calculation:
        val totalDays2 = _customTotalDaysLine2.value ?: plan2.totalCycleDays
        // Compute days elapsed from purchase date if available, or custom
        val daysFromPurchase = maxOf(1, ((System.currentTimeMillis() - plan2.purchaseTimestamp) / (24L * 60L * 60L * 1000L)).toInt() + 1)
        val currentDay2 = (_customDayLine2.value ?: plan2.customCurrentDay ?: daysFromPurchase).coerceIn(1, totalDays2)

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
            activeLineId = activeId,
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
            calendarCurrentDay = realDay,
            calendarTotalDays = realTotalDays
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DataForecastUiState()
    )

    fun selectLine(lineId: Int) {
        _activeLineId.value = lineId
    }

    fun updateRemainingBalance(lineId: Int, remainingMB: Double) {
        viewModelScope.launch {
            val current = repository.getDataPlan(lineId).firstOrNull() ?: DataPlanEntity(id = lineId)
            val safeMB = remainingMB.coerceIn(0.0, current.packageTotalMB)
            val updated = current.copy(remainingBalanceMB = safeMB)
            repository.saveOrUpdatePlan(updated)

            val day = current.customCurrentDay ?: realDay
            repository.addLog(lineId, safeMB, day, "Actualización saldo Línea $lineId")
        }
    }

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

    fun registerNewPrepaidPurchase(lineId: Int = 2, packageTotalMB: Double, totalDays: Int = 35) {
        viewModelScope.launch {
            val current = repository.getDataPlan(lineId).firstOrNull() ?: DataPlanEntity(id = lineId, planType = "PREPAID_35")
            val safePackage = packageTotalMB.coerceAtLeast(100.0)
            _customDayLine2.value = 1
            _customTotalDaysLine2.value = totalDays

            val updated = current.copy(
                packageTotalMB = safePackage,
                remainingBalanceMB = safePackage,
                totalCycleDays = totalDays,
                customCurrentDay = 1,
                purchaseTimestamp = System.currentTimeMillis(),
                planName = "Paquete ${DataFormatters.format(safePackage, current.displayUnit)} (35d)"
            )
            repository.saveOrUpdatePlan(updated)
            repository.addLog(lineId, safePackage, 1, "Compra nuevo paquete 35 días")
        }
    }

    fun updateDays(lineId: Int, currentDay: Int, totalCycleDays: Int) {
        if (lineId == 1) {
            _customDayLine1.value = currentDay
            _customTotalDaysLine1.value = totalCycleDays
        } else {
            _customDayLine2.value = currentDay
            _customTotalDaysLine2.value = totalCycleDays
        }
        viewModelScope.launch {
            val current = repository.getDataPlan(lineId).firstOrNull() ?: DataPlanEntity(id = lineId)
            repository.saveOrUpdatePlan(
                current.copy(
                    customCurrentDay = currentDay,
                    totalCycleDays = totalCycleDays
                )
            )
        }
    }

    fun resetToRealCalendar(lineId: Int) {
        if (lineId == 1) {
            _customDayLine1.value = null
            _customTotalDaysLine1.value = null
            viewModelScope.launch {
                val current = repository.getDataPlan(1).firstOrNull() ?: return@launch
                repository.saveOrUpdatePlan(
                    current.copy(
                        customCurrentDay = null,
                        totalCycleDays = realTotalDays
                    )
                )
            }
        } else {
            // For Line 2 prepaid: reset to 35 days from purchase
            _customDayLine2.value = null
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
            repository.saveOrUpdatePlan(current.copy(remainingBalanceMB = remainingMB, customCurrentDay = cycleDay))
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
            val day = current.customCurrentDay ?: realDay
            repository.saveOrUpdatePlan(current.copy(remainingBalanceMB = safeMB))
            repository.addLog(lineId, safeMB, day, note)
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            repository.deleteLog(id)
        }
    }

    companion object {
        fun provideFactory(repository: DataPlanRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DataForecastViewModel(repository) as T
                }
            }
    }
}
