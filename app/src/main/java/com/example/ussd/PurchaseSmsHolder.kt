package com.example.ussd

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge between the SMS receiver and the in-app purchase dialog.
 *
 * The receiver only looks at messages while [pending] is true, i.e. during the
 * short window after the final USSD confirmation digit was sent. Text from
 * other moments is ignored, and a session id prevents stale SMS from being
 * applied to a newer purchase.
 */
object PurchaseSmsHolder {

    data class SmsCapture(
        val text: String,
        val sender: String?,
        val timestampMs: Long
    )

    private val _lastSms = MutableStateFlow<SmsCapture?>(null)

    /** Latest purchase SMS captured while a purchase was pending. */
    val lastSms: StateFlow<SmsCapture?> = _lastSms.asStateFlow()

    @Volatile
    var pending: Boolean = false
        private set

    @Volatile
    var sessionId: Long? = null
        private set

    @Volatile
    var startedAtMs: Long = 0L
        private set

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

    /** Starts listening for the confirmation SMS of [sessionId]. */
    fun arm(sessionId: Long, startedAtMs: Long = System.currentTimeMillis()) {
        this.sessionId = sessionId
        this.startedAtMs = startedAtMs
        this.pending = true
        clear()
    }

    fun disarm() {
        pending = false
        sessionId = null
    }

    private var lastText: String? = null
    private var lastAtMs = 0L

    fun push(text: String, sender: String?, timestampMs: Long): Boolean {
        if (!pending || timestampMs < startedAtMs) return false
        if (text == lastText && timestampMs - lastAtMs < 10_000L) return false
        lastText = text
        lastAtMs = timestampMs
        _lastSms.value = SmsCapture(text.trim(), sender, timestampMs)
        return true
    }

    fun clear() {
        _lastSms.value = null
        lastText = null
    }
}
