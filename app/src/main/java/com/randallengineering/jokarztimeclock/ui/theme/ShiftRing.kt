package com.randallengineering.jokarztimeclock.ui.theme

import androidx.compose.material3.WavyProgressIndicatorDefaults

/**
 * What the hero's progress ring shows, as pure functions so it is unit tested on the JVM. The ring
 * fills 0..1 across the standard shift, then restarts and fills 0..1 across the standard → cliff
 * segment; its colour tier says which segment the shift is in.
 */
internal object ShiftRing {

    enum class Tier { STANDARD, BANKING, CLIFF }

    /** A constant zero amplitude: a flat ring with no wave motion (reduced motion). One instance, so
     *  the indicator's identity check on the lambda does not invalidate it every second. */
    private val flat: (Float) -> Float = { 0f }

    fun progress(elapsedMs: Long, standardTargetMs: Long, cliffTargetMs: Long): Float =
        if (elapsedMs <= standardTargetMs) {
            fraction(elapsedMs, standardTargetMs)
        } else {
            fraction(elapsedMs - standardTargetMs, cliffTargetMs - standardTargetMs)
        }

    fun tier(elapsedMs: Long, standardTargetMs: Long, cliffTargetMs: Long): Tier = when {
        elapsedMs >= cliffTargetMs -> Tier.CLIFF
        elapsedMs >= standardTargetMs -> Tier.BANKING
        else -> Tier.STANDARD
    }

    /** The official wavy amplitude, or none at all when the owner has turned animations off. */
    fun amplitude(reducedMotion: Boolean): (Float) -> Float =
        if (reducedMotion) flat else WavyProgressIndicatorDefaults.indicatorAmplitude

    /** An empty or inverted segment (e.g. cliff set at or below the standard target) reads as full, not NaN. */
    private fun fraction(part: Long, whole: Long): Float =
        if (whole <= 0L) 1f else (part.toFloat() / whole.toFloat()).coerceIn(0f, 1f)
}
