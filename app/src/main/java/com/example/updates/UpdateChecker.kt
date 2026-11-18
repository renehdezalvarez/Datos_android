package com.example.updates

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Result of a silent check against the latest GitHub release. */
sealed class UpdateCheckResult {
    data class Found(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    object Unavailable : UpdateCheckResult()
}

/**
 * Reads the newest release metadata from GitHub. It performs blocking I/O, so
 * callers must run [check] inside `withContext(Dispatchers.IO)`.
 *
 * The API is only used to locate the `update.json` asset; the JSON itself
 * carries the exact versionCode and SHA-256 needed for a safe in-place update.
 */
class UpdateChecker(private val timeoutMs: Int = 15_000) {

    fun check(): UpdateCheckResult = try {
        val releaseJson = getText(RELEASES_URL)
        val release = JSONObject(releaseJson)

        val metadataUrl = findMetadataUrl(release) ?: return UpdateCheckResult.Unavailable
        val info = UpdateJsonParser.parse(getText(metadataUrl)) ?: return UpdateCheckResult.Unavailable

        UpdateCheckResult.Found(
            info.copy(
                releaseUrl = release.optString("html_url"),
                notes = release.optString("body").take(NOTES_MAX_LENGTH)
            )
        )
    } catch (e: Exception) {
        // No red de GitHub, rate limit o JSON inesperado: nunca interrumpimos la app.
        UpdateCheckResult.Unavailable
    }

    private fun findMetadataUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name") == METADATA_ASSET) {
                return asset.optString("browser_download_url").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun getText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        try {
            return connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/renehdezalvarez/Datos_android/releases/latest"
        private const val METADATA_ASSET = "update.json"
        private const val USER_AGENT = "PrevisorDatos-Android"
        private const val NOTES_MAX_LENGTH = 800
    }
}
