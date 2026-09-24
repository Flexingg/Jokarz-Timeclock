package com.randallengineering.jokarztimeclock.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pure motion mappers for the main screen. No composables, so they are unit tested on the JVM; the
 * composables only feed them an animation fraction or the shift state.
 *
 * Motion policy (owner's correction of the earlier "expressive" pass): smooth and settled. Nothing
 * bounces, nothing overshoots, every animation finishes inside the 600 ms budget, and reduced motion
 * turns the decorative ones off. ThemeMotionTest samples every spec below to prove it.
 */

/** The app's own animation specs. Eased tweens only: a Bézier with control points in 0..1 cannot overshoot. */
internal object AppMotion {

    /** Material's standard easing (0.2, 0, 0, 1): quick start, long gentle settle, never past the target. */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Stage corners / wave re-seating to a new shift state. */
    const val STAGE_MS = 450

    /** A pressed control easing down to its squash. */
    const val PRESS_MS = 120

    /** A released control easing back to full size. */
    const val RELEASE_MS = 220

    /** The whole state pulse: ease down to [PULSE_SQUASH] and back up to 1. */
    const val PULSE_MS = 360

    /** Deepest point of the state pulse. */
    const val PULSE_SQUASH = 0.92f

    /** How far a held button squashes. */
    const val PRESS_SQUASH = 0.94f

    /** Every duration here, for the budget test. */
    val ALL_DURATIONS_MS = intArrayOf(STAGE_MS, PRESS_MS, RELEASE_MS, PULSE_MS)

    fun <T> stageSpec(): FiniteAnimationSpec<T> = tween(STAGE_MS, easing = StandardEasing)

    fun <T> pressSpec(pressed: Boolean): FiniteAnimationSpec<T> =
        tween(if (pressed) PRESS_MS else RELEASE_MS, easing = StandardEasing)

    /**
     * Scale of the state pulse at [fraction] (0..1 of [PULSE_MS]): starts and ends at exactly 1, dips
     * smoothly to [PULSE_SQUASH] half way, and never leaves [PULSE_SQUASH]..1 — so there is no snap at
     * the start and no spring-back past full size at the end.
     */
    fun pulseScaleAt(fraction: Float): Float {
        val s = sin(PI * fraction.coerceIn(0f, 1f)).toFloat()
        return 1f - (1f - PULSE_SQUASH) * s * s
    }
}


/**
 * The hero stage's geometry per shift state, in dp. Changing state eases between poses on
 * [AppMotion.stageSpec] (no overshoot), so the stage visibly settles into its new shape when the
 * owner clocks in, pauses or leaves. Corners are logical (start/end) and mirror under RTL.
 */
internal enum class StagePose(
    val topStart: Float,
    val topEnd: Float,
    val bottomEnd: Float,
    val bottomStart: Float,
    val waveAmplitude: Float
) {
    /** Clocked out: lopsided and playful, the deepest wave. */
    READY(topStart = 44f, topEnd = 16f, bottomEnd = 44f, bottomStart = 16f, waveAmplitude = 12f),

    /** On the clock: calm and even so nothing competes with the timer. */
    WORKING(topStart = 32f, topEnd = 32f, bottomEnd = 32f, bottomStart = 32f, waveAmplitude = 6f),

    /** On break: the lopsidedness flips the other way. */
    BREAK(topStart = 16f, topEnd = 44f, bottomEnd = 16f, bottomStart = 44f, waveAmplitude = 10f);

    companion object {
        /** A break only exists inside a shift; a stale break flag while clocked out reads as READY. */
        fun of(isClockedIn: Boolean, isOnBreak: Boolean): StagePose = when {
            !isClockedIn -> READY
            isOnBreak -> BREAK
            else -> WORKING
        }
    }
}

/**
 * Timeline of the clock in/out/import confirmation badge: a cookie that pops in, morphs into a
 * squircle and fades. Pure decoration — the state change has already happened when it starts.
 */
internal object ConfirmationMotion {

    /** Whole animation, kept under the 600 ms budget. */
    const val DURATION_MS = 520

    /** Fraction of the timeline spent morphing cookie → squircle. */
    private const val MORPH_END = 0.6f

    /** Fraction after which the badge fades out. */
    private const val FADE_START = 0.65f

    /** Starting scale of the pop. */
    private const val POP_FROM = 0.55f

    /** 0 = cookie, 1 = squircle. Eased out so the change is quick and then settles. */
    fun morphAt(fraction: Float): Float = easeOutCubic((fraction / MORPH_END).coerceIn(0f, 1f))

    /** Pops from [POP_FROM] to full size over the morph. */
    fun scaleAt(fraction: Float): Float = POP_FROM + (1f - POP_FROM) * morphAt(fraction)

    /** Fully opaque until [FADE_START], then linear to invisible at the end. */
    fun alphaAt(fraction: Float): Float {
        val f = fraction.coerceIn(0f, 1f)
        return if (f <= FADE_START) 1f else 1f - (f - FADE_START) / (1f - FADE_START)
    }

    private fun easeOutCubic(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }
}

/**
 * Dialog enter / exit. The animation drives a LINEAR progress (0 = closed, 1 = open) and the easing is
 * applied here, in the mapping. So closing — Cancel, a scrim tap, the system back button, or a
 * committed back gesture — runs the very same frames as opening, in reverse; a predictive back
 * gesture scrubs the same progress with the finger.
 */
internal object DialogMotion {

    /** Open and close each take this long (the budget is 600 ms). */
    const val DURATION_MS = 300

    /** Scale of the dialog when fully closed; it grows from here to 1 as it opens. */
    const val CLOSED_SCALE = 0.9f

    /** How much of the close a full-length back gesture previews (the rest plays on commit). */
    const val GESTURE_SHARE = 0.5f

    private val easing = AppMotion.StandardEasing

    private fun eased(progress: Float): Float = easing.transform(progress.coerceIn(0f, 1f))

    fun alphaAt(progress: Float): Float = eased(progress)

    fun scaleAt(progress: Float): Float = CLOSED_SCALE + (1f - CLOSED_SCALE) * eased(progress)

    /** Dialog progress while a back gesture is [gestureProgress] (0..1) of the way through. */
    fun progressForBackGesture(gestureProgress: Float): Float = 1f - GESTURE_SHARE * gestureProgress.coerceIn(0f, 1f)
}
