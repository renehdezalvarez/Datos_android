package com.example.data.repository

import com.example.data.local.DataDao
import com.example.data.model.DataBalanceLogEntity
import com.example.data.model.DataPlanEntity
import kotlinx.coroutines.flow.Flow
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
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Line 1: Monthly line
        val line1 = DataPlanEntity(
            id = 1,
            packageTotalMB = 10240.0, // 10 GB
            remainingBalanceMB = 3480.0, // 3.48 GB
            totalCycleDays = maxDays,
            cycleStartDay = 1,
            customCurrentDay = currentDay,
            displayUnit = "GB",
            operatorName = "Línea 1 (Mensual)",
            planName = "Plan Mensual 10 GB",
            planType = "MONTHLY",
            purchaseTimestamp = System.currentTimeMillis() - (currentDay.toLong() * 24L * 60L * 60L * 1000L)
        )
        dao.saveOrUpdateDataPlan(line1)

        // Line 2: Prepaid 35-day line!
        // Suppose package of 6 GB (6144 MB), bought 8 days ago, remaining 4200 MB
        val eightDaysAgo = System.currentTimeMillis() - (8L * 24L * 60L * 60L * 1000L)
        val line2 = DataPlanEntity(
            id = 2,
            packageTotalMB = 6144.0, // 6 GB
            remainingBalanceMB = 4200.0, // 4.2 GB
            totalCycleDays = 35, // 35 days package!
            cycleStartDay = 1,
            customCurrentDay = 8,
            displayUnit = "GB",
            operatorName = "Línea 2 (Prepago)",
            planName = "Paquete Prepago 35 Días",
            planType = "PREPAID_35",
            purchaseTimestamp = eightDaysAgo
        )
        dao.saveOrUpdateDataPlan(line2)

        // Seed logs for Line 1
        dao.insertLog(DataBalanceLogEntity(planId = 1, cycleDay = (currentDay - 4).coerceAtLeast(1), remainingMB = 6100.0, note = "Lectura inicial"))
        dao.insertLog(DataBalanceLogEntity(planId = 1, cycleDay = (currentDay - 2).coerceAtLeast(1), remainingMB = 4800.0, note = "Revisión quincenal"))
        dao.insertLog(DataBalanceLogEntity(planId = 1, cycleDay = currentDay, remainingMB = 3480.0, note = "Saldo actual verificado"))

        // Seed logs for Line 2
        dao.insertLog(DataBalanceLogEntity(planId = 2, cycleDay = 1, remainingMB = 6144.0, note = "Activación paquete 35 días"))
        dao.insertLog(DataBalanceLogEntity(planId = 2, cycleDay = 4, remainingMB = 5300.0, note = "Revisión 4to día"))
        dao.insertLog(DataBalanceLogEntity(planId = 2, cycleDay = 8, remainingMB = 4200.0, note = "Saldo actual"))
    }
}
