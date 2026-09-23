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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import com.randallengineering.jokarztimeclock.ui.theme.AmberWarning
import com.randallengineering.jokarztimeclock.ui.theme.EmeraldSuccess
import com.randallengineering.jokarztimeclock.ui.theme.PurpleAccent
import com.randallengineering.jokarztimeclock.ui.theme.RoseError
import com.randallengineering.jokarztimeclock.ui.theme.ShiftRing
import com.randallengineering.jokarztimeclock.ui.theme.StagePose
import com.randallengineering.jokarztimeclock.ui.theme.TimerDisplayStyle
import com.randallengineering.jokarztimeclock.ui.theme.WavyTopShape
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

/**
 * The "stage": one wavy-topped container holding the live timer and the primary action. Its corners
 * and wave depth spring (the theme's expressive motion scheme) to a new [StagePose] whenever the shift
 * state changes, and the primary button squashes and bounces back. The ring around the timer is the
 * official wavy progress indicator; the timer digits themselves are never animated.
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
    val stageSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val sizeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val poseSpec: AnimationSpec<Float> = if (reducedMotion) snap() else stageSpec
    val topStart by animateFloatAsState(pose.topStart, poseSpec, label = "stageTopStart")
    val topEnd by animateFloatAsState(pose.topEnd, poseSpec, label = "stageTopEnd")
    val bottomEnd by animateFloatAsState(pose.bottomEnd, poseSpec, label = "stageBottomEnd")
    val bottomStart by animateFloatAsState(pose.bottomStart, poseSpec, label = "stageBottomStart")
    val wave by animateFloatAsState(pose.waveAmplitude, poseSpec, label = "stageWave")

    // Clocking in or out squashes the primary action, which then springs back.
    val statePulse = rememberStatePulse(isClockedIn, reducedMotion)

    Surface(
        shape = WavyTopShape(
            waveAmplitude = wave.dp,
            topStart = topStart.dp,
            topEnd = topEnd.dp,
            bottomEnd = bottomEnd.dp,
            bottomStart = bottomStart.dp
        ),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = modifier.fillMaxWidth()
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
    val standardTargetMs = ((settings.standardShiftHours + mealToAdd) * 3600000.0).toLong()
    val cliffTargetMs = (settings.cliffHours * 3600000.0).toLong()

    // 0..1 across the standard shift, then again across standard → cliff.
    val progress = ShiftRing.progress(elapsedMs, standardTargetMs, cliffTargetMs)
    val ringColor = when (ShiftRing.tier(elapsedMs, standardTargetMs, cliffTargetMs)) {
        ShiftRing.Tier.STANDARD -> MaterialTheme.colorScheme.primary
        ShiftRing.Tier.BANKING -> AmberWarning
        ShiftRing.Tier.CLIFF -> RoseError
    }
    // The timer plate: onSurface on surfaceContainerHighest is a guaranteed high-contrast pair in
    // every preset and in wallpaper palettes, unlike anything derived from primary.
    val plateColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(264.dp)
    ) {
        // The official expressive wavy indicator owns the wiggle: its active arc undulates and the
        // wave travels on its own. Reduced motion flattens it (zero amplitude, zero wave speed).
        // Stroke weight stays at the ring's existing 12dp; wavelength and gap are the M3 tokens.
        val density = LocalDensity.current
        val ringStroke = remember(density) { with(density) { Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round) } }
        CircularWavyProgressIndicator(
            progress = { progress },
            color = ringColor,
            trackColor = plateColor,
            stroke = ringStroke,
            trackStroke = ringStroke,
            gapSize = WavyProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
            amplitude = ShiftRing.amplitude(reducedMotion),
            wavelength = WavyProgressIndicatorDefaults.CircularWavelength,
            waveSpeed = if (reducedMotion) 0.dp else WavyProgressIndicatorDefaults.CircularWavelength,
            modifier = Modifier.size(256.dp)
        )

        // A plain circle, not a decorative outline, so nothing competes with the digits.
        Box(
            modifier = Modifier
                .size(224.dp)
                .background(plateColor, CircleShape)
        )

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
        shape = CircleShape,
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
            // A wide button cannot carry a circle: toShape() scales the unit-square polygon to the
            // button's box, so Circle on this shape would render as an ellipse. MaterialShapes.Pill
            // is the official shape for a wide action, and it is what this button already looked like.
            shape = MaterialShapes.Pill.toShape(),
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

        val clockOutInteraction = remember { MutableInteractionSource() }
        val pressSquash = rememberPressSquash(clockOutInteraction, reducedMotion)
        Button(
            onClick = onClockToggle,
            shape = MaterialShapes.Square.toShape(),
            interactionSource = clockOutInteraction,
            colors = ButtonDefaults.buttonColors(
                containerColor = RoseError,
                contentColor = Color.White
            ),
            modifier = Modifier
                .height(50.dp)
                .graphicsLayer {
                    scaleX = pressSquash.value * statePulse.value
                    scaleY = scaleX
                }
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
}

@Composable
private fun ReadyStage(
    statePulse: State<Float>,
    reducedMotion: Boolean,
    onClockToggle: () -> Unit
) {
    Text(
        text = "READY FOR SHIFT",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp
    )

    Spacer(modifier = Modifier.height(20.dp))

    // The clock-in cookie (official MaterialShapes.Cookie9Sided): squashes while held, bounces on release.
    val interaction = remember { MutableInteractionSource() }
    val pressSquash = rememberPressSquash(interaction, reducedMotion)

    Surface(
        onClick = onClockToggle,
        shape = MaterialShapes.Cookie9Sided.toShape(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        interactionSource = interaction,
        modifier = Modifier
            .size(156.dp)
            .graphicsLayer {
                scaleX = pressSquash.value * statePulse.value
                scaleY = scaleX
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Clock In",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "CLOCK IN",
                color = MaterialTheme.colorScheme.onPrimary,
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
