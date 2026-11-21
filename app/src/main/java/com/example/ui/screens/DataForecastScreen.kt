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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
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
import com.example.ui.components.DailyConsumptionChart
import com.example.ui.components.consumptionByCycleDay
import com.example.ui.components.DataFormatters
import com.example.ui.components.DataHeroCard
import com.example.ui.components.DataPaceCard
import com.example.ui.components.DataSimulatorCard
import com.example.ui.viewmodel.SingleLineState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataForecastScreen(
    lineState: SingleLineState,
    logs: List<DataBalanceLogEntity>,
    onConsultBalance: () -> Unit,
    consultingBalance: Boolean,
    onOpenConfigurePackage: () -> Unit,
    onOpenAdjustDays: () -> Unit,
    onToggleDisplayUnit: () -> Unit,
    onSimulateDailyMB: (Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    val forecast = lineState.forecast
    val plan = lineState.plan
    val displayUnit = lineState.displayUnit

    Scaffold(
        modifier = modifier.testTag("data_forecast_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Línea 1 • Mensual",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Paquete ${DataFormatters.format(plan.packageTotalMB, displayUnit)} • Ciclo de ${plan.totalCycleDays} días",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onConsultBalance,
                        enabled = !consultingBalance,
                        modifier = Modifier.testTag("action_top_ussd_button")
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
                        modifier = Modifier.testTag("toggle_unit_button")
                    ) {
                        Text(
                            text = displayUnit,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onOpenAdjustDays,
                        modifier = Modifier.testTag("adjust_days_icon_button")
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
            // Cycle Progress Pill
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
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Día ${forecast.daysElapsed} de ${forecast.totalCycleDays}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (forecast.daysRemaining == 1) {
                                        "1 día restante para renovación"
                                    } else {
                                        "${forecast.daysRemaining} días restantes para renovación"
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

            // Big Hero Verdict Card
            item {
                DataHeroCard(
                    forecast = forecast,
                    displayUnit = displayUnit,
                    onConsultBalance = onConsultBalance,
                    consultingBalance = consultingBalance
                )
            }

            // Pace & Burn Rate vs Safe Allowance
            item {
                DataPaceCard(
                    forecast = forecast,
                    displayUnit = displayUnit
                )
            }

            // Daily consumption over the cycle (MB)
            item {
                DailyConsumptionChart(
                    consumptionByDay = consumptionByCycleDay(
                        logs = logs,
                        planId = 1,
                        totalCycleDays = forecast.totalCycleDays
                    ),
                    currentCycleDay = forecast.daysElapsed,
                    modifier = Modifier.testTag("daily_consumption_chart_line1")
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
                                text = "Desglose del Paquete",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        DataRow(
                            label = "Paquete contratado inicial",
                            sublabel = "Capacidad total para ${forecast.totalCycleDays} días",
                            value = DataFormatters.format(forecast.packageTotalMB, displayUnit)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        DataRow(
                            label = "Consumo realizado hasta hoy",
                            sublabel = "Consumido en los primeros ${forecast.daysElapsed} días",
                            value = DataFormatters.format(forecast.consumedMB, displayUnit)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        DataRow(
                            label = "Saldo restante disponible",
                            sublabel = "Datos que te quedan para navegar",
                            value = DataFormatters.format(forecast.remainingBalanceMB, displayUnit)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        DataRow(
                            label = "Gasto proyectado días restantes",
                            sublabel = "${DataFormatters.format(forecast.dailyBurnRateMB)}/día × ${forecast.daysRemaining} días",
                            value = DataFormatters.format(forecast.dailyBurnRateMB * forecast.daysRemaining, displayUnit)
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
                                    text = if (forecast.willLastWholeMonth) "Sobrante estimado al fin" else "Déficit proyectado",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (forecast.willLastWholeMonth) "¡El paquete cubre el mes!" else "Se agotará antes de renovar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = if (forecast.willLastWholeMonth) {
                                    "+ ${DataFormatters.format(forecast.projectedDeficitOrSurplusMB, displayUnit)}"
                                } else {
                                    "- ${DataFormatters.format(kotlin.math.abs(forecast.projectedDeficitOrSurplusMB), displayUnit)}"
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
                    displayUnit = displayUnit,
                    onSimulateDailyMB = onSimulateDailyMB
                )
            }

            // Quick Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onConsultBalance,
                        enabled = !consultingBalance,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("action_ussd_reading")
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
                                .testTag("action_change_package")
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
                                .testTag("action_adjust_days")
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
private fun DataRow(
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
