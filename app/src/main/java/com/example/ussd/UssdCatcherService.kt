package com.example.ussd

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.domain.UssdParser

/**
 * Accessibility fallback that reads the carrier's USSD response dialog when
 * [UssdSystemApi] is unavailable or rejected by the operator.
 *
 * Android only allows third-party apps to read USSD responses through the
 * accessibility window tree; the official TelephonyManager API is tried first
 * (see [UssdSystemApi]).
 *
 * It only inspects windows while a consultation or a package purchase is
 * pending (see [UssdCaptureHolder.consultationActive] and
 * [UssdCaptureHolder.purchaseActive]). Balance readings are dismissed after
 * capture; purchase menus are left open so the user can still act on them.
 */
class UssdCatcherService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val evt = event ?: return
        val readingActive = UssdCaptureHolder.consultationActive
        val purchaseActive = UssdCaptureHolder.purchaseActive
        if (!readingActive && !purchaseActive) return
        if (evt.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            evt.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return

        val text = try {
            collectText(root)
        } catch (_: Exception) {
            ""
        }
        if (text.isBlank()) return

        if (readingActive) {
            // Only react when the dialog actually carries a balance / days
            // (USSD dialogs first show placeholders like "Usted marcó *222#").
            val hasBalance = UssdParser.parseDataBalanceMB(text) != null
            val hasDays = UssdParser.parseDaysRemaining(text) != null
            if (!hasBalance && !hasDays) return

            if (UssdCaptureHolder.push(text)) {
                dismissUssdDialog(root)
            }
            return
        }

        // Purchase menus must stay open: the user still needs to pick an
        // option, and the dialog may ask for the final confirmation digit.
        if (purchaseActive && text.length >= 10 && text.any { it.isDigit() }) {
            UssdCaptureHolder.pushPurchase(text)
        }
    }

    override fun onInterrupt() {
        // Nothing to do.
    }

    /** Walks the node tree and concatenates every text/contentDescription. */
    private fun collectText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        node.text?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        node.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        for (i in 0 until node.childCount) {
            sb.append(collectText(node.getChild(i)))
        }
        return sb.toString()
    }

    /** Tries to close the USSD dialog: OK button first, BACK as fallback. */
    private fun dismissUssdDialog(root: AccessibilityNodeInfo) {
        try {
            val button = findDismissButton(root)
            if (button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
        } catch (_: Exception) {
        }
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (_: Exception) {
        }
    }

    private fun findDismissButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val label = (node.text ?: node.contentDescription)?.toString()?.lowercase() ?: ""
        if (node.isClickable && label.matches(DISMISS_LABELS)) return node
        for (i in 0 until node.childCount) {
            findDismissButton(node.getChild(i))?.let { return it }
        }
        return null
    }

    companion object {
        private val DISMISS_LABELS = Regex(
            """^(?:ok|okay|aceptar|cerrar|listo|continuar|close|done|salir|cancelar)\.?$"""
        )

        /**
         * True when this accessibility service is enabled in system settings.
         * Accepts both the full and the short component form used by
         * different Android versions.
         */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val component = ComponentName(context, UssdCatcherService::class.java)
            return enabled.contains(component.flattenToString()) ||
                enabled.contains(component.flattenToShortString())
        }
    }
}
