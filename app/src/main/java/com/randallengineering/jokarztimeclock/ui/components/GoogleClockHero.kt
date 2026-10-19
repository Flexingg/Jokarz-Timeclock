package com.randallengineering.jokarztimeclock.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randallengineering.jokarztimeclock.ui.theme.TimerInlineStyle
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import com.randallengineering.jokarztimeclock.ui.theme.AppMotion
import com.randallengineering.jokarztimeclock.ui.theme.ShiftRing
import com.randallengineering.jokarztimeclock.ui.theme.StagePose
import com.randallengineering.jokarztimeclock.ui.theme.TimerDisplayStyle
import com.randallengineering.jokarztimeclock.ui.theme.FluidLight
import com.randallengineering.jokarztimeclock.ui.theme.FluidRadius
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max

/**
 * The "stage": one wavy-topped container holding the live timer and the primary action. Its corners
 * and wave depth ease (AppMotion.stageSpec, no overshoot) into a new [StagePose] whenever the shift
 * state changes, and the primary action dips slightly and settles back to full size. The ring around
 * the timer is the official wavy progress indicator; the timer digits themselves are never animated.
 */
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
    val reducedMotion = rememberReducedMotion()

    val pose = StagePose.of(isClockedIn, state.isOnBreak)
    // Light as signal: the stage's glow re-colours and re-sizes with the shift state, easing into its
    // new pose without overshoot (the owner asked for no bounce; see ThemeMotionTest).
    val sizeSpec = AppMotion.stageSpec<IntSize>()
    val poseSpec: AnimationSpec<Float> = if (reducedMotion) snap() else AppMotion.stageSpec()
    val scheme = MaterialTheme.colorScheme
    val glowTarget = when (pose) {
        StagePose.READY -> scheme.primary
        StagePose.WORKING -> scheme.primary
        StagePose.BREAK -> scheme.secondary
    }
    val glowColor by animateColorAsState(glowTarget, if (reducedMotion) snap() else AppMotion.stageSpec(), label = "stageGlow")
    val glowAlpha by animateFloatAsState(pose.glowAlpha * if (scheme.isDark()) FluidLight.DARK_GLOW_FACTOR else 1f, poseSpec, label = "stageGlowAlpha")
    val glowCenter by animateFloatAsState(pose.glowCenterY, poseSpec, label = "stageGlowCenter")

    // Clocking in or out dips the primary action slightly; it eases back to full size (no bounce).
    val statePulse = rememberStatePulse(isClockedIn, reducedMotion)

    Surface(
        shape = RoundedCornerShape(FluidRadius.xl),
        color = fluidCardColor(),
        border = fluidContour(),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(glowColor.copy(alpha = glowAlpha), glowColor.copy(alpha = 0f)),
                        center = Offset(size.width / 2f, size.height * glowCenter),
                        radius = size.width * pose.glowRadius
                    )
                )
            }
        ) {
        AnimatedContent(
            targetState = isClockedIn,
            transitionSpec = {
                if (reducedMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    // A plain cross-fade: nothing slides or scales under the timer.
                    fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) using
                        SizeTransform(clip = false) { _, _ -> sizeSpec }
                }
            },
            contentAlignment = Alignment.TopCenter,
            label = "stageContent"
        ) { clockedIn ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
            ) {
                if (clockedIn) {
                    ActiveShiftStage(
                        state = state,
                        currentTickMs = currentTickMs,
                        statePulse = statePulse,
                        reducedMotion = reducedMotion,
                        onClockToggle = onClockToggle,
                        onBreakToggle = onBreakToggle,
                        onEditStartClick = onEditStartClick
                    )
                } else {
                    ReadyStage(
                        statePulse = statePulse,
                        reducedMotion = reducedMotion,
                        onClockToggle = onClockToggle
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun ActiveShiftStage(
    state: TimeclockState,
    currentTickMs: Long,
    statePulse: State<Float>,
    reducedMotion: Boolean,
    onClockToggle: () -> Unit,
    onBreakToggle: () -> Unit,
    onEditStartClick: () -> Unit
) {
    // During the exit cross-fade after clocking out the session start is already gone; show 0.
    val startMs = state.currentSessionStart ?: currentTickMs
    val elapsedMs = if (state.currentSessionStart != null) max(0L, currentTickMs - startMs) else 0L

    val settings = state.settings
    val mealToAdd = if (settings.autoBreakDeduction) settings.unpaidMealDuration else 0.0
    val isMonThu = Calendar.getInstance().apply { timeInMillis = startMs }.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.THURSDAY
    val prevBanked = if (isMonThu) PayrollEngine.getPreviousBankedHoursForCurrentWeek(startMs, state) else 0.0
    val standardTargetMs = (((settings.standardShiftHours + mealToAdd) - prevBanked) * 3600000.0).toLong()
    val cliffTargetMs = (settings.effectiveCliffHours * 3600000.0).toLong()

    // 0..1 across the standard shift, then again across standard → cliff.
    val progress = ShiftRing.progress(elapsedMs, standardTargetMs, cliffTargetMs)
    val ringColor = when (ShiftRing.tier(elapsedMs, standardTargetMs, cliffTargetMs)) {
        ShiftRing.Tier.STANDARD -> MaterialTheme.colorScheme.primary
        ShiftRing.Tier.BANKING -> MaterialTheme.colorScheme.secondary
        ShiftRing.Tier.CLIFF -> MaterialTheme.colorScheme.error
    }
    // The timer plate: onSurface on surfaceContainerHighest is a guaranteed high-contrast pair in
    // every preset and in wallpaper palettes, unlike anything derived from primary.
    val plateColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(264.dp)
    ) {
        // A luminous progress ring: a soft track, an arc that brightens toward its head, and a
        // "comet" glow riding the leading edge (light as the progress signal).
        LuminousRing(progress = progress, color = ringColor, modifier = Modifier.size(256.dp))

        // A plain circle, not a decorative outline, so nothing competes with the digits.
        Box(
            modifier = Modifier
                .size(224.dp)
                .background(plateColor, CircleShape)
                .border(fluidContour(), CircleShape)
        )

        // Center Digital Counter
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.expressiveClickable { onEditStartClick() }
        ) {
            Text(
                text = if (state.isOnBreak) "LUNCH / BREAK" else "ACTIVE SHIFT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (state.isOnBreak) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // The live timer: raw text, largest type on screen, never wrapped in an animation.
            Text(
                text = PayrollEngine.formatDuration(elapsedMs),
                style = TimerDisplayStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit start time",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Edit Start",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Real-time Shift Status Pill
    val rate = if (state.displayMode == PayMode.GROSS) state.grossRate else state.netRate

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        border = fluidContour(),
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
                    color = MaterialTheme.colorScheme.secondary,
                    style = TimerInlineStyle,
                    fontWeight = FontWeight.Bold,
                )
            } else if (isMonThu) {
                val prevBanked = PayrollEngine.getPreviousBankedHoursForCurrentWeek(startMs, state)
                val mealBreakToAdd = if (settings.autoBreakDeduction) settings.unpaidMealDuration else 0.0
                val targetStandardHrs = (settings.standardShiftHours + mealBreakToAdd) - prevBanked
                val standardMs = (targetStandardHrs * 3600000.0).toLong()

                if (elapsedMs < standardMs) {
                    val remainingMs = standardMs - elapsedMs
                    val bankNote = if (abs(prevBanked) > 0.05) {
                        val sign = if (prevBanked > 0) "+" else ""
                        " ($sign${String.format("%.1f", prevBanked)}h bank)"
                    } else ""
                    Text(
                        text = "Standard Shift: ${PayrollEngine.formatDuration(remainingMs)} remaining$bankNote",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (elapsedMs < cliffTargetMs) {
                    val bankingHrs = (elapsedMs - standardMs) / 3600000.0
                    Text(
                        text = "Banking Buffer: +${String.format("%.2f", bankingHrs)}h (Unpaid)",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    val otHours = (elapsedMs - standardTargetMs) / 3600000.0
                    val otPay = otHours * rate * settings.otMultiplier
                    val moneyStr = if (settings.hideMoneyAmounts) "" else " • ${PayrollEngine.formatMoney(otPay)}"
                    Text(
                        text = "Overtime (${settings.otMultiplier}x): ${String.format("%.2f", otHours)}h$moneyStr",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
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
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // Action Controls: Break / Resume & Clock Out buttons
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        ScaledFilledTonalButton(
            onClick = onBreakToggle,
            shape = CircleShape,
            colors = if (state.isOnBreak) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            } else {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            },
            border = fluidContour(),
            modifier = Modifier.height(52.dp)
        ) {
            Icon(
                imageVector = if (state.isOnBreak) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.isOnBreak) "Resume" else "Break",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        ScaledButton(
            onClick = onClockToggle,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            border = fluidContour(),
            modifier = Modifier
                .height(52.dp)
                .drawScaled(statePulse)
        ) {
            Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Clock Out",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ReadyStage(
    statePulse: State<Float>,
    reducedMotion: Boolean,
    onClockToggle: () -> Unit
) {
    Text(
        text = "READY FOR SHIFT",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp
    )

    Spacer(modifier = Modifier.height(20.dp))

    // The clock-in orb: a lit sphere of the primary colour with a soft halo, a highlight from above
    // and a contour edge; it compresses under the finger and a glow follows the touch
    // (expressiveClickable). The state pulse is a draw-time scale (drawScaled - never a
    // graphicsLayer here, see drawScaled for why).
    val orb = MaterialTheme.colorScheme.primary
    val onOrb = MaterialTheme.colorScheme.onPrimary
    val haloAlpha = if (MaterialTheme.colorScheme.isDark()) 0.42f else 0.30f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(160.dp)
            .drawScaled(statePulse)
            .drawBehind {
                val r = size.minDimension / 2f
                drawCircle(
                    Brush.radialGradient(
                        0.55f to orb.copy(alpha = haloAlpha), 1f to orb.copy(alpha = 0f),
                        center = center, radius = r * 1.45f
                    ),
                    radius = r * 1.45f
                )
                drawCircle(orb, radius = r)
                drawCircle(
                    Brush.radialGradient(
                        listOf(onOrb.copy(alpha = 0.30f), onOrb.copy(alpha = 0f)),
                        center = Offset(center.x, center.y - r * 0.55f),
                        radius = r * 0.9f
                    ),
                    radius = r
                )
                drawCircle(
                    Brush.verticalGradient(listOf(onOrb.copy(alpha = 0.45f), onOrb.copy(alpha = 0.05f))),
                    radius = r - 0.5.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clip(CircleShape)
            .expressiveClickable(shape = CircleShape, onClick = onClockToggle)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Clock In",
                tint = onOrb,
                modifier = Modifier.size(52.dp)
            )
            Text(
                text = "Clock In",
                color = onOrb,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    Spacer(modifier = Modifier.height(22.dp))

    Text(
        text = "Tap to begin tracking today's hours",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The active shift's progress ring: a faint full-circle track, a progress arc whose colour gathers
 * toward its head, and a soft "comet" glow with a bright core on the leading edge.
 */
@Composable
private fun LuminousRing(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    val track = if (s.isDark()) s.onSurface.copy(alpha = 0.08f) else s.outlineVariant.copy(alpha = 0.55f)
    val core = if (s.isDark()) s.onSurface else s.surfaceContainerLowest
    Canvas(modifier = modifier) {
        val stroke = 12.dp.toPx()
        val r = (size.minDimension - stroke) / 2f
        val topLeft = Offset(center.x - r, center.y - r)
        val arcSize = Size(r * 2f, r * 2f)
        drawCircle(track, radius = r, style = Stroke(stroke))
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f) return@Canvas
        rotate(-90f) {
            drawArc(
                brush = Brush.sweepGradient(0f to color.copy(alpha = 0.35f), p to color, 1f to color, center = center),
                startAngle = 0f,
                sweepAngle = 360f * p,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        val angle = Math.toRadians(360.0 * p - 90.0)
        val head = Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat())
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0f)), center = head, radius = stroke * 2.2f),
            radius = stroke * 2.2f,
            center = head
        )
        drawCircle(core.copy(alpha = 0.9f), radius = stroke * 0.28f, center = head)
    }
}
