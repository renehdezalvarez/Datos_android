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

