package com.safemode.hbconnect.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safemode.hbconnect.data.remote.models.Currency
import com.safemode.hbconnect.data.remote.models.TotalsByOrganizer
import com.safemode.hbconnect.data.remote.models.ValueOverTime
import com.safemode.hbconnect.ui.common.formatMoney
import java.time.Instant
import java.time.OffsetDateTime

/** True when there is anything worth drawing (a non-zero total or at least one data point). */
fun ValueOverTime.hasData(): Boolean = valueAtEnd != 0.0 || valueAtStart != 0.0 || entries.isNotEmpty()

private fun parseEpoch(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(value).toEpochMilli() }
        .getOrNull()
}

/** A (x, cumulativeValue) point used by the trend line, x already in epoch millis. */
private data class TrendPoint(val x: Long, val y: Double)

/** Builds the running-total series: starts at [ValueOverTime.valueAtStart] and adds each entry in date order. */
private fun ValueOverTime.cumulativeSeries(): List<TrendPoint> {
    val startX = parseEpoch(start)
    val endX = parseEpoch(end)
    val dated = entries
        .mapNotNull { e -> parseEpoch(e.date)?.let { it to e.value } }
        .sortedBy { it.first }

    val points = ArrayList<TrendPoint>(dated.size + 2)
    var running = valueAtStart
    if (startX != null) points += TrendPoint(startX, running)
    for ((x, v) in dated) {
        running += v
        points += TrendPoint(x, running)
    }
    // Anchor the right edge at the window end so the line spans the full width.
    if (endX != null && (points.isEmpty() || endX > points.last().x)) {
        points += TrendPoint(endX, running)
    }
    return points
}

/** Line chart of cumulative inventory value over the trailing window. */
@Composable
fun ValueTrendCard(data: ValueOverTime, currency: Currency?, modifier: Modifier = Modifier) {
    val delta = data.valueAtEnd - data.valueAtStart
    val lineColor = MaterialTheme.colorScheme.primary
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Inventory value",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Past 12 months", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    formatMoney(data.valueAtEnd, currency),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (delta != 0.0) {
                    val up = delta > 0
                    Text(
                        (if (up) "+" else "−") + formatMoney(kotlin.math.abs(delta), currency),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (up) lineColor else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            val points = data.cumulativeSeries()
            val minY = 0.0
            val maxY = (points.maxOfOrNull { it.y } ?: 0.0).coerceAtLeast(1.0)
            val minX = points.firstOrNull()?.x ?: 0L
            val maxX = points.lastOrNull()?.x ?: 0L
            val spanX = (maxX - minX).coerceAtLeast(1L)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                if (points.isEmpty()) return@Canvas
                fun px(p: TrendPoint): Pair<Float, Float> {
                    val x = ((p.x - minX).toDouble() / spanX.toDouble()).toFloat() * size.width
                    val yNorm = ((p.y - minY) / (maxY - minY)).toFloat().coerceIn(0f, 1f)
                    val y = size.height - yNorm * size.height
                    return x to y
                }

                val line = Path()
                val fill = Path()
                points.forEachIndexed { i, p ->
                    val (x, y) = px(p)
                    if (i == 0) {
                        line.moveTo(x, y)
                        fill.moveTo(x, size.height)
                        fill.lineTo(x, y)
                    } else {
                        line.lineTo(x, y)
                        fill.lineTo(x, y)
                    }
                }
                val lastX = px(points.last()).first
                fill.lineTo(lastX, size.height)
                fill.close()

                drawPath(
                    fill,
                    brush = Brush.verticalGradient(
                        listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                    ),
                )
                drawPath(line, color = lineColor, style = Stroke(width = 6f))
            }
        }
    }
}

/** Horizontal-bar breakdown of value by organizer (location or tag). */
@Composable
fun BreakdownCard(
    title: String,
    data: List<TotalsByOrganizer>,
    currency: Currency?,
    modifier: Modifier = Modifier,
    maxRows: Int = 6,
) {
    val rows = data.filter { it.total > 0.0 }.sortedByDescending { it.total }.take(maxRows)
    if (rows.isEmpty()) return
    val top = rows.first().total.coerceAtLeast(0.01)
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rows.forEach { row ->
                BreakdownBar(
                    name = row.name?.ifBlank { null } ?: "—",
                    value = formatMoney(row.total, currency),
                    fraction = (row.total / top).toFloat().coerceIn(0f, 1f),
                )
            }
        }
    }
}

@Composable
private fun BreakdownBar(name: String, value: String, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
