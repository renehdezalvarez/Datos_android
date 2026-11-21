package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Dialpad
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
import com.example.domain.UssdPurchaseParser
import com.example.domain.UssdPurchaseParser.PackageOption
import com.example.domain.UssdPurchaseParser.PurchaseConfirmation
import com.example.ussd.PurchaseSmsHolder
import com.example.ussd.UssdCaptureHolder
import com.example.ussd.UssdCatcherService
import com.example.ussd.UssdDialer
import com.example.ussd.UssdSystemApi
import kotlinx.coroutines.delay

private sealed interface PurchaseStep {
    data object Intro : PurchaseStep
    data object LoadingMenu : PurchaseStep
    data class MenuReady(val raw: String, val options: List<PackageOption>) : PurchaseStep
    data class Confirming(val option: PackageOption) : PurchaseStep
    data object Purchasing : PurchaseStep
    data class WaitingSms(val ussdAck: String?) : PurchaseStep
    data class Success(val option: PackageOption, val raw: String) : PurchaseStep
    data class Failure(val message: String, val raw: String?) : PurchaseStep
}

private enum class PurchaseStage { MENU, SELECT, SMS_WAIT }

private data class ActivePurchaseRequest(
    val session: Long,
    val stage: PurchaseStage,
    val startedAt: Long,
    val option: PackageOption?
)

/**
 * Buys a prepaid package for line 2 through the carrier's USSD menu.
 *
 * The menu response is parsed into in-app options. Once the user picks one
 * and confirms, the app dials the composed code with the final `1`; the
 * official telephony API is tried first and the accessibility catcher is the
 * fallback. The final confirmation arrives by SMS, so the app listens for it
 * (or lets the user paste it). Nothing is registered until that SMS is read.
 */
