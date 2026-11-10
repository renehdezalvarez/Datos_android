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
    version = 5,
    // Schemas are not exported: keeping app/schemas in sync from CI builds
    // is not worth it for this project. New installs get v5 directly and
    // existing ones migrate 4 -> 5 below.
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mobile_data_forecast.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
