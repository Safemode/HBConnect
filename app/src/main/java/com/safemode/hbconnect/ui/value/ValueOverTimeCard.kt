package com.safemode.hbconnect.ui.value

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safemode.hbconnect.data.remote.models.Currency
import com.safemode.hbconnect.data.remote.models.ValueOverTime
import com.safemode.hbconnect.ui.common.formatMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A cumulative "total value over time" line chart for the group, backed by
 * GET /groups/statistics/purchase-price. The server returns the value of items created before the
 * window ([ValueOverTime.valueAtStart]) plus each priced item created within it; summing those in
 * date order traces how the group's total value climbed across the window.
 */
@Composable
fun ValueOverTimeCard(
    data: ValueOverTime,
    currency: Currency?,
    selectedRange: ValueRange,
    onRangeChange: (ValueRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val points = remember(data) { cumulativePoints(data) }
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Value over time",
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor,
                )
                Text(
                    formatMoney(points.lastOrNull()?.value ?: data.valueAtEnd, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (points.size < 2) {
                Text(
                    "Not enough priced items in this range to chart.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor,
                )
            } else {
                val minValue = points.minOf { it.value }
                val maxValue = points.maxOf { it.value }
                val minDay = points.first().day
                val maxDay = points.last().day
                val spanDays = (maxDay - minDay).coerceAtLeast(1L).toFloat()
                val spanValue = (maxValue - minValue).toFloat().coerceAtLeast(0.01f)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                ) {
                    val inset = 6f
                    val usableH = size.height - inset * 2
                    fun px(day: Long) = ((day - minDay) / spanDays) * size.width
                    fun py(value: Double) =
                        inset + (1f - ((value - minValue).toFloat() / spanValue)) * usableH

                    val line = Path()
                    val area = Path()
                    points.forEachIndexed { index, p ->
                        val x = px(p.day)
                        val y = py(p.value)
                        if (index == 0) {
                            line.moveTo(x, y)
                            area.moveTo(x, size.height)
                            area.lineTo(x, y)
                        } else {
                            line.lineTo(x, y)
                            area.lineTo(x, y)
                        }
                    }
                    area.lineTo(px(points.last().day), size.height)
                    area.close()

                    drawPath(area, color = fillColor)
                    drawPath(
                        path = line,
                        color = lineColor,
                        style = Stroke(width = 3f),
                    )
                    // End-point marker so a short or flat series still reads as a chart.
                    val last = points.last()
                    drawCircle(
                        color = lineColor,
                        radius = 5f,
                        center = Offset(px(last.day), py(last.value)),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatDay(minDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = labelColor,
                    )
                    Text(
                        formatDay(maxDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = labelColor,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValueRange.entries.forEach { range ->
                    FilterChip(
                        selected = range == selectedRange,
                        onClick = { onRangeChange(range) },
                        label = { Text(range.label) },
                    )
                }
            }
        }
    }
}

/** A single cumulative point: a day (epoch day) and the running total value at that day. */
private data class ValuePoint(val day: Long, val value: Double)

/**
 * Builds the cumulative series from [data]: start at [ValueOverTime.valueAtStart], then add each
 * priced item's value in date order. Entries without a parseable date are skipped.
 */
private fun cumulativePoints(data: ValueOverTime): List<ValuePoint> {
    val dated = data.entries
        .mapNotNull { entry -> parseEpochDay(entry.date)?.let { it to entry.value } }
        .sortedBy { it.first }

    val startDay = parseEpochDay(data.start) ?: dated.firstOrNull()?.first ?: return emptyList()
    val points = ArrayList<ValuePoint>()
    var running = data.valueAtStart
    points.add(ValuePoint(startDay, running))
    dated.forEach { (day, value) ->
        running += value
        points.add(ValuePoint(day, running))
    }
    val endDay = parseEpochDay(data.end)
    if (endDay != null && endDay > points.last().day) {
        points.add(ValuePoint(endDay, running))
    }
    return points
}

/** Parses the leading yyyy-MM-dd of an RFC 3339 timestamp to an epoch day. */
private fun parseEpochDay(raw: String?): Long? {
    val text = raw?.take(10)?.ifBlank { null } ?: return null
    return runCatching { LocalDate.parse(text).toEpochDay() }.getOrNull()
}

private val axisFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

private fun formatDay(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(axisFormatter)
