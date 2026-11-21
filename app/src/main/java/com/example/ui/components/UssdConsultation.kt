package com.example.ui.components

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.domain.UssdParser
import com.example.ussd.UssdDialer
import com.example.ussd.UssdSystemApi

/** Handle returned by [rememberUssdConsultation]; call [start] to run the flow. */
class UssdConsultTrigger internal constructor(private val onStart: () -> Unit) {
    fun start() = onStart()
}

/**
 * Shared "read the balance" flow used by the quick "Consultar Saldo" buttons
 * (no form involved) and by the full USSD dialog opened from Ajustes.
 *
 * The official telephony API is tried first. If it is unavailable, fails,
 * times out, or returns something unparseable, the accessibility catcher is
 * armed and the dialer is opened as the fallback. The caller supplies the
 * ViewModel hooks so the reading is applied by the same session-checked code
 * in both entry points.
 */
@Composable
fun rememberUssdConsultation(
    lineId: Int,
    ussdCode: String,
    selectedSimSlot: Int?,
    onStartAutoConsult: (useAccessibility: Boolean) -> Long,
    onEnableAccessibilityFallback: (sessionId: Long) -> Unit,
    onApplyAutoReading: (sessionId: Long, text: String) -> Unit,
    successMessage: String = "Saldo actualizado automáticamente."
): UssdConsultTrigger {
    val context = LocalContext.current
    val systemApiAvailable = remember(context) { UssdSystemApi.isAvailable(context) }

    // The dialog edits these values while the composable is alive, so read
    // them at call time instead of capturing the first composition values.
    // They stay as State objects (rather than `by` locals) because the
    // returned trigger keeps the first composition's closure.
    val currentCode = rememberUpdatedState(ussdCode)
    val currentSlot = rememberUpdatedState(selectedSimSlot)
    val startConsult = rememberUpdatedState(onStartAutoConsult)
    val enableFallback = rememberUpdatedState(onEnableAccessibilityFallback)
    val applyReading = rememberUpdatedState(onApplyAutoReading)
    val currentSuccessMessage = rememberUpdatedState(successMessage)

    var pendingSystemApiAttempt by remember { mutableStateOf(false) }

    fun hasCallPermission(): Boolean = UssdDialer.hasCallPermission(context)

    fun hasReadPhonePermission(): Boolean = UssdDialer.hasReadPhonePermission(context)

    /** Uses the SIM assigned to this line, or the system default when unset. */
    fun dialUssd(code: String, directCall: Boolean) {
        UssdDialer.dial(context, code, directCall, currentSlot.value)
    }

    /** The API could not answer: arm the catcher and open the dialer. */
    fun fallbackToAccessibility(sessionId: Long) {
        enableFallback.value(sessionId)
        Toast.makeText(
            context,
            "La lectura automática no está disponible; usando el marcador.",
            Toast.LENGTH_SHORT
        ).show()
        dialUssd(currentCode.value, directCall = hasCallPermission())
    }

    fun startSystemApiAttempt() {
        val selectedSlot = currentSlot.value
        val subscriptionId = if (selectedSlot != null) {
            if (!hasReadPhonePermission()) {
                Toast.makeText(
                    context,
                    "Concede el permiso de teléfono para consultar la SIM elegida, o selecciona «Automática».",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            UssdDialer.subscriptionIdForSlot(context, selectedSlot)
        } else {
            null
        }
        if (selectedSlot != null && subscriptionId == null) {
            Toast.makeText(
                context,
                "La SIM seleccionada ya no está activa. Elige otra SIM para esta línea.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val sessionId = startConsult.value(false)
        val started = UssdSystemApi.request(context, currentCode.value, subscriptionId) { result ->
            when (result) {
                is UssdSystemApi.Result.Success -> {
                    val text = result.response
                    val hasReadableData =
                        UssdParser.parseDataBalanceMB(text) != null ||
                            (lineId == 2 && (
                                UssdParser.parseDaysRemaining(text) != null ||
                                    UssdParser.parseRechargeDate(text) != null
                                ))
                    if (hasReadableData) {
                        applyReading.value(sessionId, text)
                        Toast.makeText(
                            context,
                            currentSuccessMessage.value,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        fallbackToAccessibility(sessionId)
                    }
                }

                is UssdSystemApi.Result.Failure,
                UssdSystemApi.Result.Timeout -> fallbackToAccessibility(sessionId)
            }
        }
        if (!started) fallbackToAccessibility(sessionId)
    }

    fun startAccessibilityAttempt(directCall: Boolean) {
        startConsult.value(true)
        dialUssd(currentCode.value, directCall)
    }

    // One consultation can ask for CALL_PHONE and, when a specific SIM is
    // selected, READ_PHONE_STATE. Without CALL_PHONE we only open the dialer
    // (the user presses "call" manually).
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val callGranted = grants[Manifest.permission.CALL_PHONE] ?: hasCallPermission()
        if (pendingSystemApiAttempt) {
            pendingSystemApiAttempt = false
            if (callGranted) {
                startSystemApiAttempt()
            } else {
                startAccessibilityAttempt(directCall = false)
            }
        } else {
            startAccessibilityAttempt(directCall = callGranted)
        }
    }

    fun startConsultation() {
        val needsReadPhonePermission = systemApiAvailable && currentSlot.value != null
        val missingPermissions = buildList {
            if (!hasCallPermission()) add(Manifest.permission.CALL_PHONE)
            if (needsReadPhonePermission && !hasReadPhonePermission()) {
                add(Manifest.permission.READ_PHONE_STATE)
            }
        }

        when {
            systemApiAvailable && missingPermissions.isEmpty() -> startSystemApiAttempt()
            systemApiAvailable -> {
                pendingSystemApiAttempt = true
                permissionsLauncher.launch(missingPermissions.toTypedArray())
            }
            hasCallPermission() -> startAccessibilityAttempt(directCall = true)
            else -> {
                pendingSystemApiAttempt = false
                permissionsLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
            }
        }
    }

    return remember { UssdConsultTrigger { startConsultation() } }
}