@Composable
fun UssdPurchaseDialog(
    displayUnit: String,
    selectedSimSlot: Int?,
    currentTotalMB: Double,
    currentRemainingMB: Double,
    onDismiss: () -> Unit,
    onOpenManual: () -> Unit,
    onPurchaseConfirmed: (packageMB: Double, totalDays: Int, rawText: String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val systemApiAvailable = remember(context) { UssdSystemApi.isAvailable(context) }
    val catcherEnabled = remember(context) { UssdCatcherService.isEnabled(context) }

    var step by remember { mutableStateOf<PurchaseStep>(PurchaseStep.Intro) }
    var sessionCounter by remember { mutableStateOf(0L) }
    var active by remember { mutableStateOf<ActivePurchaseRequest?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var rawMenuText by remember { mutableStateOf("") }
    var menuOptions by remember { mutableStateOf<List<PackageOption>>(emptyList()) }
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

    fun openAccessibilitySettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo abrir la configuración", Toast.LENGTH_SHORT).show()
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

    fun startAssisted(session: Long, stage: PurchaseStage, code: String, option: PackageOption?) {
        UssdCaptureHolder.clearPurchase()
        UssdCaptureHolder.armPurchase()
        active = ActivePurchaseRequest(session, stage, System.currentTimeMillis(), option)
        UssdDialer.dial(
            context,
            code,
            directCall = UssdDialer.hasCallPermission(context),
            selectedSlot = selectedSimSlot
        )
        Toast.makeText(
            context,
            when (stage) {
                PurchaseStage.MENU ->
                    "La API no respondió: marca el código y elige la opción; la app leerá la respuesta."
                PurchaseStage.SELECT ->
                    "La API no respondió: se marcó la opción; confirma en el diálogo si el operador lo pide."
                PurchaseStage.SMS_WAIT ->
                    "La API no respondió: se marcó el código final. Espera el SMS de confirmación."
            },
            Toast.LENGTH_LONG
        ).show()
    }

    fun handleMenuResponse(session: Long, text: String) {
        if (active?.session != session) return
        val options = UssdPurchaseParser.parseMenu(text)
        UssdCaptureHolder.disarmPurchase()
        active = null
        rawMenuText = text
        menuOptions = options
        step = PurchaseStep.MenuReady(text, options)
    }

    fun showResult(result: PurchaseConfirmation, text: String) {
        if (result.failure || !result.success) {
            step = PurchaseStep.Failure(result.message, text)
            return
        }
        val gb = result.sizeGB ?: sizeText.toDoubleOrNull()
        val days = result.days ?: daysText.toIntOrNull()?.takeIf { it > 0 } ?: 35
        if (gb == null || gb <= 0.0) {
            step = PurchaseStep.Failure(
                "La compra se confirmó pero no se pudo saber el tamaño del paquete. Regístralo manualmente.",
                text
            )
            return
        }
        val confirmedOption = PackageOption(
            number = manualOptionText.toIntOrNull() ?: 0,
            sizeGB = gb,
            days = days,
            price = result.price,
            rawLine = text.trim()
        )
        step = PurchaseStep.Success(confirmedOption, text)
    }

    fun handlePurchaseResponse(session: Long, text: String) {
        val request = active ?: return
        if (request.session != session) return
        val result = UssdPurchaseParser.parseConfirmation(text)
        UssdCaptureHolder.disarmPurchase()

        // The carrier asks for the final `1` after the option is selected.
        if (result.needsConfirmation && request.stage == PurchaseStage.SELECT) {
            request.option?.let { sendFinalConfirmation(session, it) }
            return
        }
        // After the final digit the purchase is confirmed by SMS, not here.
        if (request.stage == PurchaseStage.SMS_WAIT) return

        active = null
        showResult(result, text)
    }

    /** Applies a carrier SMS (auto-captured or pasted by the user). */
    fun applySmsConfirmation(text: String) {
        if (text.isBlank()) return
        val result = UssdPurchaseParser.parseSmsConfirmation(text)
        PurchaseSmsHolder.disarm()
        UssdCaptureHolder.disarmPurchase()
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

    fun requestMenu() {
        runWithPermissions {
            UssdCaptureHolder.clearPurchase()
            UssdCaptureHolder.disarmPurchase()
            sessionCounter += 1
            val session = sessionCounter
            active = ActivePurchaseRequest(session, PurchaseStage.MENU, System.currentTimeMillis(), null)
            step = PurchaseStep.LoadingMenu

            val subscriptionId = resolveSubscriptionId()
            val canUseApi = systemApiAvailable && (selectedSimSlot == null || subscriptionId != null)
            if (!canUseApi) {
                startAssisted(session, PurchaseStage.MENU, PrepaidPurchase.MENU_CODE, null)
                return@runWithPermissions
            }
            val started = UssdSystemApi.request(
                context,
                PrepaidPurchase.MENU_CODE,
                subscriptionId
            ) { result ->
                if (active?.session != session) return@request
                when (result) {
                    is UssdSystemApi.Result.Success -> handleMenuResponse(session, result.response)
                    is UssdSystemApi.Result.Failure,
                    UssdSystemApi.Result.Timeout -> startAssisted(
                        session,
                        PurchaseStage.MENU,
                        PrepaidPurchase.MENU_CODE,
                        null
                    )
                }
            }
            if (!started) {
                startAssisted(session, PurchaseStage.MENU, PrepaidPurchase.MENU_CODE, null)
            }
        }
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
            Toast.makeText(context, "Indica el tamaño del paquete en GB.", Toast.LENGTH_SHORT).show()
            return
        }
        runWithPermissions {
            UssdCaptureHolder.clearPurchase()
            UssdCaptureHolder.disarmPurchase()
            sessionCounter += 1
            val session = sessionCounter
            val code = PrepaidPurchase.selectionCode(option.number)
            active = ActivePurchaseRequest(session, PurchaseStage.SELECT, System.currentTimeMillis(), option)
            step = PurchaseStep.Purchasing

            val subscriptionId = resolveSubscriptionId()
            val canUseApi = systemApiAvailable && (selectedSimSlot == null || subscriptionId != null)
            if (!canUseApi) {
                startAssisted(session, PurchaseStage.SELECT, code, option)
                return@runWithPermissions
            }
            val started = UssdSystemApi.request(context, code, subscriptionId) { result ->
                if (active?.session != session) return@request
                when (result) {
                    is UssdSystemApi.Result.Success -> handlePurchaseResponse(session, result.response)
                    is UssdSystemApi.Result.Failure,
                    UssdSystemApi.Result.Timeout -> startAssisted(session, PurchaseStage.SELECT, code, option)
                }
            }
            if (!started) {
                startAssisted(session, PurchaseStage.SELECT, code, option)
            }
        }
    }

    /** Sends the final `1`, then waits for the carrier's confirmation SMS. */
    fun sendFinalConfirmation(session: Long, option: PackageOption) {
        runWithPermissions {
            UssdCaptureHolder.clearPurchase()
            UssdCaptureHolder.disarmPurchase()
            val code = PrepaidPurchase.confirmationCode(option.number)
            val startedAt = System.currentTimeMillis()
            PurchaseSmsHolder.arm(session, startedAt)
            active = ActivePurchaseRequest(session, PurchaseStage.SMS_WAIT, startedAt, option)
            smsText = ""
            ussdAckText = ""
            step = PurchaseStep.WaitingSms(null)

            val subscriptionId = resolveSubscriptionId()
            val canUseApi = systemApiAvailable && (selectedSimSlot == null || subscriptionId != null)
            if (!canUseApi) {
                startAssisted(session, PurchaseStage.SMS_WAIT, code, option)
                return@runWithPermissions
            }
            val started = UssdSystemApi.request(context, code, subscriptionId) { result ->
                if (active?.session != session) return@request
                when (result) {
                    is UssdSystemApi.Result.Success -> {
                        // The USSD answer is only an acknowledgement: the real
                        // confirmation arrives later by SMS.
                        ussdAckText = result.response.trim()
                        step = PurchaseStep.WaitingSms(ussdAckText)
                    }
                    is UssdSystemApi.Result.Failure,
                    UssdSystemApi.Result.Timeout -> startAssisted(session, PurchaseStage.SMS_WAIT, code, option)
                }
            }
            if (!started) {
                startAssisted(session, PurchaseStage.SMS_WAIT, code, option)
            }
        }
    }

    fun finishPurchase(success: PurchaseStep.Success) {
        val gb = success.option.sizeGB ?: return
        val days = success.option.days ?: 35
        onPurchaseConfirmed(gb * 1024.0, days, success.raw)
        onDismiss()
    }

    // Captures from the accessibility fallback are matched against the active
    // session, so a late dialog from a previous attempt is ignored.
    LaunchedEffect(active?.session) {
        val request = active ?: return@LaunchedEffect
        UssdCaptureHolder.lastPurchaseCapture.collect { capture ->
            val current = active ?: return@collect
            if (current.session != request.session) return@collect
            if (capture == null || capture.timestampMs < current.startedAt) return@collect
            when (current.stage) {
                PurchaseStage.MENU -> handleMenuResponse(current.session, capture.text)
                PurchaseStage.SELECT -> handlePurchaseResponse(current.session, capture.text)
                PurchaseStage.SMS_WAIT -> Unit
            }
        }
    }

    LaunchedEffect(active?.session) {
        val request = active ?: return@LaunchedEffect
        if (request.stage != PurchaseStage.SMS_WAIT) return@LaunchedEffect
        PurchaseSmsHolder.lastSms.collect { sms ->
            val current = active ?: return@collect
            if (current.session != request.session || current.stage != PurchaseStage.SMS_WAIT) return@collect
            if (sms == null || sms.timestampMs < current.startedAt) return@collect
            applySmsConfirmation(sms.text)
        }
    }

    LaunchedEffect(active?.session) {
        val request = active ?: return@LaunchedEffect
        delay(180_000L)
        if (active?.session == request.session) {
            UssdCaptureHolder.disarmPurchase()
            PurchaseSmsHolder.disarm()
            active = null
            step = PurchaseStep.Failure(
                "Tiempo de espera agotado. Si recibiste el SMS, pégalo para registrar la compra.",
                null
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            UssdCaptureHolder.disarmPurchase()
            PurchaseSmsHolder.disarm()
        }
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
                    PurchaseStep.Intro -> {
                        Text(
                            text = "Se consultarán los paquetes disponibles con ${PrepaidPurchase.MENU_CODE}. " +
                                "Podrás ver las opciones aquí antes de confirmar.",
                            style = MaterialTheme.typography.bodyMedium
                        )
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
                        if (!catcherEnabled) {
                            TextButton(onClick = { openAccessibilitySettings() }) {
                                Text("Activar lector USSD como respaldo")
                            }
                        }
                        TextButton(onClick = onOpenManual) {
                            Text("Registrar la compra manualmente")
                        }
                    }

                    PurchaseStep.LoadingMenu, PurchaseStep.Purchasing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (step is PurchaseStep.Purchasing) {
                                    "Confirmando la compra…"
                                } else {
                                    "Consultando paquetes disponibles…"
                                }
                            )
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

                    is PurchaseStep.MenuReady -> {
                        Text("Paquetes detectados:", fontWeight = FontWeight.Bold)
                        if (current.options.isNotEmpty()) {
                            current.options.forEach { option ->
                                FilterChip(
                                    selected = false,
                                    onClick = { selectOption(option) },
                                    label = { Text(option.rawLine) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Text(
                                text = "No pude interpretar el menú. Puedes escribir el número de opción y el tamaño.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedTextField(
                            value = manualOptionText,
                            onValueChange = { manualOptionText = it },
                            label = { Text("Número de opción") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("ussd_purchase_option_input")
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = current.raw.replace('\n', ' ').take(300),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    is PurchaseStep.Confirming -> {
                        Text("Revisa la compra antes de continuar:", fontWeight = FontWeight.Bold)
                        Text(current.option.rawLine)
                        OutlinedTextField(
                            value = sizeText,
                            onValueChange = { sizeText = it },
                            label = { Text("Tamaño del paquete (GB)") },
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
                            Text(
                                text = "Saldo resultante estimado: " +
                                    DataFormatters.format(currentRemainingMB + gb * 1024.0, displayUnit) +
                                    " (paquete total " +
                                    DataFormatters.format(currentTotalMB + gb * 1024.0, displayUnit) + ")",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "Primero se marca ${PrepaidPurchase.selectionCode(current.option.number)} " +
                                "y, cuando el operador pida confirmar, " +
                                "${PrepaidPurchase.confirmationCode(current.option.number)}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { step = PurchaseStep.MenuReady(rawMenuText, menuOptions) }) {
                            Text("Volver a las opciones")
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
                            ) + " · ${current.option.days ?: 35} días",
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
                        if (!catcherEnabled) {
                            TextButton(onClick = { openAccessibilitySettings() }) {
                                Text("Activar lector de accesibilidad")
                            }
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
                PurchaseStep.Intro -> Button(
                    onClick = { requestMenu() },
                    modifier = Modifier.testTag("ussd_purchase_start")
                ) {
                    Icon(Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ver paquetes")
                }

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

                is PurchaseStep.Failure -> Button(onClick = { requestMenu() }) {
                    Text("Reintentar")
                }

                PurchaseStep.LoadingMenu, PurchaseStep.Purchasing -> {
                    Text("Espera…", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
