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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DataBalanceLogEntity
import com.example.ui.components.DataFormatters
import com.example.ui.viewmodel.DataForecastUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private data class DailyConsumptionEntry(
    val dayKey: Long,
    val planId: Int,
    val dateLabel: String,
    val consumedMB: Double,
    val addedMB: Double,
    val endOfDayRemainingMB: Double,
    val readingsCount: Int
)

private fun dayStartMillis(timestamp: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun computeDailyConsumption(
    logs: List<DataBalanceLogEntity>,
    selectedLineId: Int
): List<DailyConsumptionEntry> {
    val relevant = if (selectedLineId == 0) logs else logs.filter { it.planId == selectedLineId }
    if (relevant.isEmpty()) return emptyList()
    val byPlanAsc = relevant.groupBy { it.planId }.mapValues { (_, v) -> v.sortedBy { it.timestamp } }
    val dayFmt = SimpleDateFormat("EEE dd MMM yyyy", Locale("es", "ES"))
    val accum = linkedMapOf<String, DailyConsumptionEntry>()

    byPlanAsc.forEach { (planId, planLogs) ->
        var prevRemaining: Double? = null
        planLogs.forEach { log ->
            val start = dayStartMillis(log.timestamp)
            val key = "$planId-$start"
            val prev = accum[key]
            val deltaPrev = prevRemaining
            var consumed = prev?.consumedMB ?: 0.0
            var added = prev?.addedMB ?: 0.0
            if (deltaPrev != null) {
                val delta = deltaPrev - log.remainingMB
                if (delta > 0.0) consumed += delta else if (delta < 0.0) added += -delta
            }
            accum[key] = DailyConsumptionEntry(
                dayKey = start,
                planId = planId,
                dateLabel = dayFmt.format(Date(start)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "ES")) else it.toString() },
                consumedMB = consumed,
                addedMB = added,
                endOfDayRemainingMB = log.remainingMB,
                readingsCount = (prev?.readingsCount ?: 0) + 1
            )
            prevRemaining = log.remainingMB
        }
    }
    return accum.values.sortedWith(compareByDescending<DailyConsumptionEntry> { it.dayKey }.thenBy { it.planId })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataLogsScreen(
    state: DataForecastUiState,
    onOpenAddLog: (lineId: Int) -> Unit,
    onDeleteLog: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale("es", "ES"))
    var selectedFilterLineId by remember { mutableIntStateOf(0) } // 0 = all, 1 = Line 1, 2 = Line 2
    var viewMode by remember { mutableIntStateOf(0) } // 0 = readings, 1 = daily consumption

    val filteredLogs = remember(state.logs, selectedFilterLineId) {
        if (selectedFilterLineId == 0) {
            state.logs
        } else {
            state.logs.filter { it.planId == selectedFilterLineId }
        }
    }
    val dailyEntries = remember(state.logs, selectedFilterLineId) {
        computeDailyConsumption(state.logs, selectedFilterLineId)
    }

    Scaffold(
        modifier = modifier.testTag("data_logs_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Historial de Lecturas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (viewMode == 0) "${filteredLogs.size} lecturas registradas" else "${dailyEntries.size} días con consumo calculado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (viewMode == 0) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenAddLog(if (selectedFilterLineId != 0) selectedFilterLineId else 1) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Registrar Saldo") },
                    modifier = Modifier.testTag("fab_add_log")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SecondaryTabRow(selectedTabIndex = viewMode) {
                Tab(
                    selected = viewMode == 0,
                    onClick = { viewMode = 0 },
                    text = { Text("Lecturas") }
                )
                Tab(
                    selected = viewMode == 1,
                    onClick = { viewMode = 1 },
                    text = { Text("Consumo diario") }
                )
            }

            // Line Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilterLineId == 0,
                        onClick = { selectedFilterLineId = 0 },
                        label = { Text("Todas (${state.logs.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilterLineId == 1,
                        onClick = { selectedFilterLineId = 1 },
                        label = { Text("Línea 1: Mensual (${state.logs.count { it.planId == 1 }})") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilterLineId == 2,
                        onClick = { selectedFilterLineId = 2 },
                        label = { Text("Línea 2: Prepago 35d (${state.logs.count { it.planId == 2 }})") }
                    )
                }
            }

            if (viewMode == 0) {
                if (filteredLogs.isEmpty()) {
                    EmptyLogsPlaceholder(
                        text = "Sin lecturas guardadas",
                        subtitle = "Registra revisiones periódicas del saldo de tu operador para comprobar cómo desciende tu saldo."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { log ->
                            val unit = if (log.planId == 2) state.line2.displayUnit else state.line1.displayUnit
                            LogItemCard(
                                log = log,
                                displayUnit = unit,
                                dateFormat = dateFormat,
                                onDelete = { onDeleteLog(log.id) }
                            )
                        }
                    }
                }
            } else {
                if (dailyEntries.isEmpty()) {
                    EmptyLogsPlaceholder(
                        text = "Sin consumo diario calculado",
                        subtitle = "Se necesitan al menos dos lecturas registradas para calcular el consumo entre lecturas de cada día."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(dailyEntries, key = { "${it.planId}-${it.dayKey}" }) { entry ->
                            val unit = if (entry.planId == 2) state.line2.displayUnit else state.line1.displayUnit
                            DailyConsumptionCard(entry = entry, displayUnit = unit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLogsPlaceholder(text: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DailyConsumptionCard(
    entry: DailyConsumptionEntry,
    displayUnit: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (entry.planId == 2) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (entry.planId == 2) Icons.Default.SimCard else Icons.Default.DataUsage,
                        contentDescription = null,
                        tint = if (entry.planId == 2) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (entry.planId == 2) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (entry.planId == 2) "Línea 2 (Prepago 35d)" else "Línea 1 (Mensual)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (entry.planId == 2) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.dateLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                val consumedText = if (entry.consumedMB > 0.0) "Consumido: ${DataFormatters.format(entry.consumedMB, displayUnit)}" else "Sin consumo registrado"
                val addedText = if (entry.addedMB > 0.0) " · Recarga/Ajuste: +${DataFormatters.format(entry.addedMB, displayUnit)}" else ""
                Text(
                    text = consumedText + addedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Saldo al cierre: ${DataFormatters.format(entry.endOfDayRemainingMB, displayUnit)} · ${entry.readingsCount} lectura(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogItemCard(
    log: DataBalanceLogEntity,
    displayUnit: String,
    dateFormat: SimpleDateFormat,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_item_${log.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (log.planId == 2) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (log.planId == 2) Icons.Default.SimCard else Icons.Default.DataUsage,
                        contentDescription = null,
                        tint = if (log.planId == 2) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (log.planId == 2) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (log.planId == 2) "Línea 2 (Prepago 35d)" else "Línea 1 (Mensual)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (log.planId == 2) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Día ${log.cycleDay}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = DataFormatters.format(log.remainingMB, displayUnit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (log.note.isNotBlank()) "${log.note} • ${dateFormat.format(Date(log.timestamp))}" else dateFormat.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Eliminar lectura",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
