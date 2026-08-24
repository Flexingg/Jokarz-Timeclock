package com.randallengineering.jokarztimeclock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import com.randallengineering.jokarztimeclock.ui.theme.AmberWarning
import com.randallengineering.jokarztimeclock.ui.theme.PurpleAccent
import com.randallengineering.jokarztimeclock.ui.theme.RoseError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class WeekCardData(
    val weekStartMs: Long,
    val label: String,
    val isCurrentWeek: Boolean,
    val weekClockedMs: Long,
    val weekSystemInput: Double,
    val weekBanked: Double
)

@Composable
fun GoogleWeeklySwiper(
    state: TimeclockState,
    modifier: Modifier = Modifier
) {
    val currentWeekStart = PayrollEngine.getStartOfWeekDate()
    val weekStarts = mutableSetOf(currentWeekStart)

    state.sessions.forEach { sess ->
        weekStarts.add(PayrollEngine.getStartOfWeekDate(Date(sess.start)))
    }

    val sortedWeeks = weekStarts.sortedDescending()
    val weekDataList = sortedWeeks.map { weekStartMs ->
        var weekBanked = 0.0
        var weekSystemInput = 0.0
        var weekClockedMs = 0L

        var cur = weekStartMs
        val todayStart = PayrollEngine.getStartOfDay()
        for (i in 0 until 7) {
            if (cur <= todayStart) {
                val stats = PayrollEngine.calculateDayStats(cur, excludeActive = false, state = state)
                weekBanked += stats.bankedHours
                weekSystemInput += stats.systemInput
                weekClockedMs += stats.clockedMs
            }
            cur += 86400000L
        }

        val dStart = Date(weekStartMs)
        val dEnd = Date(weekStartMs + (6L * 86400000L))
        val sdf = SimpleDateFormat("MMM d", Locale.US)
        val label = if (weekStartMs == currentWeekStart) "Current Week" else "${sdf.format(dStart)} - ${sdf.format(dEnd)}"

        WeekCardData(
            weekStartMs = weekStartMs,
            label = label,
            isCurrentWeek = weekStartMs == currentWeekStart,
            weekClockedMs = weekClockedMs,
            weekSystemInput = weekSystemInput,
            weekBanked = weekBanked
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "WEEKLY BANK & INPUT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(weekDataList) { data ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    tonalElevation = 2.dp,
                    modifier = Modifier.width(260.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = data.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${PayrollEngine.formatDurationShort(data.weekClockedMs)} Tech",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "SYSTEM INPUT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${String.format("%.1f", data.weekSystemInput)}h OT",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (data.weekSystemInput > 0) PurpleAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (abs(data.weekBanked) > 0.05) {
                                val bankColor = if (data.weekBanked >= 0) AmberWarning else RoseError
                                val bankSign = if (data.weekBanked >= 0) "+" else ""
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "NET BANKED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$bankSign${String.format("%.1f", data.weekBanked)}h",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = bankColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
