package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.DataFormatters
import com.example.ui.components.SimSelector
import com.example.ui.components.UpdateCard
import com.example.ui.viewmodel.DataForecastUiState
import com.example.updates.UpdateUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    state: DataForecastUiState,
    onOpenConfigurePackage: (lineId: Int) -> Unit,
    onOpenUssdReading: (lineId: Int) -> Unit,
    onSelectSim: (lineId: Int, simSlotIndex: Int?) -> Unit,
    onToggleUnit: (lineId: Int) -> Unit,
    onResetToCalendar: (lineId: Int) -> Unit,
    updateState: UpdateUiState,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onOpenRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.testTag("data_settings_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configuración de Líneas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Line 1: Monthly Line Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NetworkCell,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Línea 1 (Mensual)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Ciclo mensual de facturación",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { onOpenConfigurePackage(1) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Modificar")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = DataFormatters.format(state.line1.plan.packageTotalMB, state.line1.displayUnit),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Duración: ${state.line1.plan.totalCycleDays} días • Saldo: ${DataFormatters.format(state.line1.plan.remainingBalanceMB, state.line1.displayUnit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.line1.displayUnit == "GB",
                                onClick = { if (state.line1.displayUnit != "GB") onToggleUnit(1) },
                                label = { Text("GB") }
                            )
                            FilterChip(
                                selected = state.line1.displayUnit == "MB",
                                onClick = { if (state.line1.displayUnit != "MB") onToggleUnit(1) },
                                label = { Text("MB") }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Código USSD: ${state.line1.plan.ussdCode}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { onOpenUssdReading(1) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lectura USSD")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        SimSelector(
                            selectedSlotIndex = state.line1.plan.simSlotIndex,
                            onSelect = { onSelectSim(1, it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Line 2: Prepaid 35 Days Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Línea 2 (Prepago)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Paquete con vigencia de 35 días",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { onOpenConfigurePackage(2) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Modificar")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = DataFormatters.format(state.line2.plan.packageTotalMB, state.line2.displayUnit),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Vence a los ${state.line2.plan.totalCycleDays} días (${state.line2.expirationDateFormatted}) • Saldo: ${DataFormatters.format(state.line2.plan.remainingBalanceMB, state.line2.displayUnit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.line2.rechargeEligibleDateFormatted.isNotBlank()) {
                            Text(
                                text = "Recarga disponible desde: ${state.line2.rechargeEligibleDateFormatted}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.line2.displayUnit == "GB",
                                onClick = { if (state.line2.displayUnit != "GB") onToggleUnit(2) },
                                label = { Text("GB") }
                            )
                            FilterChip(
                                selected = state.line2.displayUnit == "MB",
                                onClick = { if (state.line2.displayUnit != "MB") onToggleUnit(2) },
                                label = { Text("MB") }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Código USSD: ${state.line2.plan.ussdCode}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { onOpenUssdReading(2) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lectura USSD")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        SimSelector(
                            selectedSlotIndex = state.line2.plan.simSlotIndex,
                            onSelect = { onSelectSim(2, it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Calculation Guide Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lógica de Predicción de Datos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        FormulaStepItem(
                            step = "1",
                            title = "Paquete Prepago de 35 Días",
                            detail = "La Línea 2 cuenta 35 días a partir de la fecha de compra de tu paquete y predice si tus megas restantes alcanzarán hasta el día 35."
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        FormulaStepItem(
                            step = "2",
                            title = "Ritmo promedio de consumo",
                            detail = "Megas consumidos ÷ Días transcurridos = consumo promedio diario (MB/día)."
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        FormulaStepItem(
                            step = "3",
                            title = "Cuota diaria segura",
                            detail = "Saldo restante ÷ Días restantes = límite máximo diario para no quedarte sin conexión antes del vencimiento."
                        )
                    }
                }
            }

            // Sync with Real Calendar Button
            item {
                OutlinedButton(
                    onClick = {
                        onResetToCalendar(1)
                        onResetToCalendar(2)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sincronizar días transcurridos")
                }
            }

            // Self-update: check, download and install the signed APK from GitHub Releases.
            item {
                UpdateCard(
                    state = updateState,
                    onCheck = onCheckUpdates,
                    onDownload = onDownloadUpdate,
                    onInstall = onInstallUpdate,
                    onDismiss = onDismissUpdate,
                    onOpenRelease = onOpenRelease
                )
            }
        }
    }
}

@Composable
private fun FormulaStepItem(
    step: String,
    title: String,
    detail: String
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
