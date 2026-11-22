package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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

/**
 * Registers a new prepaid cycle. The app does not ask how many GB were bought:
 * the carrier reports the real balance, which is recorded afterwards with a
 * USSD or manual reading.
 */
@Composable
fun NewPrepaidPurchaseDialog(
    currentRemainingMB: Double,
    displayUnit: String,
    previousBalanceLost: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (totalDays: Int, purchaseDateMillis: Long?) -> Unit
) {
    var daysText by remember { mutableStateOf("35") }
    var dateText by remember { mutableStateOf("") }

    val dateFormat = remember {
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).apply {
            isLenient = false
        }
    }

    // null = empty (today); parsed date or error message.
    val parsedDate: Pair<Long?, String?> = remember(dateText) {
        when {
            dateText.isBlank() -> null to null
            else -> {
                val parsed = try {
                    dateFormat.parse(dateText)
                } catch (_: Exception) {
                    null
                }
                when {
                    parsed == null -> null to "Fecha no válida (usa dd/MM/aaaa)"
                    else -> {
                        val cal = java.util.Calendar.getInstance().apply {
                            time = parsed
                            set(java.util.Calendar.HOUR_OF_DAY, 12)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        val today = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, 23)
                            set(java.util.Calendar.MINUTE, 59)
                            set(java.util.Calendar.SECOND, 59)
                        }
                        when {
                            cal.timeInMillis > today.timeInMillis -> null to "La fecha no puede ser futura"
                            else -> cal.timeInMillis to null
                        }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nueva Compra Prepago", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = if (previousBalanceLost) {
                        "El paquete anterior venció y su saldo se perdió. Registra la compra para reiniciar el ciclo y después consulta el saldo para anotar tus megas disponibles."
                    } else {
                        "Registra que compraste un nuevo paquete para reiniciar el contador de 35 días. Después consulta el saldo: la app no suma megas por su cuenta, el operador es la fuente del saldo real."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text("Días de vigencia") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Fecha de compra (opcional)") },
                    placeholder = { Text("Hoy (dd/MM/aaaa si fue otro día)") },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                    isError = parsedDate.second != null,
                    supportingText = {
                        if (parsedDate.second != null) {
                            Text(parsedDate.second ?: "", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Déjalo vacío si la compra es de hoy.")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_purchase_date_input")
                )

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (previousBalanceLost) {
                            "Saldo anterior perdido al vencer: ${DataFormatters.format(currentRemainingMB, displayUnit)}"
                        } else {
                            "Saldo registrado hasta ahora: ${DataFormatters.format(currentRemainingMB, displayUnit)} (se actualizará al consultar)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = daysText.toIntOrNull() ?: 35
                    onConfirm(days.coerceAtLeast(1), parsedDate.first)
                    onDismiss()
                },
                enabled = (daysText.toIntOrNull() ?: 0) > 0 && parsedDate.second == null,
                modifier = Modifier.testTag("confirm_new_purchase_button")
            ) {
                Text("Registrar Compra")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
