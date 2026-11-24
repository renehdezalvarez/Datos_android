package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

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
