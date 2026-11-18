package com.example.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * Bridges the downloaded APK to Android's package installer.
 *
 * A sideloaded app cannot install silently: Android always shows its own
 * confirmation screen. We can only make the rest of the flow invisible and
 * grant the URI permission needed on Android 7+ (hence the FileProvider).
 */
object UpdateInstaller {

    fun canRequestInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun manageUnknownSourcesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }
        context.startActivity(intent)
    }

    /**
     * Rejects APKs signed by a different key (debug builds, tampered downloads)
     * before handing them to the system installer.
     */
    fun hasMatchingSignature(context: Context, apkFile: File): Boolean {
        val flags = signatureFlags()
        val installed = try {
            context.packageManager.getPackageInfo(context.packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
        val candidate = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: return false

        val installedCerts = certificates(installed)
        val candidateCerts = certificates(candidate)
        return installedCerts.any { cert -> candidateCerts.any { it.contentEquals(cert) } }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun certificates(info: PackageInfo): List<ByteArray> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptyList()
            val history = signing.signingCertificateHistory?.toList().orEmpty()
            val signers = signing.apkContentsSigners?.toList().orEmpty()
            return (history + signers).map { it.toByteArray() }
        }
        @Suppress("DEPRECATION")
        return info.signatures?.map { it.toByteArray() }.orEmpty()
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val BUFFER_SIZE = 16 * 1024
}
