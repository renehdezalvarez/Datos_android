package com.example.ussd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Reads the carrier's purchase confirmation SMS only while the app is waiting
 * for it (see [PurchaseSmsHolder.pending]). All other messages are ignored.
 */
class PurchaseSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!PurchaseSmsHolder.pending) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        }.getOrNull() ?: return
        if (messages.isEmpty()) return

        val body = messages.joinToString("\n") { message ->
            message.displayMessageBody ?: message.messageBody ?: ""
        }
        if (body.isBlank()) return

        PurchaseSmsHolder.push(
            text = body,
            sender = messages.firstOrNull()?.displayOriginatingAddress,
            timestampMs = System.currentTimeMillis()
        )
    }
}
