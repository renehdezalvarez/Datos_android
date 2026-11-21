package com.example.ussd

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Shared dialing helpers for every USSD flow (balance reading, recharge date
 * and package purchase). A line can be pinned to a SIM slot; when it is,
 * direct calls are placed through that subscription instead of the system
 * default.
 */
object UssdDialer {

    fun hasCallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

    fun hasReadPhonePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    /** Null means "system default" or "permission not granted yet". */
    fun subscriptionIdForSlot(context: Context, slot: Int?): Int? {
        if (slot == null || !hasReadPhonePermission(context)) return null
        return SimInfoProvider.subscriptionIdForSlot(context, slot)
    }

    fun dial(context: Context, code: String, directCall: Boolean, selectedSlot: Int?) {
        if (!directCall || selectedSlot == null) {
            openDialIntent(context, code, directCall)
            return
        }

        if (!hasReadPhonePermission(context)) {
            Toast.makeText(
                context,
                "Concede el permiso de teléfono para marcar en la SIM elegida; se abrirá el marcador.",
                Toast.LENGTH_LONG
            ).show()
            openDialIntent(context, code, directCall = false)
            return
        }

        val handle = SimInfoProvider.phoneAccountHandleForSlot(context, selectedSlot)
        val telecomManager = context.getSystemService(TelecomManager::class.java)
        if (handle != null && telecomManager != null) {
            try {
                val extras = Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
                telecomManager.placeCall(Uri.parse("tel:${Uri.encode(code)}"), extras)
                return
            } catch (_: Exception) {
                // Fall through to the dialer below.
            }
        }

        Toast.makeText(
            context,
            "No se pudo marcar directamente en la SIM elegida; selecciónala en el marcador.",
            Toast.LENGTH_LONG
        ).show()
        openDialIntent(context, code, directCall = false)
    }

    private fun openDialIntent(context: Context, code: String, directCall: Boolean) {
        try {
            val encoded = Uri.encode(code)
            val action = if (directCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, Uri.parse("tel:$encoded")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo marcar $code", Toast.LENGTH_SHORT).show()
        }
    }
}
