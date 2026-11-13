package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.domain.UssdParser
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.UssdAutoReading
import com.example.ussd.UssdCatcherService
import com.example.ussd.UssdSystemApi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UssdReadingDialog(
    lineId: Int,
    lineName: String,
    currentUssdCode: String,
    packageTotalMB: Double,
    totalCycleDays: Int,
    displayUnit: String,
    selectedSimSlot: Int?,
    autoReading: UssdAutoReading?,
    onDismiss: () -> Unit,
    onSaveUssdCode: (String) -> Unit,
    onSelectSim: (Int?) -> Unit,
    onStartAutoConsult: (useAccessibility: Boolean) -> Long,
    onEnableAccessibilityFallback: (sessionId: Long) -> Unit,
    onApplyAutoReading: (sessionId: Long, text: String) -> Unit,
    onConfirmReading: (remainingMB: Double, note: String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val systemApiAvailable = remember(context) { UssdSystemApi.isAvailable(context) }

    var ussdCodeText by remember(currentUssdCode) { mutableStateOf(currentUssdCode) }
    var inputText by remember { mutableStateOf("") }
    var manualUnit by remember(displayUnit) { mutableStateOf(displayUnit) }
    var noteText by remember { mutableStateOf("Lectura USSD ($currentUssdCode)") }
    var catcherEnabled by remember { mutableStateOf(UssdCatcherService.isEnabled(context)) }

    // Re-check the accessibility service state when the user comes back
    // from the system settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                catcherEnabled = UssdCatcherService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val parsedBalance = remember(inputText, manualUnit) {
        UssdParser.parse(inputText, manualUnit)
    }

    // Only surface the auto reading captured while this dialog is open.
    val dialogOpenMs = remember { System.currentTimeMillis() }
    val latestAutoReading = autoReading?.takeIf { it.lineId == lineId && it.id >= dialogOpenMs }

    // Same automatic flow the quick "Consultar Saldo" buttons use.
    val consult = rememberUssdConsultation(
        lineId = lineId,
        ussdCode = ussdCodeText,
        selectedSimSlot = selectedSimSlot,
        onStartAutoConsult = onStartAutoConsult,
        onEnableAccessibilityFallback = onEnableAccessibilityFallback,
        onApplyAutoReading = onApplyAutoReading
    )

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Dialpad,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Lectura por USSD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(lineName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: USSD Code Configuration
                Text(
                    text = "1. Código USSD de tu operadora",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = ussdCodeText,
                    onValueChange = {
                        ussdCodeText = it
                        onSaveUssdCode(it)
                        noteText = "Lectura USSD ($it)"
                    },
                    label = { Text("Código USSD") },
                    placeholder = { Text("Ej. *111#") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("ussd_code_input")
                )

                // Section 2: SIM selection + automatic dialing/reading
                Text(
                    text = "2. SIM de la línea y lectura del saldo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                SimSelector(
                    selectedSlotIndex = selectedSimSlot,
                    onSelect = onSelectSim,
                    modifier = Modifier.fillMaxWidth()
                )

                if (systemApiAvailable) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusSuccess.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lectura automática mediante la API de telefonía del sistema." +
                                    if (catcherEnabled) {
                                        " El lector de accesibilidad queda como respaldo."
                                    } else {
                                        " Si el operador la bloquea, podrás pegar la respuesta manualmente."
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!catcherEnabled) {
                        TextButton(
                            onClick = { openAccessibilitySettings() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Activar lector de accesibilidad como respaldo",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                } else if (catcherEnabled) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusSuccess.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lectura automática activada: la respuesta del operador se registrará sola.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Activa el lector automático (solo una vez) para que la app lea la respuesta por ti:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { openAccessibilitySettings() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Activar «Lector de respuestas USSD»", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Button(
                    onClick = { consult.start() },
                    modifier = Modifier.fillMaxWidth().testTag("mark_and_read_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Marcar $ussdCodeText y leer el saldo")
                }

                // Automatic reading result
                if (latestAutoReading != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusSuccess.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Registrado automáticamente",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusSuccess
                                )
                            }
                            if (latestAutoReading.remainingMB != null) {
                                Text(
                                    text = "Saldo: ${DataFormatters.format(latestAutoReading.remainingMB, displayUnit)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (latestAutoReading.daysRemaining != null) {
                                Text(
                                    text = "Días restantes del paquete: ${latestAutoReading.daysRemaining}" +
                                        (latestAutoReading.newCycleDay?.let { " (día $it de $totalCycleDays)" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = "«${latestAutoReading.rawText.replace('\n', ' ').take(140)}»",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Section 3: Manual capture (fallback)
                Text(
                    text = "3. Captura manual del saldo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Si prefieres anotarlo a mano, pega el mensaje de la operadora o escribe tus megas/gigas:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Respuesta del operador o saldo") },
                    placeholder = { Text("Ej. 'Le quedan 4.25 GB' o '3500 MB'") },
                    leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("ussd_response_input"),
                    minLines = 2,
                    maxLines = 4
                )

                OutlinedButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            inputText = clip
                        } else {
                            Toast.makeText(context, "Portapapeles vacío", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pegar del portapapeles", style = MaterialTheme.typography.labelSmall)
                }

                // Parsed balance indicator
                if (parsedBalance != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Saldo detectado con éxito:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = DataFormatters.format(parsedBalance.valueMB, displayUnit),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                } else if (inputText.isNotBlank()) {
                    Text(
                        text = "Introduce una cifra válida (ej. 3.5 o '4.2 GB')",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Manual unit selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = manualUnit == "GB",
                        onClick = { manualUnit = "GB" },
                        label = { Text("Unidad: Gigabytes (GB)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = manualUnit == "MB",
                        onClick = { manualUnit = "MB" },
                        label = { Text("Megabytes (MB)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Nota de la lectura") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (latestAutoReading != null) {
                // Already saved automatically; nothing left to confirm.
                Button(onClick = onDismiss, modifier = Modifier.testTag("close_after_auto_button")) {
                    Text("Listo")
                }
            } else {
                Button(
                    onClick = {
                        parsedBalance?.let {
                            val safeMB = it.valueMB.coerceIn(0.0, packageTotalMB)
                            onConfirmReading(safeMB, noteText.ifBlank { "Lectura USSD ($ussdCodeText)" })
                            onDismiss()
                        }
                    },
                    enabled = parsedBalance != null && parsedBalance.valueMB >= 0.0,
                    modifier = Modifier.testTag("apply_ussd_reading_button")
                ) {
                    Text("Guardar Lectura")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

