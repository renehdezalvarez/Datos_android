package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DataBalanceLogEntity
import com.example.ui.components.DailyConsumptionChart
import com.example.ui.components.DataFormatters
import com.example.ui.components.DataHeroCard
import com.example.ui.components.DataPaceCard
import com.example.ui.components.DataSimulatorCard
import com.example.ui.components.consumptionByCycleDay
import com.example.ui.viewmodel.SingleLineState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataForecastScreen(
    lineState: SingleLineState,
    logs: List<DataBalanceLogEntity>,
    onConsultBalance: () -> Unit,
    consultingBalance: Boolean,
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
            item {
                DataHeroCard(
                    forecast = forecast,
                    displayUnit = displayUnit,
                    onConsultBalance = onConsultBalance,
                    consultingBalance = consultingBalance
                )
            }

            item {
                DataPaceCard(
                    forecast = forecast,
                    displayUnit = displayUnit
                )
            }

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

            item {
                DataSimulatorCard(
                    forecast = forecast,
                    displayUnit = displayUnit,
                    onSimulateDailyMB = onSimulateDailyMB
                )
            }
        }
    }
}
