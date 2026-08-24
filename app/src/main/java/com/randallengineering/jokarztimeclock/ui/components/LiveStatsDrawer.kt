package com.randallengineering.jokarztimeclock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import com.randallengineering.jokarztimeclock.ui.theme.AmberWarning
import com.randallengineering.jokarztimeclock.ui.theme.EmeraldSuccess
import com.randallengineering.jokarztimeclock.ui.theme.PurpleAccent
import com.randallengineering.jokarztimeclock.ui.theme.RoseError
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
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                modifier = Modifier.clickable { onEditStartClick() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = PayrollEngine.formatDuration(elapsedMs),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Edit,
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
                        color = AmberWarning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = PayrollEngine.formatDuration(breakElapsed),
                        color = AmberWarning,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
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
                    val bankColor = if (prevBanked >= 0) AmberWarning else RoseError

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "REMAINING$bankText: ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = PayrollEngine.formatDuration(remainingMs),
                            color = EmeraldSuccess,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
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
                            color = AmberWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "+${String.format("%.2f", bankingHrs)}h",
                            color = AmberWarning,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
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
                            color = PurpleAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${String.format("%.2f", otHours)}h | ${PayrollEngine.formatMoney(otPay)}",
                            color = PurpleAccent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
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
                        color = PurpleAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${String.format("%.2f", payableHours)}h | ${PayrollEngine.formatMoney(pay)}",
                        color = PurpleAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Break / Lunch Button
            ElevatedButton(
                onClick = onBreakToggle,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = if (state.isOnBreak) AmberWarning else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (state.isOnBreak) Color.Black else AmberWarning
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (state.isOnBreak) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.isOnBreak) "Resume Shift" else "Break / Lunch",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
