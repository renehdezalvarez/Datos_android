package com.example.updates

/** Metadata published in the `update.json` asset of the latest GitHub release. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val tag: String,
    val apkName: String,
    val sha256: String,
    val sizeBytes: Long,
    val releaseUrl: String,
    val notes: String
) {
    val apkUrl: String
        get() = "$RELEASE_DOWNLOAD_BASE/$tag/$apkName"

    companion object {
        const val RELEASE_DOWNLOAD_BASE =
            "https://github.com/renehdezalvarez/Datos_android/releases/download"
    }
}
