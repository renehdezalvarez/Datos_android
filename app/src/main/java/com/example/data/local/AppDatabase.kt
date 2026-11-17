package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.DataBalanceLogEntity
import com.example.data.model.DataPlanEntity

@Database(
    entities = [
        DataPlanEntity::class,
        DataBalanceLogEntity::class
    ],
    version = 8,
    // Schemas are not exported: keeping app/schemas in sync from CI builds
    // is not worth it for this project. New installs get v8 directly and
    // existing ones migrate 4 -> 5 -> 6 -> 7 -> 8 below.
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dataDao(): DataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v5: postpaid line default USSD code *222# -> *111#
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE data_plan SET ussdCode = '*111#' WHERE id = 1 AND ussdCode = '*222#'")
            }
        }

        // v6: the monthly line now follows the calendar month (28/29/30/31
        // days). Legacy rows stored a manually-set day from old seeds that
        // would otherwise show up as a spurious calendar-day correction.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE data_plan SET customCurrentDay = NULL WHERE id = 1")
            }
        }

        // v7: each line can remember which physical SIM performs its USSD
        // consultation. Existing rows start as "system default".
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE data_plan ADD COLUMN simSlotIndex INTEGER")
            }
        }

        // v8: prepaid line stores when a balance recharge becomes possible,
        // plus the cycle in which the carrier date was queried.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE data_plan ADD COLUMN rechargeEligibleDateMs INTEGER")
                db.execSQL("ALTER TABLE data_plan ADD COLUMN rechargeCheckedCycleStartMs INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mobile_data_forecast.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
