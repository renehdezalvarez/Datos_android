package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.DataUsage
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.domain.UssdParser
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.UssdAutoReading
import com.example.ussd.UssdCatcherService

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateBalanceDialog(
    currentRemainingMB: Double,
    packageTotalMB: Double,
    displayUnit: String,
    onDismiss: () -> Unit,
    onConfirm: (newRemainingMB: Double) -> Unit
) {
    var inputUnit by remember { mutableStateOf(displayUnit) }
    var valueText by remember {
        val initialVal = if (displayUnit == "GB") {
            String.format(java.util.Locale.US, "%.2f", currentRemainingMB / 1024.0)
        } else {
            currentRemainingMB.toInt().toString()
        }
        mutableStateOf(initialVal)
    }

    val packageGB = packageTotalMB / 1024.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Actualizar Saldo Restante", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Introduce el saldo de datos que te queda en este momento según tu operador:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Unit Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = inputUnit == "GB",
                        onClick = {
                            if (inputUnit != "GB") {
                                val currentMb = valueText.toDoubleOrNull() ?: 0.0
                                valueText = String.format(java.util.Locale.US, "%.2f", currentMb / 1024.0)
                                inputUnit = "GB"
                            }
                        },
                        label = { Text("Gigabytes (GB)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = inputUnit == "MB",
                        onClick = {
                            if (inputUnit != "MB") {
                                val currentGb = valueText.toDoubleOrNull() ?: 0.0
                                valueText = (currentGb * 1024.0).toInt().toString()
                                inputUnit = "MB"
                            }
                        },
                        label = { Text("Megabytes (MB)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Saldo Restante ($inputUnit)") },
                    placeholder = { Text(if (inputUnit == "GB") "Ej. 3.5" else "Ej. 3500") },
                    leadingIcon = { Icon(Icons.Default.DataUsage, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("remaining_balance_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick presets
                Text(
                    text = "Valores rápidos para paquete de ${packageGB.toInt()} GB:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val fractions = listOf(0.75 to "75%", 0.50 to "50%", 0.25 to "25%", 0.10 to "10%")
                    fractions.forEach { (pct, label) ->
                        val presetMB = packageTotalMB * pct
                        FilterChip(
                            selected = false,
                            onClick = {
                                valueText = if (inputUnit == "GB") {
                                    String.format(java.util.Locale.US, "%.2f", presetMB / 1024.0)
                                } else {
                                    presetMB.toInt().toString()
                                }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawVal = valueText.toDoubleOrNull() ?: 0.0
                    val finalMB = if (inputUnit == "GB") rawVal * 1024.0 else rawVal
                    onConfirm(finalMB.coerceIn(0.0, packageTotalMB))
                    onDismiss()
                },
                modifier = Modifier.testTag("save_balance_button")
            ) {
                Text("Guardar Saldo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigurePackageDialog(
    currentPackageTotalMB: Double,
    currentRemainingMB: Double,
    currentTotalDays: Int,
    displayUnit: String,
    onDismiss: () -> Unit,
    onConfirm: (packageMB: Double, remainingMB: Double, totalDays: Int) -> Unit
) {
    var inputUnit by remember(displayUnit) { mutableStateOf(displayUnit) }

    var sizeText by remember(currentPackageTotalMB, inputUnit) {
        val formatted = if (inputUnit == "GB") {
            val gb = currentPackageTotalMB / 1024.0
            if (gb == gb.toLong().toDouble()) gb.toLong().toString() else String.format(java.util.Locale.US, "%.2f", gb)
        } else {
            currentPackageTotalMB.toInt().toString()
        }
        mutableStateOf(formatted)
    }

    var remainingText by remember(currentRemainingMB, inputUnit) {
        val formatted = if (inputUnit == "GB") {
            val gb = currentRemainingMB / 1024.0
            if (gb == gb.toLong().toDouble()) gb.toLong().toString() else String.format(java.util.Locale.US, "%.2f", gb)
        } else {
            currentRemainingMB.toInt().toString()
        }
        mutableStateOf(formatted)
    }

    var daysText by remember(currentTotalDays) { mutableStateOf(currentTotalDays.toString()) }

    val packagePresetsGB = listOf(1.0, 2.0, 3.0, 5.0, 8.0, 10.0, 15.0, 20.0, 30.0, 50.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Paquete Contratado", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Ajusta la capacidad de tu paquete de datos, tu saldo restante y la duración del ciclo:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Unit Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = inputUnit == "GB",
                        onClick = {
                            if (inputUnit != "GB") {
                                val currentMb = sizeText.toDoubleOrNull() ?: 0.0
                                val curRemMb = remainingText.toDoubleOrNull() ?: 0.0
                                sizeText = String.format(java.util.Locale.US, "%.2f", currentMb / 1024.0)
                                remainingText = String.format(java.util.Locale.US, "%.2f", curRemMb / 1024.0)
                                inputUnit = "GB"
                            }
                        },
                        label = { Text("Gigabytes (GB)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = inputUnit == "MB",
                        onClick = {
                            if (inputUnit != "MB") {
                                val currentGb = sizeText.toDoubleOrNull() ?: 0.0
                                val curRemGb = remainingText.toDoubleOrNull() ?: 0.0
                                sizeText = (currentGb * 1024.0).toInt().toString()
                                remainingText = (curRemGb * 1024.0).toInt().toString()
                                inputUnit = "MB"
                            }
                        },
                        label = { Text("Megabytes (MB)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = sizeText,
                    onValueChange = { sizeText = it },
                    label = { Text("Tamaño total del paquete ($inputUnit)") },
                    placeholder = { Text(if (inputUnit == "GB") "Ej. 10" else "Ej. 10240") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("package_size_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Paquetes frecuentes:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    packagePresetsGB.forEach { gbVal ->
                        val presetStr = if (inputUnit == "GB") {
                            gbVal.toInt().toString()
                        } else {
                            (gbVal * 1024.0).toInt().toString()
                        }
                        val isSelected = sizeText == presetStr

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                sizeText = presetStr
                                // Suggest remaining balance as full if empty or 0
                                if (remainingText.isBlank() || (remainingText.toDoubleOrNull() ?: 0.0) == 0.0) {
                                    remainingText = presetStr
                                }
                            },
                            label = { Text("${gbVal.toInt()} GB", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = remainingText,
                    onValueChange = { remainingText = it },
                    label = { Text("Saldo restante actual ($inputUnit)") },
                    placeholder = { Text(if (inputUnit == "GB") "Ej. 4.5" else "Ej. 4500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("package_remaining_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text("Días del ciclo (duración mensual)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("package_cycle_days_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawSize = sizeText.toDoubleOrNull() ?: 10.0
                    val rawRemaining = remainingText.toDoubleOrNull() ?: rawSize
                    val packageMB = if (inputUnit == "GB") rawSize * 1024.0 else rawSize
                    val remMB = if (inputUnit == "GB") rawRemaining * 1024.0 else rawRemaining
                    val days = daysText.toIntOrNull() ?: 30

                    onConfirm(packageMB.coerceAtLeast(100.0), remMB.coerceIn(0.0, packageMB), days)
                    onDismiss()
                },
                enabled = (sizeText.toDoubleOrNull() ?: 0.0) > 0.0,
                modifier = Modifier.testTag("save_package_button")
            ) {
                Text("Guardar Paquete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun AdjustCycleDaysDialog(
    currentDay: Int,
    totalCycleDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (currentDay: Int, totalCycleDays: Int) -> Unit,
    onResetCalendar: () -> Unit
) {
    var curDayText by remember { mutableStateOf(currentDay.toString()) }
    var totDaysText by remember { mutableStateOf(totalCycleDays.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ajustar Días del Ciclo", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "Ajusta en qué día del paquete te encuentras y cuántos días dura tu ciclo de facturación:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = curDayText,
                        onValueChange = { curDayText = it },
                        label = { Text("Día actual") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("dialog_day_input")
                    )

                    OutlinedTextField(
                        value = totDaysText,
                        onValueChange = { totDaysText = it },
                        label = { Text("Días totales") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("dialog_total_days_input")
                    )
                }

                val cd = curDayText.toIntOrNull() ?: currentDay
                val td = totDaysText.toIntOrNull() ?: totalCycleDays
                val rem = (td - cd).coerceAtLeast(0)

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Días restantes para el fin del ciclo: $rem días",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onResetCalendar()
                        onDismiss()
                    }
                ) {
                    Text("Sincronizar con calendario actual real")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cd = curDayText.toIntOrNull() ?: currentDay
                    val td = totDaysText.toIntOrNull() ?: totalCycleDays
                    onConfirm(cd, td)
                    onDismiss()
                },
                modifier = Modifier.testTag("apply_cycle_days_button")
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun AddBalanceLogDialog(
    currentDay: Int,
    displayUnit: String,
    initialLineId: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (lineId: Int, remainingMB: Double, cycleDay: Int, note: String) -> Unit
) {
    var selectedLineId by remember(initialLineId) { mutableStateOf(initialLineId) }
    var valueText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf(currentDay.toString()) }
    var unit by remember { mutableStateOf(displayUnit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Lectura de Saldo", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Selecciona la línea y guarda la lectura de datos actual para el historial de ritmo de gasto:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Line Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedLineId == 1,
                        onClick = { selectedLineId = 1 },
                        label = { Text("Línea 1 (Mensual)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedLineId == 2,
                        onClick = { selectedLineId = 2 },
                        label = { Text("Línea 2 (Prepago 35d)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Saldo Restante ($unit)") },
                    placeholder = { Text(if (unit == "GB") "Ej. 3.2" else "Ej. 3200") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("log_balance_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = it },
                    label = { Text("Día del ciclo (1-${if (selectedLineId == 2) 35 else 31})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Nota (opcional)") },
                    placeholder = { Text("Ej. Revisión quincenal, después de descargar actualización") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val raw = valueText.toDoubleOrNull() ?: 0.0
                    val day = dayText.toIntOrNull() ?: currentDay
                    val mb = if (unit == "GB") raw * 1024.0 else raw
                    if (mb > 0) {
                        onConfirm(selectedLineId, mb, day, noteText)
                        onDismiss()
                    }
                },
                enabled = (valueText.toDoubleOrNull() ?: 0.0) > 0,
                modifier = Modifier.testTag("save_log_button")
            ) {
                Text("Guardar Lectura")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewPrepaidPurchaseDialog(
    currentPackageTotalMB: Double,
    onDismiss: () -> Unit,
    onConfirm: (packageMB: Double, totalDays: Int) -> Unit
) {
    var sizeGBText by remember {
        val gb = currentPackageTotalMB / 1024.0
        mutableStateOf(if (gb == gb.toLong().toDouble()) gb.toLong().toString() else String.format(java.util.Locale.US, "%.1f", gb))
    }
    var daysText by remember { mutableStateOf("35") }
    val presets = listOf(2.0, 4.0, 6.0, 8.0, 10.0, 15.0, 20.0, 30.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nueva Compra Prepago (35d)", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "Registra la compra de un nuevo paquete. El saldo se reiniciará con la capacidad total y el ciclo comenzará desde el día 1 con vigencia de 35 días:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = sizeGBText,
                    onValueChange = { sizeGBText = it },
                    label = { Text("Capacidad del paquete (GB)") },
                    placeholder = { Text("Ej. 6") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_purchase_gb_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { gbVal ->
                        val presetStr = gbVal.toInt().toString()
                        FilterChip(
                            selected = sizeGBText == presetStr,
                            onClick = { sizeGBText = presetStr },
                            label = { Text("${gbVal.toInt()} GB", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text("Días de vigencia") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val gb = sizeGBText.toDoubleOrNull() ?: 6.0
                    val days = daysText.toIntOrNull() ?: 35
                    onConfirm(gb * 1024.0, days)
                    onDismiss()
                },
                enabled = (sizeGBText.toDoubleOrNull() ?: 0.0) > 0.0,
                modifier = Modifier.testTag("confirm_new_purchase_button")
            ) {
                Text("Activar Paquete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UssdReadingDialog(
    lineId: Int,
    lineName: String,
    currentUssdCode: String,
    packageTotalMB: Double,
    totalCycleDays: Int,
    displayUnit: String,
    autoReading: UssdAutoReading?,
    onDismiss: () -> Unit,
    onSaveUssdCode: (String) -> Unit,
    onStartAutoConsult: () -> Unit,
    onConfirmReading: (remainingMB: Double, note: String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val parsedBalance = remember(inputText, manualUnit) {
        UssdParser.parse(inputText, manualUnit)
    }

    // Only surface the auto reading captured while this dialog is open.
    val dialogOpenMs = remember { System.currentTimeMillis() }
    val latestAutoReading = autoReading?.takeIf { it.lineId == lineId && it.id >= dialogOpenMs }

    fun dialUssd(code: String, directCall: Boolean) {
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

    // ACTION_CALL dials the USSD code right away; without the permission it
    // falls back to ACTION_DIAL (the user presses "call" manually).
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        dialUssd(ussdCodeText, directCall = granted)
    }

    fun onMarkAndReadClick() {
        onStartAutoConsult()
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            dialUssd(ussdCodeText, directCall = true)
        } else {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
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

                // Section 2: Automatic dialing + reading
                Text(
                    text = "2. Marcar y leer el saldo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (catcherEnabled) {
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
                                text = "Lectura automática activada: la respuesta del operador se registrará sola y el diálogo se cerrará solo.",
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
                                onClick = {
                                    try {
                                        context.startActivity(
                                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "No se pudo abrir la configuración", Toast.LENGTH_SHORT).show()
                                    }
                                },
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
                    onClick = { onMarkAndReadClick() },
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

