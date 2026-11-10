package com.example.ussd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge between the [UssdCatcherService] (accessibility service) and the
 * rest of the app.
 *
 * Privacy by design: the catcher only inspects window contents while
 * [consultationActive] is true, i.e. during the short window after the user
 * pressed "Marcar" and before the response is consumed or the consultation
 * times out.
 */
object UssdCaptureHolder {

    data class Capture(
        val text: String,
        val timestampMs: Long
    )

    private val _lastCapture = MutableStateFlow<Capture?>(null)

    /** Latest captured USSD response, if any. */
    val lastCapture: StateFlow<Capture?> = _lastCapture.asStateFlow()

    @Volatile
    var consultationActive: Boolean = false
        private set

    /** Arms the catcher: called right before the USSD code is dialed. */
    fun arm() {
        consultationActive = true
    }

    /** Disarms the catcher: called when the response is applied or on timeout. */
    fun disarm() {
        consultationActive = false
    }

    private var lastPushedText: String? = null
    private var lastPushedAtMs = 0L

    /**
     * Stores a captured response. Duplicate pushes of the same text within a
     * short window are ignored (USSD dialogs fire several content-change
     * events for one response).
     */
    fun push(text: String): Boolean {
        val now = System.currentTimeMillis()
        if (text == lastPushedText && now - lastPushedAtMs < 5_000L) return false
        lastPushedText = text
        lastPushedAtMs = now
        _lastCapture.value = Capture(text.trim(), now)
        return true
    }

    fun clear() {
        _lastCapture.value = null
        lastPushedText = null
    }
}
