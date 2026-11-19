package com.example.updates

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.BuildConfig
import com.example.domain.UpdateVersions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class UpdateStatus { IDLE, CHECKING, DOWNLOADING, READY, ERROR }

data class UpdateUiState(
    val info: UpdateInfo? = null,
    val status: UpdateStatus = UpdateStatus.IDLE,
    val progress: Int = 0,
    val message: String? = null,
    val downloadedPath: String? = null,
    val isReleaseBuild: Boolean = true
)

/** Owns the update state shared by the settings card and the download worker. */
class UpdateManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = UpdatePreferences(appContext)
    private val workManager = WorkManager.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(
        UpdateUiState(isReleaseBuild = !BuildConfig.DEBUG)
    )
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init {
        discardStaleUpdate()
        refresh()
        checkIfNeeded()

        scope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(UpdateDownloadWorker.UNIQUE_WORK_NAME)
                .collect { infos ->
                    val work = infos.firstOrNull() ?: return@collect
                    if (prefs.availableInfo == null) {
                        _state.update {
                            it.copy(status = UpdateStatus.IDLE, progress = 0, downloadedPath = null)
                        }
                        return@collect
                    }
                    when (work.state) {
                        WorkInfo.State.RUNNING,
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.BLOCKED -> _state.update {
                            it.copy(
                                status = UpdateStatus.DOWNLOADING,
                                progress = work.progress.getInt(UpdateDownloadWorker.PROGRESS, it.progress),
                                message = null
                            )
                        }

                        WorkInfo.State.SUCCEEDED -> {
                            val path = prefs.downloadedPath
                            if (path != null && File(path).isFile) {
                                _state.update {
                                    it.copy(
                                        status = UpdateStatus.READY,
                                        progress = 100,
                                        downloadedPath = path,
                                        message = null
                                    )
                                }
                            } else {
                                prefs.clearDownload()
                                _state.update {
                                    it.copy(status = UpdateStatus.IDLE, progress = 0, downloadedPath = null)
                                }
                            }
                        }

                        WorkInfo.State.FAILED,
                        WorkInfo.State.CANCELLED -> _state.update {
                            it.copy(
                                status = UpdateStatus.ERROR,
                                progress = 0,
                                downloadedPath = null,
                                message = "No se pudo descargar la actualización. Revisa tu conexión e inténtalo de nuevo."
                            )
                        }
                    }
                }
        }
    }

    fun refresh() {
        scope.launch {
            discardStaleUpdate()
            val path = prefs.downloadedPath?.takeIf { File(it).isFile }
            _state.update {
                it.copy(
                    info = prefs.availableInfo,
                    downloadedPath = path,
                    status = if (path != null) UpdateStatus.READY else it.status
                )
            }
        }
    }

    /** Elimina tarjeta, descarga y aviso de una oferta que ya no aplica. */
    private fun clearOffer(message: String? = null) {
        prefs.availableInfo = null
        prefs.clearDownload()
        UpdateNotifier.cancel(appContext)
        _state.update {
            it.copy(
                info = null,
                downloadedPath = null,
                status = UpdateStatus.IDLE,
                progress = 0,
                message = message
            )
        }
    }

    /**
     * An offer left over from a previous install must not outlive the version
     * that replaced it, even while the check is throttled or offline.
     */
    private fun discardStaleUpdate() {
        val info = prefs.availableInfo ?: return
        if (UpdateVersions.shouldOfferUpdate(
                info.versionCode,
                BuildConfig.VERSION_CODE,
                prefs.skippedVersionCode
            )
        ) return
        clearOffer()
    }

    /** Cheap on app start: the periodic worker and this call share the throttle. */
    fun checkIfNeeded() {
        scope.launch { check(force = false) }
    }

    suspend fun check(force: Boolean = false) {
        if (BuildConfig.DEBUG) return
        val now = System.currentTimeMillis()
        if (!force && now - prefs.lastCheckMs < CHECK_INTERVAL_MS) return
        prefs.lastCheckMs = now

        _state.update { it.copy(status = UpdateStatus.CHECKING, message = null) }
        val result = withContext(Dispatchers.IO) { UpdateChecker().check() }

        when (result) {
            is UpdateCheckResult.Found -> {
                val info = result.info
                val isNewer = UpdateVersions.isNewer(info.versionCode, BuildConfig.VERSION_CODE)
                val shouldOffer = UpdateVersions.shouldOfferUpdate(
                    info.versionCode,
                    BuildConfig.VERSION_CODE,
                    prefs.skippedVersionCode
                )
                when {
                    shouldOffer -> {
                        prefs.availableInfo = info
                        _state.update {
                            it.copy(info = info, status = UpdateStatus.IDLE, message = null)
                        }
                    }

                    isNewer -> {
                        // Versión más nueva, pero omitida: no dejamos una descarga vieja visible.
                        clearOffer()
                    }

                    else -> {
                        clearOffer(if (force) "Ya tienes la versión más reciente." else null)
                    }
                }
            }

            UpdateCheckResult.Unavailable -> _state.update {
                it.copy(
                    status = UpdateStatus.IDLE,
                    message = if (force) "No se pudo comprobar. Revisa tu conexión." else null
                )
            }

            UpdateCheckResult.UpToDate -> Unit
        }
    }

    fun download() {
        if (prefs.availableInfo == null) return
        prefs.clearDownload()
        UpdateNotifier.cancel(appContext)
        UpdateDownloadWorker.enqueue(appContext)
        _state.update {
            it.copy(status = UpdateStatus.DOWNLOADING, progress = 0, downloadedPath = null, message = null)
        }
    }

    fun dismiss() {
        prefs.availableInfo?.let { prefs.skippedVersionCode = it.versionCode }
        prefs.availableInfo = null
        prefs.clearDownload()
        UpdateDownloadWorker.cancel(appContext)
        UpdateNotifier.cancel(appContext)
        _state.update {
            it.copy(
                info = null,
                downloadedPath = null,
                status = UpdateStatus.IDLE,
                progress = 0,
                message = null
            )
        }
    }

    fun clearDownload() {
        prefs.clearDownload()
        UpdateDownloadWorker.cancel(appContext)
        UpdateNotifier.cancel(appContext)
        _state.update {
            it.copy(downloadedPath = null, status = UpdateStatus.IDLE, progress = 0)
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    }
}

/** Process-wide instance so the WorkInfo collector is not duplicated on rotation. */
object UpdateManagerProvider {
    @Volatile
    private var instance: UpdateManager? = null

    fun get(context: Context): UpdateManager =
        instance ?: synchronized(this) {
            instance ?: UpdateManager(context.applicationContext).also { instance = it }
        }
}
