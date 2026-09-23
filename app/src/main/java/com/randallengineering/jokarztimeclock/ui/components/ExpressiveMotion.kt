package com.randallengineering.jokarztimeclock.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.randallengineering.jokarztimeclock.ui.theme.ConfirmationMotion
import com.randallengineering.jokarztimeclock.ui.theme.ExpressiveShapes
import com.randallengineering.jokarztimeclock.ui.theme.ShapeMorph
import com.randallengineering.jokarztimeclock.ui.theme.shapeMorph
import kotlinx.coroutines.CoroutineScope

/*
 * Playful motion for the main screen. Everything here is decoration layered on a state change that
 * has already happened: nothing waits for an animation, nothing loops, and nothing touches the live
 * notification (the status bar chronometer is system-drawn; see NoPeriodicNotificationUpdateTest).
 * Compose already scales its animations by the system animator duration scale; the explicit
 * reduced-motion flag additionally drops the purely decorative squashes and the confirmation badge.
 */

/** The bouncy spring shared by the primary action, the stage morph and the state pulse. */
fun <T> expressiveSpring(): SpringSpec<T> =
    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

/** True when the owner has turned animations off (Developer options / "Remove animations"). */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Runs [block] whenever [key] changes, but not for the value it had on first composition — so a
 * screen opened (or rotated) while already clocked in does not replay the "you just clocked in" moment.
 */
@Composable
fun LaunchedChangeEffect(key: Any?, block: suspend CoroutineScope.() -> Unit) {
    val seen = remember { booleanArrayOf(false) }
    LaunchedEffect(key) {
        if (!seen[0]) {
            seen[0] = true
            return@LaunchedEffect
        }
        block()
    }
}

/** How far a state pulse squashes before springing back. */
private const val PULSE_SQUASH = 0.86f

/** How far a held primary button squashes. */
private const val PRESS_SQUASH = 0.9f

/**
 * A scale that squashes and springs back to 1 each time [key] changes (e.g. the shift state).
 * Read it inside `graphicsLayer {}` so the bounce only redraws.
 */
@Composable
fun rememberStatePulse(key: Any?, reducedMotion: Boolean): State<Float> {
    val scale = remember { Animatable(1f) }
    LaunchedChangeEffect(key) {
        if (reducedMotion) return@LaunchedChangeEffect
        scale.snapTo(PULSE_SQUASH)
        scale.animateTo(1f, expressiveSpring())
    }
    return scale.asState()
}

/** Squash-while-pressed for a primary button, released with the bouncy spring. */
@Composable
fun rememberPressSquash(interactionSource: InteractionSource, reducedMotion: Boolean): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) PRESS_SQUASH else 1f,
        animationSpec = expressiveSpring(),
        label = "pressSquash"
    )
}

/**
 * A short (< 600 ms) cookie → squircle "done" badge that pops, morphs and fades whenever [trigger]
 * changes after first composition. It has no pointer input, so taps go straight through to whatever
 * is underneath; a new trigger restarts it, and with animations off it never shows.
 */
@Composable
fun ConfirmationBurst(trigger: Any?, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    val morph = remember { ShapeMorph(ExpressiveShapes.Cookie, ExpressiveShapes.Badge) }
    LaunchedChangeEffect(trigger) {
        if (reducedMotion) return@LaunchedChangeEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(ConfirmationMotion.DURATION_MS, easing = LinearEasing))
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(76.dp)
            .graphicsLayer {
                val t = progress.value
                alpha = if (t >= 1f) 0f else ConfirmationMotion.alphaAt(t)
                scaleX = ConfirmationMotion.scaleAt(t)
                scaleY = scaleX
            }
            .shapeMorph(morph) { ConfirmationMotion.morphAt(progress.value) }
            .background(MaterialTheme.colorScheme.primary)
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(38.dp)
        )
    }
}

/**
 * Text that rolls vertically to its new value. For figures that change occasionally (hours paid);
 * never use it for the live timer, which must stay raw text.
 */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith
                (slideOutVertically { -it / 2 } + fadeOut()) using SizeTransform(clip = false)
        },
        label = "rollingText",
        modifier = modifier
    ) { value ->
        Text(text = value, style = style, color = color)
    }
}

/**
 * An amount that counts smoothly to [target] and then shows [target] exactly (the float used while
 * counting never leaks into the settled figure). Short enough that a once-a-second update while
 * clocked in settles well before the next one.
 */
@Composable
fun rememberCountingAmount(target: Double): Double {
    val animated by animateFloatAsState(
        targetValue = target.toFloat(),
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "countingAmount"
    )
    return if (animated == target.toFloat()) target else animated.toDouble()
}
