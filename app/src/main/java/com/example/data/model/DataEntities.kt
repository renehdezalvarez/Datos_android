package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_plan")
data class DataPlanEntity(
    @PrimaryKey
    val id: Int = 1,
    val packageTotalMB: Double = 10240.0, // 10 GB
    val remainingBalanceMB: Double = 10240.0, // starts with the full package
    val totalCycleDays: Int = 30,
    val cycleStartDay: Int = 1,
    val customCurrentDay: Int? = null,
    val displayUnit: String = "GB", // "GB" or "MB"
    val operatorName: String = "Línea Principal",
    val planName: String = "Paquete 10 GB",
    val planType: String = "MONTHLY", // "MONTHLY" or "PREPAID_35"
    val purchaseTimestamp: Long = System.currentTimeMillis(),
    val ussdCode: String = "*111#",
    // 0-based SIM slot used to query this line's balance. Null means "use
    // the system default subscription", which is what users had before the
    // selector existed and is still useful on single-SIM devices.
    val simSlotIndex: Int? = null
)

@Entity(tableName = "data_balance_logs")
data class DataBalanceLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val cycleDay: Int,
    val remainingMB: Double,
    val note: String = ""
)
