package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.DataBalanceLogEntity
import com.example.data.model.DataPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DataDao {

    @Query("SELECT * FROM data_plan WHERE id = :id LIMIT 1")
    fun getDataPlan(id: Int): Flow<DataPlanEntity?>

    @Query("SELECT * FROM data_plan ORDER BY id ASC")
    fun getAllPlans(): Flow<List<DataPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOrUpdateDataPlan(plan: DataPlanEntity)

    @Query("SELECT * FROM data_balance_logs WHERE planId = :planId ORDER BY timestamp DESC")
    fun getLogsForPlan(planId: Int): Flow<List<DataBalanceLogEntity>>

    @Query("SELECT * FROM data_balance_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<DataBalanceLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DataBalanceLogEntity): Long

    @Query("DELETE FROM data_balance_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM data_balance_logs")
    suspend fun clearAllLogs()
}
