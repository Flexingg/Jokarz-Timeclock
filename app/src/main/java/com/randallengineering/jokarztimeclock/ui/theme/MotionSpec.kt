package com.randallengineering.jokarztimeclock.ui.theme

/**
 * Pure motion mappers for the expressive main screen. No Compose types, so they are unit tested on
 * the JVM; the composables only feed them an animation fraction or the shift state.
 */

/**
 * The hero stage's geometry per shift state, in dp. Changing state animates between poses with a
 * bouncy spring, so the stage visibly "re-seats" itself when the owner clocks in, pauses or leaves.
 * Corners are logical (start/end) and mirror under RTL.
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
