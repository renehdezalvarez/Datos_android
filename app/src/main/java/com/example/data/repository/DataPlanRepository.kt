package com.example.data.repository

import com.example.data.local.DataDao
import com.example.data.model.DataBalanceLogEntity
import com.example.data.model.DataPlanEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

class DataPlanRepository(private val dao: DataDao) {

    fun getDataPlan(id: Int = 1): Flow<DataPlanEntity?> = dao.getDataPlan(id)

    fun getAllPlans(): Flow<List<DataPlanEntity>> = dao.getAllPlans()

    fun getLogsForPlan(planId: Int): Flow<List<DataBalanceLogEntity>> = dao.getLogsForPlan(planId)

    fun getAllLogs(): Flow<List<DataBalanceLogEntity>> = dao.getAllLogs()

    suspend fun saveOrUpdatePlan(plan: DataPlanEntity) = dao.saveOrUpdateDataPlan(plan)

    suspend fun addLog(planId: Int, remainingMB: Double, cycleDay: Int, note: String) {
        dao.insertLog(
            DataBalanceLogEntity(
                planId = planId,
                cycleDay = cycleDay,
                remainingMB = remainingMB,
                note = note
            )
        )
    }

    suspend fun deleteLog(id: Long) = dao.deleteLogById(id)

    suspend fun seedInitialDataIfEmpty() {
        // Each line is seeded independently so an existing user configuration
        // is never overwritten by the defaults below.
        if (dao.getDataPlan(1).firstOrNull() == null) {
            val calendar = Calendar.getInstance()
            val maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

            // Line 1: monthly line, starts with the full package available.
            val line1 = DataPlanEntity(
                id = 1,
                packageTotalMB = 10240.0, // 10 GB
                remainingBalanceMB = 10240.0,
                totalCycleDays = maxDays,
                cycleStartDay = 1,
                customCurrentDay = null, // falls back to the real calendar day
                displayUnit = "GB",
                operatorName = "Línea 1 (Mensual)",
                planName = "Plan Mensual 10 GB",
                planType = "MONTHLY",
                purchaseTimestamp = System.currentTimeMillis(),
                ussdCode = "*222#"
            )
            dao.saveOrUpdateDataPlan(line1)
        }

        if (dao.getDataPlan(2).firstOrNull() == null) {
            // Line 2: prepaid 35-day line, starts with the full package available.
            val line2 = DataPlanEntity(
                id = 2,
                packageTotalMB = 6144.0, // 6 GB
                remainingBalanceMB = 6144.0,
                totalCycleDays = 35, // 35 days package!
                cycleStartDay = 1,
                customCurrentDay = 1,
                displayUnit = "GB",
                operatorName = "Línea 2 (Prepago)",
                planName = "Paquete Prepago 35 Días",
                planType = "PREPAID_35",
                purchaseTimestamp = System.currentTimeMillis(),
                ussdCode = "*222*328#"
            )
            dao.saveOrUpdateDataPlan(line2)
        }
    }
}
