package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Diálogo único de configuración de una línea. Sustituye al antiguo formulario
 * de paquete y al de ajuste de días: ambos campos se guardan en una sola
 * operación para no sobrescribir la fila dos veces.
 *
 * El día se envía como null cuando el usuario no lo cambió, así que abrir el
 * diálogo para editar solo el saldo no reancla el ciclo de la prepaga.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditLineDialog(
    lineId: Int,
    currentPackageTotalMB: Double,
    currentRemainingMB: Double,
    currentDay: Int,
    totalCycleDays: Int,
    displayUnit: String,
    onDismiss: () -> Unit,
    onConfirm: (
        packageMB: Double,
        remainingMB: Double,
        currentDay: Int?,
        totalDays: Int
    ) -> Unit,
    onResetCalendar: () -> Unit
) {
    val monthlyCalendarCycle = lineId == 1
    val showPackageSize = lineId == 1

    var inputUnit by remember { mutableStateOf(displayUnit) }
    var sizeText by remember { mutableStateOf(formatAmount(currentPackageTotalMB, displayUnit)) }
    var remainingText by remember { mutableStateOf(formatAmount(currentRemainingMB, displayUnit)) }
    var dayText by remember(currentDay) { mutableStateOf(currentDay.toString()) }
    val daysTextInitial = totalCycleDays.toString()
    var totalDaysText by remember(daysTextInitial) { mutableStateOf(daysTextInitial) }

    val packagePresetsGB = listOf(1.0, 2.0, 3.0, 5.0, 8.0, 10.0, 15.0, 20.0, 30.0, 50.0)

    fun switchUnit(newUnit: String) {
        if (inputUnit == newUnit) return
        sizeText = convertAmount(sizeText, inputUnit, newUnit)
        remainingText = convertAmount(remainingText, inputUnit, newUnit)
        inputUnit = newUnit
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (monthlyCalendarCycle) "Modificar línea mensual" else "Modificar línea prepaga",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (showPackageSize) {
                        "Ajusta la capacidad del paquete, tu saldo restante y en qué día del mes estás."
                    } else {
                        "Ajusta tu saldo disponible y la duración del ciclo. El consumo se calcula a partir de tus lecturas."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = inputUnit == "GB",
                        onClick = { switchUnit("GB") },
                        label = { Text("GB") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = inputUnit == "MB",
                        onClick = { switchUnit("MB") },
                        label = { Text("MB") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (showPackageSize) {
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

                    Text(
                        text = "Paquetes frecuentes:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            FilterChip(
                                selected = sizeText == presetStr,
                                onClick = {
                                    sizeText = presetStr
                                    if (remainingText.isBlank() || (toMB(remainingText, inputUnit) ?: 0.0) == 0.0) {
                                        remainingText = presetStr
                                    }
                                },
                                label = { Text("${gbVal.toInt()} GB", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

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

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = { dayText = it },
                        label = { Text("Día del ciclo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_day_input")
                    )
                    OutlinedTextField(
                        value = totalDaysText,
                        onValueChange = { if (!monthlyCalendarCycle) totalDaysText = it },
                        readOnly = monthlyCalendarCycle,
                        label = { Text("Días totales") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_total_days_input")
                    )
                }

                Text(
                    text = if (monthlyCalendarCycle) {
                        "El ciclo sigue el mes calendario (28-31 días). El día se corrige aquí y sigue avanzando solo."
                    } else {
                        "Cambiar el día reancla la fecha de compra; el contador sigue avanzando desde ahí."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(onClick = onResetCalendar) {
                    Text(
                        if (monthlyCalendarCycle) {
                            "Sincronizar con calendario real"
                        } else {
                            "Restablecer vigencia a 35 días"
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val packageMB = when {
                        showPackageSize -> toMB(sizeText, inputUnit) ?: currentPackageTotalMB
                        else -> currentPackageTotalMB
                    }
                    val remainingMB = toMB(remainingText, inputUnit) ?: currentRemainingMB
                    val changedDay = dayText.toIntOrNull()?.takeIf { it != currentDay }
                    val days = totalDaysText.toIntOrNull() ?: totalCycleDays

                    onConfirm(
                        packageMB.coerceAtLeast(100.0),
                        remainingMB,
                        changedDay,
                        days
                    )
                    onDismiss()
                },
                enabled = (toMB(remainingText, inputUnit) ?: -1.0) >= 0.0 &&
                    (!showPackageSize || (toMB(sizeText, inputUnit) ?: 0.0) > 0.0),
                modifier = Modifier.testTag("save_package_button")
            ) {
                Text("Guardar cambios")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun toMB(text: String, unit: String): Double? {
    val value = text.toDoubleOrNull() ?: return null
    return if (unit == "GB") value * 1024.0 else value
}

private fun formatAmount(mb: Double, unit: String): String {
    if (unit != "GB") return mb.toInt().toString()
    val gb = mb / 1024.0
    return if (gb == gb.toLong().toDouble()) {
        gb.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", gb)
    }
}

private fun convertAmount(text: String, fromUnit: String, toUnit: String): String {
    val mb = toMB(text, fromUnit) ?: return text
    return formatAmount(mb, toUnit)
}
