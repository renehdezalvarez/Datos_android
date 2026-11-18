package com.example.updates

import android.content.Context
import androidx.core.content.edit

/** Small persisted state for the self-update flow (no Room schema changes). */
class UpdatePreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastCheckMs: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_CHECK, value) }

    var skippedVersionCode: Int
        get() = prefs.getInt(KEY_SKIPPED_CODE, 0)
        set(value) = prefs.edit { putInt(KEY_SKIPPED_CODE, value) }

    var availableInfo: UpdateInfo?
        get() {
            val code = prefs.getInt(KEY_CODE, 0)
            val apk = prefs.getString(KEY_APK, null)
            val sha = prefs.getString(KEY_SHA, null)
            if (code <= 0 || apk.isNullOrBlank() || sha.isNullOrBlank()) return null
            return UpdateInfo(
                versionCode = code,
                versionName = prefs.getString(KEY_NAME, "").orEmpty(),
                tag = prefs.getString(KEY_TAG, "").orEmpty(),
                apkName = apk,
                sha256 = sha,
                sizeBytes = prefs.getLong(KEY_SIZE, 0L),
                releaseUrl = prefs.getString(KEY_RELEASE_URL, "").orEmpty(),
                notes = prefs.getString(KEY_NOTES, "").orEmpty()
            )
        }
        set(value) {
            prefs.edit {
                if (value == null) {
                    remove(KEY_CODE)
                    remove(KEY_NAME)
                    remove(KEY_TAG)
                    remove(KEY_APK)
                    remove(KEY_SHA)
                    remove(KEY_SIZE)
                    remove(KEY_RELEASE_URL)
                    remove(KEY_NOTES)
                } else {
                    putInt(KEY_CODE, value.versionCode)
                    putString(KEY_NAME, value.versionName)
                    putString(KEY_TAG, value.tag)
                    putString(KEY_APK, value.apkName)
                    putString(KEY_SHA, value.sha256)
                    putLong(KEY_SIZE, value.sizeBytes)
                    putString(KEY_RELEASE_URL, value.releaseUrl)
                    putString(KEY_NOTES, value.notes)
                }
            }
        }

    var downloadedPath: String?
        get() = prefs.getString(KEY_DOWNLOADED_PATH, null)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_DOWNLOADED_PATH) else putString(KEY_DOWNLOADED_PATH, value)
        }

    var downloadedSha256: String?
        get() = prefs.getString(KEY_DOWNLOADED_SHA, null)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_DOWNLOADED_SHA) else putString(KEY_DOWNLOADED_SHA, value)
        }

    fun clearDownload() {
        downloadedPath = null
        downloadedSha256 = null
    }

    companion object {
        private const val PREFS_NAME = "app_updates"
        private const val KEY_LAST_CHECK = "last_check_ms"
        private const val KEY_SKIPPED_CODE = "skipped_version_code"
        private const val KEY_CODE = "version_code"
        private const val KEY_NAME = "version_name"
        private const val KEY_TAG = "tag"
        private const val KEY_APK = "apk_name"
        private const val KEY_SHA = "sha256"
        private const val KEY_SIZE = "size"
        private const val KEY_RELEASE_URL = "release_url"
        private const val KEY_NOTES = "notes"
        private const val KEY_DOWNLOADED_PATH = "downloaded_path"
        private const val KEY_DOWNLOADED_SHA = "downloaded_sha256"
    }
}
