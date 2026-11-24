package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import com.example.data.model.DataBalanceLogEntity
import com.example.domain.PrepaidRecharge
import com.example.ui.components.DailyConsumptionChart
import com.example.ui.components.DataHeroCard
import com.example.ui.components.DataPaceCard
import com.example.ui.components.DataSimulatorCard
import com.example.ui.components.consumptionByCycleDay
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
    onOpenPurchase: () -> Unit,
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
                            text = "Vence a los ${plan.totalCycleDays} días (${lineState.expirationDateFormatted})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                onClick = onOpenPurchase,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("prepaid_warning_buy_button")
                            ) {
                                Text("Comprar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

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

            if (daysLeft > 5) {
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
                                onClick = onOpenPurchase,
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
            }

            item {
                DataHeroCard(
                    forecast = forecast,
                    displayUnit = unit,
                    showPackageTotal = false,
                    onConsultBalance = onConsultBalance,
                    consultingBalance = consultingBalance
                )
            }

            item {
                DataPaceCard(
                    forecast = forecast,
                    displayUnit = unit
                )
            }

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

            item {
                DataSimulatorCard(
                    forecast = forecast,
                    displayUnit = unit,
                    onSimulateDailyMB = onSimulateDailyMB
                )
            }
        }
    }
}
