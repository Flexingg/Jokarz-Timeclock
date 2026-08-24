package com.randallengineering.jokarztimeclock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.randallengineering.jokarztimeclock.ui.theme.PurplePrimary
import com.randallengineering.jokarztimeclock.ui.theme.RoseError
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

@Composable
fun GoogleClockHero(
    state: TimeclockState,
    currentTickMs: Long,
    onClockToggle: () -> Unit,
    onBreakToggle: () -> Unit,
    onEditStartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isClockedIn = state.isClockedIn && state.currentSessionStart != null
    val startMs = state.currentSessionStart ?: currentTickMs
    val elapsedMs = if (isClockedIn) max(0L, currentTickMs - startMs) else 0L

    val settings = state.settings
    val standardTargetMs = ((settings.standardShiftHours + settings.unpaidMealDuration) * 3600000.0).toLong()
    val cliffTargetMs = (settings.cliffHours * 3600000.0).toLong()

    // Circular progress animation (0.0 to 1.0)
    val progress = if (isClockedIn) {
        if (elapsedMs <= standardTargetMs) {
            (elapsedMs.toFloat() / standardTargetMs.toFloat()).coerceIn(0f, 1f)
        } else {
            ((elapsedMs - standardTargetMs).toFloat() / (cliffTargetMs - standardTargetMs).toFloat()).coerceIn(0f, 1f)
        }
    } else 0f

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp)
        ) {
            if (isClockedIn) {
                // GOOGLE CLOCK ACTIVE STOPWATCH DISPLAY WITH RADIAL RING
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(240.dp)
                ) {
                    Canvas(modifier = Modifier.size(230.dp)) {
                        val strokeWidth = 10.dp.toPx()
                        val arcSize = size.width - strokeWidth
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                        // Background track
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Active dynamic arc
                        val arcColor = if (elapsedMs >= cliffTargetMs) RoseError
                        else if (elapsedMs >= standardTargetMs) AmberWarning
                        else primaryColor

                        drawArc(
                            color = arcColor,
                            startAngle = -90f,
                            sweepAngle = progress * 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Center Digital Counter
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onEditStartClick() }
                    ) {
                        Text(
                            text = if (state.isOnBreak) "LUNCH / BREAK" else "ACTIVE SHIFT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isOnBreak) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = PayrollEngine.formatDuration(elapsedMs),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit start time",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Edit Start",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real-time Shift Status Pill
                val cal = Calendar.getInstance().apply { timeInMillis = startMs }
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val isMonThu = dayOfWeek in Calendar.MONDAY..Calendar.THURSDAY
                val rate = if (state.displayMode == PayMode.GROSS) state.grossRate else state.netRate

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (state.isOnBreak) {
                            val breakElapsed = state.accumulatedBreakMs + (currentTickMs - (state.breakStartTime ?: currentTickMs))
                            Text(
                                text = "On Break: ${PayrollEngine.formatDuration(breakElapsed)}",
                                color = AmberWarning,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        } else if (isMonThu) {
                            val prevBanked = PayrollEngine.getPreviousBankedHoursForCurrentWeek(startMs, state)
                            val targetStandardHrs = (settings.standardShiftHours + settings.unpaidMealDuration) - prevBanked
                            val standardMs = (targetStandardHrs * 3600000.0).toLong()

                            if (elapsedMs < standardMs) {
                                val remainingMs = standardMs - elapsedMs
                                val bankNote = if (abs(prevBanked) > 0.05) {
                                    val sign = if (prevBanked > 0) "+" else ""
                                    " ($sign${String.format("%.1f", prevBanked)}h bank)"
                                } else ""
                                Text(
                                    text = "Standard Shift: ${PayrollEngine.formatDuration(remainingMs)} remaining$bankNote",
                                    color = EmeraldSuccess,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else if (elapsedMs < cliffTargetMs) {
                                val bankingHrs = (elapsedMs - standardMs) / 3600000.0
                                Text(
                                    text = "Banking Buffer: +${String.format("%.2f", bankingHrs)}h (Unpaid)",
                                    color = AmberWarning,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                val otHours = (elapsedMs - standardTargetMs) / 3600000.0
                                val otPay = otHours * rate * settings.otMultiplier
                                val moneyStr = if (settings.hideMoneyAmounts) "" else " • ${PayrollEngine.formatMoney(otPay)}"
                                Text(
                                    text = "Overtime (${settings.otMultiplier}x): ${String.format("%.2f", otHours)}h$moneyStr",
                                    color = PurpleAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            val elapsedHrs = elapsedMs / 3600000.0
                            val payableHours = if (elapsedHrs > 4.0) elapsedHrs - 0.5 else elapsedHrs
                            val pay = maxOf(0.0, payableHours * rate * settings.otMultiplier)
                            val moneyStr = if (settings.hideMoneyAmounts) "" else " • ${PayrollEngine.formatMoney(pay)}"
                            Text(
                                text = "Weekend OT: ${String.format("%.2f", payableHours)}h$moneyStr",
                                color = PurpleAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Controls (Pause / Resume & Clock Out)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onBreakToggle,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (state.isOnBreak) AmberWarning else MaterialTheme.colorScheme.surface,
                            contentColor = if (state.isOnBreak) Color.Black else AmberWarning
                        ),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isOnBreak) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isOnBreak) "Resume" else "Lunch / Pause",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = onClockToggle,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseError,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Clock Out",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

            } else {
                // GOOGLE CLOCK READY STATE
                Text(
                    text = "READY FOR SHIFT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Big Google Clock In Button
                Surface(
                    shape = CircleShape,
                    color = primaryColor,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClockToggle
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Clock In",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "CLOCK IN",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tap to begin tracking today's hours",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
