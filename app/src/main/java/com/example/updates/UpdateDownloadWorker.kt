package com.example.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads and verifies the signed APK in the background. Only after the
 * SHA-256 and the signing certificate match do we expose the file to the UI.
 */
class UpdateDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val prefs = UpdatePreferences(context)
        val info = prefs.availableInfo ?: return@withContext Result.failure()
        val file = File(context.cacheDir, APK_FILE_NAME)
        if (file.exists() && !file.delete()) return@withContext Result.failure()

        var verified = false
        try {
            val connection = URL(info.apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                }

                val totalBytes = connection.contentLength
                val digest = MessageDigest.getInstance("SHA-256")
                var downloadedBytes = 0
                var lastPercent = -1

                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloadedBytes += read

                            if (totalBytes > 0) {
                                val percent = (downloadedBytes.toLong() * 100 / totalBytes)
                                    .coerceIn(0L, 100L)
                                    .toInt()
                                if (percent != lastPercent && (percent % 5 == 0 || percent == 100)) {
                                    lastPercent = percent
                                    setProgressAsync(workDataOf(PROGRESS to percent)).get()
                                }
                            }
                        }
                    }
                }

                if (downloadedBytes == 0) {
                    file.delete()
                    return@withContext Result.failure()
                }

                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha.equals(info.sha256, ignoreCase = true)) {
                    file.delete()
                    return@withContext Result.failure()
                }
                if (!UpdateInstaller.hasMatchingSignature(context, file)) {
                    file.delete()
                    return@withContext Result.failure()
                }
                verified = true

                prefs.downloadedPath = file.absolutePath
                prefs.downloadedSha256 = actualSha
                UpdateNotifier.postReady(context, info)
                Result.success()
            } finally {
                connection.disconnect()
                if (!verified && file.exists()) file.delete()
            }
        } catch (e: Exception) {
            if (file.exists()) file.delete()
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "app_update_download"
        const val PROGRESS = "progress"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        private const val APK_FILE_NAME = "update.apk"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 16 * 1024
        private const val MAX_RETRIES = 3
        private const val USER_AGENT = "PrevisorDatos-Android"
    }
}
