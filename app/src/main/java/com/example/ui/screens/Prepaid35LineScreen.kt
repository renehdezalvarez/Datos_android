package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.model.DataBalanceLogEntity
import com.example.domain.PrepaidRecharge
import com.example.ui.components.DailyConsumptionChart
import com.example.ui.components.consumptionByCycleDay
import com.example.ui.components.DataFormatters
import com.example.ui.components.DataHeroCard
import com.example.ui.components.DataPaceCard
import com.example.ui.components.DataSimulatorCard
import com.example.ui.viewmodel.SingleLineState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Prepaid35LineScreen(
    lineState: SingleLineState,
    logs: List<DataBalanceLogEntity>,
    onConsultBalance: () -> Unit,
    consultingBalance: Boolean,
    onConsultRechargeDate: () -> Unit,
    consultingRechargeDate: Boolean,
    onOpenConfigurePackage: () -> Unit,
    onOpenAdjustDays: () -> Unit,
    onOpenNewPurchaseDialog: () -> Unit,
    onToggleDisplayUnit: () -> Unit,
    onSimulateDailyMB: (Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    val forecast = lineState.forecast
    val plan = lineState.plan
    val unit = lineState.displayUnit

    Scaffold(
        modifier = modifier.testTag("prepaid_35_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Línea 2 • Prepago",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Paquete de ${DataFormatters.format(plan.packageTotalMB, unit)} • Vence a los 35 días (${lineState.expirationDateFormatted})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onConsultBalance,
                        enabled = !consultingBalance,
                        modifier = Modifier.testTag("prepaid_top_ussd_button")
                    ) {
                        if (consultingBalance) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Dialpad,
                                contentDescription = "Consultar saldo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onToggleDisplayUnit,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("prepaid_toggle_unit")
                    ) {
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onOpenAdjustDays,
                        modifier = Modifier.testTag("prepaid_adjust_days_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Ajustar días del ciclo"
                        )
                    }
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
            contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Expiration warning: 5 days or less left on the package
            val daysLeft = forecast.daysRemaining
            if (daysLeft <= 5) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prepaid_expiration_warning"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        daysLeft <= 0 -> "El paquete ha vencido"
                                        else -> "¡Quedan $daysLeft ${if (daysLeft == 1) "día" else "días"} del paquete!"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = when {
                                        daysLeft <= 0 -> "El saldo restante se perdió. Compra un nuevo paquete para recuperar tus datos."
                                        daysLeft == 1 -> "Último día: al terminar hoy el saldo restante se pierde. Compra para conservarlo."
                                        else -> "El día ${forecast.totalCycleDays} es la fecha límite: el saldo que quede al vencer se pierde. Compra antes para conservarlo."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = onOpenNewPurchaseDialog,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("prepaid_warning_buy_button")
                            ) {
                                Text("Comprar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Recharge date reported by the carrier for this prepaid line only.
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prepaid_recharge_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recarga de saldo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (lineState.rechargeEligibleDateFormatted.isNotBlank()) {
                                Text(
                                    text = "Puedes recargar desde el ${lineState.rechargeEligibleDateFormatted}.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Fecha informada por el operador: ${lineState.rechargeCarrierDateFormatted}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Consulta ${PrepaidRecharge.USSD_CODE} una vez por ciclo para saber cuándo puedes recargar.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onConsultRechargeDate,
                            enabled = !consultingRechargeDate,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("prepaid_recharge_consult_button")
                        ) {
                            if (consultingRechargeDate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Consultar", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // New 35-day Purchase Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "¿Compraste un nuevo paquete?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Registra una nueva compra para reiniciar el contador de 35 días",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onOpenNewPurchaseDialog,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("button_new_prepaid_purchase")
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nueva Compra", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // 35-Day Cycle Progress Pill
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Día ${forecast.daysElapsed} de ${forecast.totalCycleDays} (Ciclo 35 días)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        forecast.daysRemaining <= 0 -> "Venció: ${lineState.expirationDateFormatted}"
                                        forecast.daysRemaining == 1 -> "1 día restante • Vence: ${lineState.expirationDateFormatted}"
                                        else -> "${forecast.daysRemaining} días restantes • Vence: ${lineState.expirationDateFormatted}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenAdjustDays,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Cambiar", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Hero Verdict Card for Line 2
            item {
                DataHeroCard(
                    forecast = forecast,
                    displayUnit = unit,
                    onConsultBalance = onConsultBalance,
                    consultingBalance = consultingBalance
                )
            }

            // Pace & Burn Rate vs Safe Allowance for 35 days
            item {
                DataPaceCard(
                    forecast = forecast,
                    displayUnit = unit
                )
            }

            // Daily consumption over the cycle (MB)
            item {
                DailyConsumptionChart(
                    consumptionByDay = consumptionByCycleDay(
                        logs = logs,
                        planId = 2,
                        totalCycleDays = forecast.totalCycleDays
                    ),
                    currentCycleDay = forecast.daysElapsed,
                    modifier = Modifier.testTag("daily_consumption_chart_line2")
                )
            }

            // Detailed Data Breakdown Card
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
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Desglose Paquete Prepago (35 Días)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        BreakdownItem(
                            label = "Paquete contratado",
                            sublabel = "Válido por ${forecast.totalCycleDays} días desde la compra",
                            value = DataFormatters.format(forecast.packageTotalMB, unit)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        BreakdownItem(
                            label = "Consumo realizado hasta hoy",
                            sublabel = "Gastado en los primeros ${forecast.daysElapsed} días",
                            value = DataFormatters.format(forecast.consumedMB, unit)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        BreakdownItem(
                            label = "Saldo restante disponible",
                            sublabel = "Megas disponibles en la línea",
                            value = DataFormatters.format(forecast.remainingBalanceMB, unit)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        BreakdownItem(
                            label = "Gasto proyectado al día 35",
                            sublabel = "${DataFormatters.format(forecast.dailyBurnRateMB)}/día × ${forecast.daysRemaining.coerceAtLeast(0)} días restantes",
                            value = DataFormatters.format(forecast.dailyBurnRateMB * forecast.daysRemaining.coerceAtLeast(0), unit)
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (forecast.willLastWholeMonth) "Sobrante estimado al vencimiento" else "Déficit antes del día 35",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (forecast.willLastWholeMonth) "¡El paquete cubre los 35 días!" else "Se agotará antes de que venza",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = if (forecast.willLastWholeMonth) {
                                    "+ ${DataFormatters.format(forecast.projectedDeficitOrSurplusMB, unit)}"
                                } else {
                                    "- ${DataFormatters.format(kotlin.math.abs(forecast.projectedDeficitOrSurplusMB), unit)}"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (forecast.willLastWholeMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // What-If Simulator Card
            item {
                DataSimulatorCard(
                    forecast = forecast,
                    displayUnit = unit,
                    onSimulateDailyMB = onSimulateDailyMB
                )
            }

            // Quick Actions Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onConsultBalance,
                        enabled = !consultingBalance,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prepaid_action_ussd_reading")
                    ) {
                        if (consultingBalance) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (consultingBalance) {
                                "Consultando saldo…"
                            } else {
                                "Consultar Saldo por USSD (${plan.ussdCode})"
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onOpenConfigurePackage,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("prepaid_action_change_package")
                        ) {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cambiar Paquete")
                        }

                        OutlinedButton(
                            onClick = onOpenAdjustDays,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("prepaid_action_adjust_days")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ajustar Días")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownItem(
    label: String,
    sublabel: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
