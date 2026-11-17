package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.data.model.DataBalanceLogEntity
import kotlin.math.max

/**
 * Consumption per cycle day, derived from the balance logs of one line.
 *
 * The difference between two consecutive readings is consumed data, and it is
 * attributed to the cycle day of the later reading. This way the last reading
 * of a day acts as the starting balance for the next day.
 */
fun consumptionByCycleDay(
    logs: List<DataBalanceLogEntity>,
    planId: Int,
    totalCycleDays: Int
): List<Double> {
    val days = max(1, totalCycleDays)
    val result = DoubleArray(days) { 0.0 }
    var previous: DataBalanceLogEntity? = null
    logs.filter { it.planId == planId }
        .sortedBy { it.timestamp }
        .forEach { log ->
            val prev = previous
            if (prev != null) {
                val consumed = prev.remainingMB - log.remainingMB
                if (consumed > 0.0) {
                    result[log.cycleDay.coerceIn(1, days) - 1] += consumed
                }
            }
            previous = log
        }
    return result.toList()
}

/** Bar chart of data consumed on each day of the cycle (always in MB). */
@Composable
fun DailyConsumptionChart(
    consumptionByDay: List<Double>,
    currentCycleDay: Int,
    modifier: Modifier = Modifier,
    title: String = "Consumo por día del ciclo"
) {
    val hasData = consumptionByDay.any { it > 0.0 }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(
                text = "MB consumidos según las lecturas registradas. La última lectura de un día es el inicio del siguiente.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasData) {
                Text(
                    text = "Aún no hay suficientes lecturas para calcular el consumo diario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ConsumptionBars(
                    consumptionByDay = consumptionByDay,
                    currentCycleDay = currentCycleDay
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "La barra en color secundario marca el día actual del ciclo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConsumptionBars(
    consumptionByDay: List<Double>,
    currentCycleDay: Int
) {
    val textMeasurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val currentDayColor = MaterialTheme.colorScheme.secondary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val maxValue = (consumptionByDay.maxOrNull() ?: 0.0).coerceAtLeast(1.0)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Gráfico de barras del consumo de datos por día del ciclo en MB"
            }
    ) {
        val dayCount = consumptionByDay.size.coerceAtLeast(1)
        val topZone = 20.dp.toPx()
        val labelZone = 22.dp.toPx()
        val chartHeight = size.height - topZone - labelZone
        val baselineY = topZone + chartHeight
        val slotWidth = size.width / dayCount
        val barWidth = (slotWidth * 0.7f).coerceAtMost(20.dp.toPx()).coerceAtLeast(2.dp.toPx())

        val maxLabel = textMeasurer.measure(DataFormatters.format(maxValue, "MB"), labelStyle)
        drawText(maxLabel, topLeft = Offset(0f, 0f), color = axisColor)

        drawLine(
            color = axisColor.copy(alpha = 0.25f),
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.dp.toPx()
        )

        consumptionByDay.forEachIndexed { index, value ->
            val day = index + 1
            val x = slotWidth * index + (slotWidth - barWidth) / 2
            val isCurrentDay = day == currentCycleDay

            if (value > 0.0) {
                val barHeight = ((value / maxValue) * chartHeight).toFloat().coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    color = if (isCurrentDay) currentDayColor else barColor,
                    topLeft = Offset(x, baselineY - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            } else if (isCurrentDay) {
                // Small marker so the current day is still identifiable.
                drawRoundRect(
                    color = currentDayColor.copy(alpha = 0.4f),
                    topLeft = Offset(x, baselineY - 3.dp.toPx()),
                    size = Size(barWidth, 3.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            val showLabel = dayCount <= 12 || day == 1 || day == dayCount ||
                day % 5 == 0 || isCurrentDay
            if (showLabel) {
                val label = textMeasurer.measure(day.toString(), labelStyle)
                drawText(
                    label,
                    topLeft = Offset(x + (barWidth - label.size.width) / 2, baselineY + 4.dp.toPx()),
                    color = axisColor
                )
            }
        }
    }
}
