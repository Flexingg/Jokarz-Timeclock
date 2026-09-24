package com.randallengineering.jokarztimeclock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randallengineering.jokarztimeclock.ui.theme.PillShape
import com.randallengineering.jokarztimeclock.ui.theme.TimerInlineStyle
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import java.util.Calendar
import kotlin.math.abs

@Composable
fun LiveStatsDrawer(
    state: TimeclockState,
    onEditStartClick: () -> Unit,
    onBreakToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.isClockedIn && state.currentSessionStart != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val startMs = state.currentSessionStart ?: System.currentTimeMillis()
        val elapsedMs = System.currentTimeMillis() - startMs

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            // Live Digital Timer (Click to edit start time)
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                modifier = Modifier.expressiveClickable(onClick = onEditStartClick)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = PayrollEngine.formatDuration(elapsedMs),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = TimerInlineStyle,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit Start Time",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Shift Status Calculation
            val cal = Calendar.getInstance().apply { timeInMillis = startMs }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val isMonThu = dayOfWeek in Calendar.MONDAY..Calendar.THURSDAY
            val rate = if (state.displayMode == PayMode.GROSS) state.grossRate else state.netRate
            val settings = state.settings

            if (state.isOnBreak) {
                val breakElapsed = state.accumulatedBreakMs + (System.currentTimeMillis() - (state.breakStartTime ?: System.currentTimeMillis()))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "ON BREAK / LUNCH: ",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = PayrollEngine.formatDuration(breakElapsed),
                        color = MaterialTheme.colorScheme.secondary,
                        style = TimerInlineStyle,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (isMonThu) {
                val prevBanked = PayrollEngine.getPreviousBankedHoursForCurrentWeek(startMs, state)
                val targetStandardHrs = (settings.standardShiftHours + settings.unpaidMealDuration) - prevBanked
                val standardMs = (targetStandardHrs * 3600000.0).toLong()
                val cliffMs = (settings.cliffHours * 3600000.0).toLong()

                if (elapsedMs < standardMs) {
                    val remainingMs = standardMs - elapsedMs
                    val bankText = if (abs(prevBanked) > 0.05) {
                        val sign = if (prevBanked > 0) "+" else ""
                        " ($sign${String.format("%.1f", prevBanked)}h bank)"
                    } else ""
                    val bankColor = if (prevBanked >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "REMAINING$bankText: ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = PayrollEngine.formatDuration(remainingMs),
                            color = MaterialTheme.colorScheme.tertiary,
                            style = TimerInlineStyle,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else if (elapsedMs < cliffMs) {
                    val bankingHrs = (elapsedMs - standardMs) / 3600000.0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "BANKING UNPAID: ",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "+${String.format("%.2f", bankingHrs)}h",
                            color = MaterialTheme.colorScheme.secondary,
                            style = TimerInlineStyle,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    val otHours = (elapsedMs - ((settings.standardShiftHours + settings.unpaidMealDuration) * 3600000.0).toLong()) / 3600000.0
                    val otPay = otHours * rate * settings.otMultiplier
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "LIVE OT (${settings.otMultiplier}x): ",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${String.format("%.2f", otHours)}h | ${PayrollEngine.formatMoney(otPay)}",
                            color = MaterialTheme.colorScheme.primary,
                            style = TimerInlineStyle,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else {
                val elapsedHrs = elapsedMs / 3600000.0
                val payableHours = if (elapsedHrs > 4.0) elapsedHrs - 0.5 else elapsedHrs
                val pay = maxOf(0.0, payableHours * rate * settings.otMultiplier)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "WEEKEND OT: ",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${String.format("%.2f", payableHours)}h | ${PayrollEngine.formatMoney(pay)}",
                        color = MaterialTheme.colorScheme.primary,
                        style = TimerInlineStyle,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Break / Lunch Button
            ScaledElevatedButton(
                onClick = onBreakToggle,
                shape = PillShape,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = if (state.isOnBreak) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (state.isOnBreak) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.secondary
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (state.isOnBreak) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.isOnBreak) "Resume Shift" else "Break / Lunch",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
