package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.example.ui.components.UpdateCard
import com.example.ui.viewmodel.DataForecastUiState
import com.example.updates.UpdateUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    state: DataForecastUiState,
    onOpenEditLine: (lineId: Int) -> Unit,
    onOpenUssdReading: (lineId: Int) -> Unit,
    onToggleUnit: (lineId: Int) -> Unit,
    onOpenPrepaidPurchase: () -> Unit,
    onConsultPrepaidRecharge: () -> Unit,
    consultingPrepaidRecharge: Boolean,
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
            item {
                LineSettingsCard(
                    title = "Línea 1 (Mensual)",
                    subtitle = "Ciclo mensual de calendario",
                    icon = Icons.Default.NetworkCell,
                    accent = MaterialTheme.colorScheme.primary,
                    mainValue = DataFormatters.format(state.line1.plan.packageTotalMB, state.line1.displayUnit),
                    details = "Saldo: ${DataFormatters.format(state.line1.plan.remainingBalanceMB, state.line1.displayUnit)} • Día ${state.line1.forecast.daysElapsed} de ${state.line1.plan.totalCycleDays}",
                    ussdCode = state.line1.plan.ussdCode,
                    displayUnit = state.line1.displayUnit,
                    onEdit = { onOpenEditLine(1) },
                    onUssd = { onOpenUssdReading(1) },
                    onToggleUnit = { onToggleUnit(1) }
                )
            }

            item {
                LineSettingsCard(
                    title = "Línea 2 (Prepago)",
                    subtitle = "Paquete con vigencia de ${state.line2.plan.totalCycleDays} días",
                    icon = Icons.Default.SimCard,
                    accent = MaterialTheme.colorScheme.secondary,
                    mainValue = DataFormatters.format(state.line2.plan.remainingBalanceMB, state.line2.displayUnit),
                    details = buildString {
                        append("Saldo disponible • Vence: ${state.line2.expirationDateFormatted}")
                        if (state.line2.rechargeEligibleDateFormatted.isNotBlank()) {
                            append(" • Recarga: ${state.line2.rechargeEligibleDateFormatted}")
                        }
                    },
                    ussdCode = state.line2.plan.ussdCode,
                    displayUnit = state.line2.displayUnit,
                    onEdit = { onOpenEditLine(2) },
                    onUssd = { onOpenUssdReading(2) },
                    onToggleUnit = { onToggleUnit(2) },
                    extraActions = {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenPrepaidPurchase,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Comprar paquete", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = onConsultPrepaidRecharge,
                                enabled = !consultingPrepaidRecharge,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (consultingPrepaidRecharge) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Consultar recarga", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                text = "Cómo se calcula el pronóstico",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        FormulaStepItem(
                            step = "1",
                            title = "Saldo y ciclo",
                            detail = "Línea 1 sigue el mes calendario real. Línea 2 cuenta desde la compra (por defecto 35 días) y usa tus lecturas como referencia de consumo."
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FormulaStepItem(
                            step = "2",
                            title = "Ritmo promedio",
                            detail = "Consumo realizado ÷ días transcurridos = consumo promedio diario."
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FormulaStepItem(
                            step = "3",
                            title = "Cuota segura",
                            detail = "Saldo restante ÷ días restantes = límite diario para llegar al final del ciclo."
                        )
                    }
                }
            }

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
private fun LineSettingsCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    mainValue: String,
    details: String,
    ussdCode: String,
    displayUnit: String,
    onEdit: () -> Unit,
    onUssd: () -> Unit,
    onToggleUnit: () -> Unit,
    extraActions: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(10.dp)) {
                    Text("Modificar")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = mainValue,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
            Text(
                text = details,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = displayUnit == "GB", onClick = { if (displayUnit != "GB") onToggleUnit() }, label = { Text("GB") })
                FilterChip(selected = displayUnit == "MB", onClick = { if (displayUnit != "MB") onToggleUnit() }, label = { Text("MB") })
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "USSD: $ussdCode",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onUssd, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Lectura USSD")
                }
            }

            extraActions?.invoke()
        }
    }
}

@Composable
private fun FormulaStepItem(step: String, title: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
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
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
