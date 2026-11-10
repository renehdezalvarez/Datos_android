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
fun NewPrepaidPurchaseDialog(
    currentPackageTotalMB: Double,
    currentRemainingMB: Double,
    displayUnit: String,
    previousBalanceLost: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (packageMB: Double, totalDays: Int, purchaseDateMillis: Long?) -> Unit
) {
    var sizeGBText by remember {
        val gb = currentPackageTotalMB / 1024.0
        mutableStateOf(if (gb == gb.toLong().toDouble()) gb.toLong().toString() else String.format(java.util.Locale.US, "%.1f", gb))
    }
    var daysText by remember { mutableStateOf("35") }
    var dateText by remember { mutableStateOf("") }
    val presets = listOf(2.0, 4.0, 6.0, 8.0, 10.0, 15.0, 20.0, 30.0)

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

    val purchaseGB = sizeGBText.toDoubleOrNull() ?: 0.0
    // After the deadline (day 35) the leftover balance is forfeited, so the
    // new package starts from zero instead of adding on top of it.
    val resultingMB = if (previousBalanceLost) purchaseGB * 1024.0 else currentRemainingMB + purchaseGB * 1024.0

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
                        "El paquete anterior venció y su saldo restante se perdió. Registra la compra del nuevo paquete, que arranca desde cero:"
                    } else {
                        "Registra la compra de un nuevo paquete. La capacidad comprada se SUMA al saldo restante actual y el ciclo se reinicia desde la fecha de compra:"
                    },
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

                if (purchaseGB > 0.0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (previousBalanceLost) {
                                Text(
                                    text = "Saldo anterior: ${DataFormatters.format(currentRemainingMB, displayUnit)} (perdido al vencer)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                Text(
                                    text = "Saldo actual: ${DataFormatters.format(currentRemainingMB, displayUnit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "+ Paquete comprado: ${DataFormatters.format(purchaseGB * 1024.0, displayUnit)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "= Saldo resultante: ${DataFormatters.format(resultingMB, displayUnit)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val gb = sizeGBText.toDoubleOrNull() ?: 6.0
                    val days = daysText.toIntOrNull() ?: 35
                    onConfirm(gb * 1024.0, days, parsedDate.first)
                    onDismiss()
                },
                enabled = purchaseGB > 0.0 && parsedDate.second == null,
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

