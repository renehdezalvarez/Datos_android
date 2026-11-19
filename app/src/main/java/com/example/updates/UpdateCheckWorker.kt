package com.example.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.BuildConfig
import com.example.domain.UpdateVersions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Silent periodic check: it never installs or downloads anything by itself. */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) {
            val context = applicationContext
            val prefs = UpdatePreferences(context)

            // Si la versión ofrecida ya está instalada (o fue omitida), limpiamos
            // tarjeta y notificación aunque GitHub no responda en esta pasada.
            if (prefs.clearIfNotApplicable(BuildConfig.VERSION_CODE)) {
                UpdateNotifier.cancel(context)
            }

            val result = UpdateChecker().check()
            prefs.lastCheckMs = System.currentTimeMillis()

            if (result is UpdateCheckResult.Found) {
                val info = result.info
                if (UpdateVersions.shouldOfferUpdate(
                        info.versionCode,
                        BuildConfig.VERSION_CODE,
                        prefs.skippedVersionCode
                    )
                ) {
                    prefs.availableInfo = info
                    UpdateNotifier.postAvailable(context, info)
                } else {
                    // La release ya no ofrece nada instalable: descartamos
                    // tarjeta, descarga y aviso pendientes.
                    prefs.availableInfo = null
                    prefs.clearDownload()
                    UpdateNotifier.cancel(context)
                }
            }
        }
        Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "app_update_check"

        /** Called on every app start; KEEP makes this a no-op after the first call. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
