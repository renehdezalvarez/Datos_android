package com.example.ussd

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sends a USSD request through the official telephony API (Android 8+).
 *
 * This avoids reading the system dialog through accessibility. Some carriers
 * disable the API, so callers must keep the accessibility catcher and the
 * manual capture as fallbacks.
 */
object UssdSystemApi {

    sealed interface Result {
        data class Success(val response: String) : Result
        data class Failure(val failureCode: Int) : Result
        data object Timeout : Result
    }

    private const val TIMEOUT_MS = 20_000L

    /** True when the platform/device exposes the telephony API this app needs. */
    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return false
        return context.getSystemService(TelephonyManager::class.java) != null
    }

    /**
     * Starts a USSD request. Returns false when the request could not be
     * started at all; [callback] is then never invoked. The caller must hold
     * `CALL_PHONE` (the UI requests it before calling this). When
     * [subscriptionId] is provided, the request is sent through that SIM;
     * null keeps the system default subscription.
     *
     * The callback always runs on the main thread and is invoked at most once:
     * with the response, a failure code, or [Result.Timeout].
     */
    @SuppressLint("MissingPermission")
    fun request(
        context: Context,
        code: String,
        subscriptionId: Int? = null,
        callback: (Result) -> Unit
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!isAvailable(context)) return false
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val baseTelephonyManager = context.getSystemService(TelephonyManager::class.java)
            ?: return false
        val telephonyManager = if (subscriptionId != null) {
            if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) return false
            runCatching {
                baseTelephonyManager.createForSubscriptionId(subscriptionId)
            }.getOrNull() ?: return false
        } else {
            baseTelephonyManager
        }
        val handler = Handler(Looper.getMainLooper())
        val finished = AtomicBoolean(false)

        fun finish(result: Result) {
            if (!finished.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            callback(result)
        }

        handler.postDelayed({ finish(Result.Timeout) }, TIMEOUT_MS)

        return try {
            telephonyManager.sendUssdRequest(
                code,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        response: CharSequence
                    ) {
                        finish(Result.Success(response?.toString().orEmpty()))
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int
                    ) {
                        finish(Result.Failure(failureCode))
                    }
                },
                handler
            )
            true
        } catch (_: Exception) {
            // The caller will fall back to the accessibility/dialer route.
            if (finished.compareAndSet(false, true)) {
                handler.removeCallbacksAndMessages(null)
            }
            false
        }
    }
}
