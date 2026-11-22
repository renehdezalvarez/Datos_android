package com.example.ui.components

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.PrepaidPurchase
import com.example.domain.UssdPurchaseParser.PackageOption
import com.example.domain.UssdPurchaseParser.PurchaseConfirmation
import com.example.ussd.PurchaseSmsHolder
import com.example.ussd.UssdDialer
import com.example.ussd.UssdSystemApi
import kotlinx.coroutines.delay

private sealed interface PurchaseStep {
    data class MenuReady(val raw: String, val options: List<PackageOption>) : PurchaseStep
    data class Confirming(val option: PackageOption) : PurchaseStep
    data object Purchasing : PurchaseStep
    data class WaitingSms(val ussdAck: String?) : PurchaseStep
    data class Success(val option: PackageOption, val raw: String) : PurchaseStep
    data class Failure(val message: String, val raw: String?) : PurchaseStep
}

private data class ActivePurchaseRequest(
    val session: Long,
    val startedAt: Long,
    val option: PackageOption
)

/**
 * Buys a prepaid package for line 2.
 *
 * The package menu is kept in the app ([PrepaidPurchase.MENU_OPTIONS]), so the
 * only USSD request is the final purchase code (`*133*1*4*N*1#`). The official
 * telephony API is tried first and the dialer is the fallback. The purchase is
 * registered only when ETECSA's confirmation SMS is read (or pasted).
 */
