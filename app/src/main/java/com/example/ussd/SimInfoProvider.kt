package com.example.ussd

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/** A currently active SIM that can be assigned to one of the two lines. */
data class SimOption(
    val slotIndex: Int,
    val subscriptionId: Int,
    val label: String,
    val isDefault: Boolean
)

/**
 * Lists the active SIMs and resolves the subscription/phone account associated
 * with a stored SIM slot.
 *
 * Reading the SIM list requires `READ_PHONE_STATE`; this is the same "Teléfono"
 * permission group as `CALL_PHONE`, but it still has to be declared and
 * requested explicitly on modern Android versions.
 */
object SimInfoProvider {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun activeSims(context: Context): List<SimOption> {
        if (!hasPermission(context)) return emptyList()

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
            as? SubscriptionManager ?: return emptyList()
        val subscriptions = runCatching {
            subscriptionManager.activeSubscriptionInfoList
        }.getOrNull().orEmpty()

        val defaultSubscriptionId = runCatching {
            SubscriptionManager.getDefaultSubscriptionId()
        }.getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)

        return subscriptions
            .sortedBy { it.simSlotIndex }
            .mapNotNull { info ->
                val slot = info.simSlotIndex
                val subscriptionId = info.subscriptionId
                if (slot < 0 || subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    return@mapNotNull null
                }

                val slotLabel = "SIM ${slot + 1}"
                val displayName = info.displayName?.toString()?.trim().orEmpty()
                val carrierName = info.carrierName?.toString()?.trim().orEmpty()
                val detail = when {
                    displayName.isNotEmpty() && carrierName.isNotEmpty() && displayName != carrierName ->
                        "$displayName · $carrierName"

                    displayName.isNotEmpty() -> displayName
                    carrierName.isNotEmpty() -> carrierName
                    else -> ""
                }

                SimOption(
                    slotIndex = slot,
                    subscriptionId = subscriptionId,
                    label = if (detail.isEmpty()) slotLabel else "$slotLabel · $detail",
                    isDefault = subscriptionId == defaultSubscriptionId
                )
            }
    }

    /** Subscription id for a stored SIM slot, or null when it is no longer active. */
    fun subscriptionIdForSlot(context: Context, slotIndex: Int): Int? =
        activeSims(context).firstOrNull { it.slotIndex == slotIndex }?.subscriptionId

    /**
     * Phone account used by the system dialer for a SIM slot. It lets the
     * accessibility/dialer fallback place the USSD call on the same SIM the
     * line is configured to use instead of whichever SIM is the default.
     */
    @SuppressLint("MissingPermission")
    fun phoneAccountHandleForSlot(context: Context, slotIndex: Int): PhoneAccountHandle? {
        if (!hasPermission(context)) return null
        // getSubscriptionId(PhoneAccountHandle) is only public on newer
        // releases; older devices fall back to the normal dialer.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null

        val subscriptionId = subscriptionIdForSlot(context, slotIndex) ?: return null
        val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return null
        val telephonyManager = context.getSystemService(TelephonyManager::class.java) ?: return null

        return runCatching {
            telecomManager.callCapablePhoneAccounts.firstOrNull { handle ->
                telephonyManager.getSubscriptionId(handle) == subscriptionId
            }
        }.getOrNull()
    }
}
