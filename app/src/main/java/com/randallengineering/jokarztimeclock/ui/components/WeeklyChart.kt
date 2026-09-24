package com.randallengineering.jokarztimeclock.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import kotlin.math.max

@Composable
fun WeeklyChart(
    state: TimeclockState,
    modifier: Modifier = Modifier
) {
    val startOfWeek = PayrollEngine.getStartOfWeekDate()
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val textMeasurer = rememberTextMeasurer()

    // Read the roles here: the Canvas draw lambda is not composable.
    val scheme = MaterialTheme.colorScheme
    val baseBarColor = scheme.primary
    val emptyBarColor = scheme.surfaceContainerHighest
    val overtimeColor = scheme.error
    val baselineColor = scheme.outlineVariant
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurface)
    val dayStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)

    val dayData = (0 until 7).map { i ->
        val dayMs = startOfWeek + (i * 86400000L)
        val stats = PayrollEngine.calculateDayStats(dayMs, excludeActive = false, state = state)
        Pair(days[i], stats)
    }

    var maxHours = 14.0
    dayData.forEach { (_, stats) ->
        if (stats.clockedHours > maxHours) maxHours = stats.clockedHours + 1.0
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val chartBottom = canvasHeight - 24.dp.toPx()
            val chartTop = 20.dp.toPx()
            val availableHeight = chartBottom - chartTop

            val barCount = 7
            val totalSpacing = canvasWidth * 0.25f
            val spacing = totalSpacing / (barCount + 1)
            val barWidth = (canvasWidth - totalSpacing) / barCount

            // Baseline
            drawLine(
                color = baselineColor,
                start = Offset(0f, chartBottom),
                end = Offset(canvasWidth, chartBottom),
                strokeWidth = 1.5.dp.toPx()
            )

            dayData.forEachIndexed { index, (dayName, stats) ->
                val x = spacing + (index * (barWidth + spacing))
                val totalH = ((stats.clockedHours / maxHours) * availableHeight).toFloat()
                val y = chartBottom - totalH

                val otH = ((stats.otHours / maxHours) * availableHeight).toFloat()
                val baseH = max(0f, totalH - otH)

                // Base Bar
                val barColor = if (stats.clockedHours > 0) baseBarColor else emptyBarColor
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y + otH),
                    size = Size(barWidth, baseH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // Overtime Top Bar (error role)
                if (stats.otHours > 0) {
                    drawRoundRect(
                        color = overtimeColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, otH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }

                // Value text on top
                if (stats.clockedHours > 0) {
                    val valueStr = String.format("%.1f", stats.clockedHours)
                    val textLayout = textMeasurer.measure(
                        text = valueStr,
                        style = valueStyle
                    )
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(x + (barWidth / 2f) - (textLayout.size.width / 2f), max(0f, y - textLayout.size.height - 2.dp.toPx()))
                    )
                }

                // Day Label below
                val labelLayout = textMeasurer.measure(
                    text = dayName,
                    style = dayStyle
                )
                drawText(
                    textLayoutResult = labelLayout,
                    topLeft = Offset(x + (barWidth / 2f) - (labelLayout.size.width / 2f), chartBottom + 4.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.height(10.dp).padding(end = 4.dp)) {
                    drawCircle(color = baseBarColor, radius = 5.dp.toPx())
                }
                Text("Base Hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.height(10.dp).padding(end = 4.dp)) {
                    drawCircle(color = overtimeColor, radius = 5.dp.toPx())
                }
                Text("Overtime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