@Composable
fun UssdPurchaseDialog(
    displayUnit: String,
    selectedSimSlot: Int?,
    currentRemainingMB: Double,
    previousBalanceLost: Boolean = false,
    onDismiss: () -> Unit,
    onOpenManual: () -> Unit,
    onPurchaseConfirmed: (rechargedMB: Double, totalDays: Int, rawText: String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val systemApiAvailable = remember(context) { UssdSystemApi.isAvailable(context) }

    val rawMenuText = PrepaidPurchase.MENU_TEXT
    val menuOptions = PrepaidPurchase.MENU_OPTIONS

    var step by remember {
        mutableStateOf<PurchaseStep>(PurchaseStep.MenuReady(rawMenuText, menuOptions))
    }
    var sessionCounter by remember { mutableStateOf(0L) }
    var active by remember { mutableStateOf<ActivePurchaseRequest?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var apiUsable by remember { mutableStateOf(systemApiAvailable) }
    var assistedNoticeShown by remember { mutableStateOf(false) }

    var manualOptionText by remember { mutableStateOf("") }
    var sizeText by remember { mutableStateOf("") }
    var daysText by remember { mutableStateOf("35") }
    var smsText by remember { mutableStateOf("") }
    var ussdAckText by remember { mutableStateOf("") }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val action = pendingAction
        pendingAction = null
        val callGranted = grants[Manifest.permission.CALL_PHONE]
            ?: UssdDialer.hasCallPermission(context)
        val smsGranted = grants[Manifest.permission.RECEIVE_SMS]
            ?: PurchaseSmsHolder.hasPermission(context)
        if (!smsGranted) {
            Toast.makeText(
                context,
                "Sin permiso SMS no podremos leer la confirmación; podrás pegarla a mano.",
                Toast.LENGTH_LONG
            ).show()
        }
        if (callGranted) {
            action?.invoke()
        } else {
            Toast.makeText(context, "Sin permiso de llamada no se puede marcar el USSD.", Toast.LENGTH_LONG).show()
        }
    }

    fun runWithPermissions(action: () -> Unit) {
        val missing = buildList {
            if (!UssdDialer.hasCallPermission(context)) add(Manifest.permission.CALL_PHONE)
            if (systemApiAvailable && selectedSimSlot != null &&
                !UssdDialer.hasReadPhonePermission(context)
            ) {
                add(Manifest.permission.READ_PHONE_STATE)
            }
            if (!PurchaseSmsHolder.hasPermission(context)) add(Manifest.permission.RECEIVE_SMS)
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingAction = action
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    fun resolveSubscriptionId(): Int? = when {
        selectedSimSlot == null -> null
        !UssdDialer.hasReadPhonePermission(context) -> null
        else -> UssdDialer.subscriptionIdForSlot(context, selectedSimSlot)
    }

    fun startAssisted(session: Long, code: String, option: PackageOption) {
        apiUsable = false
        active = ActivePurchaseRequest(session, active?.startedAt ?: System.currentTimeMillis(), option)
        UssdDialer.dial(
            context,
            code,
            directCall = UssdDialer.hasCallPermission(context),
            selectedSlot = selectedSimSlot
        )
        if (!assistedNoticeShown) {
            Toast.makeText(
                context,
                "Marcando la compra. Espera el SMS de confirmación de ETECSA.",
                Toast.LENGTH_LONG
            ).show()
            assistedNoticeShown = true
        }
    }

    fun showResult(result: PurchaseConfirmation, text: String) {
        if (result.failure || !result.success) {
            step = PurchaseStep.Failure(result.message, text)
            return
        }
        val optionNumber = manualOptionText.toIntOrNull() ?: 0
        // The editable field is authoritative: it is what the user reviewed in
        // the confirmation step. The parser/menu values are only fallbacks.
        val gb = sizeText.toDoubleOrNull()
            ?: result.sizeGB
            ?: menuOptions.firstOrNull { it.number == optionNumber }?.sizeGB
        val days = result.days ?: daysText.toIntOrNull()?.takeIf { it > 0 } ?: 35
        if (gb == null || gb <= 0.0) {
            step = PurchaseStep.Failure(
                "La compra se confirmó pero no se supo cuánto saldo recargó. Regístralo manualmente.",
                text
            )
            return
        }
        step = PurchaseStep.Success(
            PackageOption(
                number = optionNumber,
                sizeGB = gb,
                days = days,
                price = result.price,
                rawLine = text.trim()
            ),
            text
        )
    }

    /** Applies a carrier SMS (auto-captured or pasted by the user). */
    fun applySmsConfirmation(text: String) {
        if (text.isBlank()) return
        val result = com.example.domain.UssdPurchaseParser.parseSmsConfirmation(text)
        PurchaseSmsHolder.disarm()
        active = null
        if (result.failure || !result.success) {
            step = PurchaseStep.Failure(
                if (result.failure) result.message
                else "El SMS no parece confirmar esta compra. Puedes registrarla manualmente.",
                text
            )
            return
        }
        showResult(result.copy(success = true, failure = false), text)
    }

    fun selectOption(option: PackageOption) {
        sizeText = option.sizeGB?.toString() ?: ""
        daysText = option.days?.toString() ?: "35"
        manualOptionText = option.number.toString()
        step = PurchaseStep.Confirming(option)
    }

    fun continueFromMenu() {
        val number = manualOptionText.toIntOrNull()?.takeIf { it in 1..99 }
        val option = menuOptions.firstOrNull { it.number == number }
            ?: number?.let { PackageOption(it, null, null, null, rawMenuText) }
        if (option == null) {
            Toast.makeText(context, "Escribe el número de la opción.", Toast.LENGTH_SHORT).show()
            return
        }
        selectOption(option)
    }

    fun startPurchase(option: PackageOption) {
        val gb = sizeText.toDoubleOrNull()
        if (gb == null || gb <= 0.0) {
            Toast.makeText(context, "Indica el saldo recargado en GB.", Toast.LENGTH_SHORT).show()
            return
        }
        // Disable the button while permissions are being requested.
        step = PurchaseStep.Purchasing
        assistedNoticeShown = false
        runWithPermissions {
            sessionCounter += 1
            val session = sessionCounter
            val code = PrepaidPurchase.confirmationCode(option.number)
            val startedAt = System.currentTimeMillis()
            PurchaseSmsHolder.arm(session, startedAt)
            active = ActivePurchaseRequest(session, startedAt, option)
            smsText = ""
            ussdAckText = ""
            step = PurchaseStep.WaitingSms(null)

            val subscriptionId = resolveSubscriptionId()
            val canUseApi = apiUsable && (selectedSimSlot == null || subscriptionId != null)
            if (!canUseApi) {
                startAssisted(session, code, option)
                return@runWithPermissions
            }
            val started = UssdSystemApi.request(context, code, subscriptionId) { result ->
                if (active?.session != session) return@request
                when (result) {
                    is UssdSystemApi.Result.Success -> {
                        ussdAckText = result.response.trim()
                        step = PurchaseStep.WaitingSms(ussdAckText)
                    }
                    is UssdSystemApi.Result.Failure,
                    UssdSystemApi.Result.Timeout -> startAssisted(session, code, option)
                }
            }
            if (!started) {
                startAssisted(session, code, option)
            }
        }
    }

    fun backToMenu() {
        PurchaseSmsHolder.disarm()
        active = null
        step = PurchaseStep.MenuReady(rawMenuText, menuOptions)
    }

    fun finishPurchase(success: PurchaseStep.Success) {
        val gb = sizeText.toDoubleOrNull() ?: success.option.sizeGB ?: return
        val days = success.option.days ?: 35
        onPurchaseConfirmed(gb * 1024.0, days, success.raw)
        onDismiss()
    }

    LaunchedEffect(active?.session) {
        val request = active ?: return@LaunchedEffect
        PurchaseSmsHolder.lastSms.collect { sms ->
            val current = active ?: return@collect
            if (current.session != request.session) return@collect
            if (sms == null || sms.timestampMs < current.startedAt) return@collect
            applySmsConfirmation(sms.text)
        }
    }

    LaunchedEffect(active?.session) {
        val request = active ?: return@LaunchedEffect
        delay(180_000L)
        if (active?.session == request.session) {
            PurchaseSmsHolder.disarm()
            active = null
            step = PurchaseStep.Failure(
                "Tiempo de espera agotado. Si recibiste el SMS, pégalo para registrar la compra.",
                null
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { PurchaseSmsHolder.disarm() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Comprar paquete por USSD", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (val current = step) {
                    is PurchaseStep.MenuReady -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "La compra tiene un coste real en tu línea. La app nunca confirmará sola: " +
                                        "deberás elegir el paquete y tocar «Comprar».",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Text("Paquetes disponibles:", fontWeight = FontWeight.Bold)
                        current.options.forEach { option ->
                            FilterChip(
                                selected = false,
                                onClick = { selectOption(option) },
                                label = { Text(option.rawLine) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = manualOptionText,
                            onValueChange = { manualOptionText = it },
                            label = { Text("Número de opción") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("ussd_purchase_option_input")
                        )
                        TextButton(onClick = onOpenManual) {
                            Text("Registrar la compra manualmente")
                        }
                    }

                    is PurchaseStep.Confirming -> {
                        Text("Revisa la compra antes de continuar:", fontWeight = FontWeight.Bold)
                        Text(current.option.rawLine)
                        OutlinedTextField(
                            value = sizeText,
                            onValueChange = { sizeText = it },
                            label = { Text("Saldo recargado (GB)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = daysText,
                            onValueChange = { daysText = it },
                            label = { Text("Días de vigencia") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val gb = sizeText.toDoubleOrNull() ?: 0.0
                        if (gb > 0.0) {
                            val resultingMB = if (previousBalanceLost) gb * 1024.0
                                              else currentRemainingMB + gb * 1024.0
                            Text(
                                text = (if (previousBalanceLost) "Saldo vencido perdido. " else "") +
                                    "Saldo resultante estimado: " +
                                    DataFormatters.format(resultingMB, displayUnit),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "Se marcará ${PrepaidPurchase.confirmationCode(current.option.number)}. " +
                                "Ese código ya incluye la aceptación de la compra; después llega el SMS.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { step = PurchaseStep.MenuReady(rawMenuText, menuOptions) }) {
                            Text("Volver a los paquetes")
                        }
                    }

                    PurchaseStep.Purchasing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Confirmando la compra…")
                        }
                    }

                    is PurchaseStep.WaitingSms -> {
                        Text("Compra enviada. Esperando el SMS de confirmación…", fontWeight = FontWeight.Bold)
                        current.ussdAck?.let { ack ->
                            Text(
                                text = "Respuesta USSD: ${ack.replace('\n', ' ').take(160)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Si no se detecta solo, pega aquí el SMS de ETECSA:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = smsText,
                            onValueChange = { smsText = it },
                            label = { Text("SMS de confirmación") },
                            modifier = Modifier.fillMaxWidth().testTag("ussd_purchase_sms_input"),
                            minLines = 2,
                            maxLines = 4
                        )
                        TextButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    smsText = clip
                                } else {
                                    Toast.makeText(context, "Portapapeles vacío", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Pegar SMS")
                        }
                        TextButton(onClick = onOpenManual) {
                            Text("Registrar la compra manualmente")
                        }
                    }

                    is PurchaseStep.Success -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compra confirmada", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = DataFormatters.format(
                                (current.option.sizeGB ?: 0.0) * 1024.0,
                                displayUnit
                            ) + " recargados · ${current.option.days ?: 35} días",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = current.raw.replace('\n', ' ').take(200),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    is PurchaseStep.Failure -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("No se pudo confirmar", fontWeight = FontWeight.Bold)
                        }
                        Text(current.message, style = MaterialTheme.typography.bodyMedium)
                        current.raw?.let {
                            Text(
                                text = it.replace('\n', ' ').take(200),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        TextButton(onClick = onOpenManual) {
                            Text("Registrar manualmente")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            when (val current = step) {
                is PurchaseStep.MenuReady -> Button(
                    onClick = { continueFromMenu() },
                    enabled = manualOptionText.toIntOrNull() != null
                ) {
                    Text("Continuar")
                }

                is PurchaseStep.Confirming -> Button(
                    onClick = { startPurchase(current.option) },
                    modifier = Modifier.testTag("ussd_purchase_confirm")
                ) {
                    Text("Comprar")
                }

                is PurchaseStep.WaitingSms -> Button(
                    onClick = { applySmsConfirmation(smsText) },
                    enabled = smsText.isNotBlank()
                ) {
                    Text("Confirmar con SMS")
                }

                is PurchaseStep.Success -> Button(onClick = { finishPurchase(current) }) {
                    Text("Listo")
                }

                is PurchaseStep.Failure -> Button(onClick = { backToMenu() }) {
                    Text("Volver a los paquetes")
                }

                PurchaseStep.Purchasing -> {
                    Text("Espera…", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
