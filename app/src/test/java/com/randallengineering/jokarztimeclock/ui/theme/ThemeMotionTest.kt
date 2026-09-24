package com.randallengineering.jokarztimeclock.ui.theme

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.material3.MotionScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner asked for calm motion: MotionScheme.standard(), no bounce, no overshoot, < 600 ms. This
 * samples the real animation specs (the same maths Compose runs) so re-introducing the expressive
 * scheme or a bouncy spring fails here instead of shipping.
 */
class ThemeMotionTest {

    @Test
    fun themeUsesTheStandardMotionScheme() {
        assertEquals(MotionScheme.standard()::class, AppMotionScheme::class)
        assertNotEquals(MotionScheme.expressive()::class, AppMotionScheme::class)
    }

    @Test
    fun appSpecsNeverOvershootAndStayInBudget() {
        val specs = mapOf(
            "stage" to AppMotion.stageSpec<Float>(),
            "press" to AppMotion.pressSpec<Float>(pressed = true),
            "release" to AppMotion.pressSpec<Float>(pressed = false)
        )
        for ((name, spec) in specs) {
            val run = sample(spec)
            assertTrue("$name overshoots by ${run.overshoot}", run.overshoot <= 1e-4f)
            assertTrue("$name dips below its start by ${run.undershoot}", run.undershoot <= 1e-4f)
            assertTrue("$name takes ${run.durationMs} ms", run.durationMs < 600)
        }
        for (ms in AppMotion.ALL_DURATIONS_MS) assertTrue("$ms ms is over budget", ms < 600)
    }

    /**
     * The theme scheme's own specs (read by library components) must be the calm ones: sampled at 1 ms,
     * none of standard()'s springs ever passes its target, where expressive()'s default spatial spring
     * overshoots by ~1.5 %. The comparison proves this test can tell the two schemes apart.
     */
    @Test
    fun themeSpecsDoNotBounce() {
        val standard = listOf(
            AppMotionScheme.fastSpatialSpec<Float>(),
            AppMotionScheme.defaultSpatialSpec<Float>(),
            AppMotionScheme.slowSpatialSpec<Float>()
        ).map { sample(it).overshoot }
        standard.forEach { assertTrue("standard spatial overshoot $it", it <= 1e-4f) }

        listOf(
            AppMotionScheme.fastEffectsSpec<Float>(),
            AppMotionScheme.defaultEffectsSpec<Float>(),
            AppMotionScheme.slowEffectsSpec<Float>()
        ).forEach { assertTrue(sample(it).overshoot <= 1e-4f) }

        val expressive = sample(MotionScheme.expressive().defaultSpatialSpec<Float>()).overshoot
        assertTrue("expressive $expressive should overshoot, unlike standard $standard", expressive > 0.01f)
    }

    @Test
    fun statePulseIsSmoothAndNeverPassesFullSize() {
        assertEquals(1f, AppMotion.pulseScaleAt(0f), 0f)
        assertEquals(1f, AppMotion.pulseScaleAt(1f), 1e-6f)
        assertEquals(AppMotion.PULSE_SQUASH, AppMotion.pulseScaleAt(0.5f), 1e-6f)
        var previous = 1f
        for (i in 1..1000) {
            val v = AppMotion.pulseScaleAt(i / 1000f)
            assertTrue("pulse left its range: $v", v in AppMotion.PULSE_SQUASH..1f)
            // No snap: at 1 ms resolution of a 360 ms pulse, no step is larger than a sliver.
            assertTrue("pulse jumps ${previous - v} at $i", kotlin.math.abs(previous - v) < 0.001f)
            previous = v
        }
        // Fully monotonic down, then up (a bounce would reverse direction more than once).
        val values = (0..200).map { AppMotion.pulseScaleAt(it / 200f) }
        val reversals = values.zipWithNext { a, b -> b - a }.filter { it != 0f }
            .zipWithNext { a, b -> a.sign() != b.sign() }.count { it }
        assertEquals(1, reversals)
    }

    @Test
    fun squashesAreSlight() {
        assertTrue(AppMotion.PRESS_SQUASH in 0.9f..0.99f)
        assertTrue(AppMotion.PULSE_SQUASH in 0.9f..0.99f)
    }

    private fun Float.sign(): Int = if (this > 0f) 1 else -1

    private class Run(val overshoot: Float, val undershoot: Float, val durationMs: Long)

    /** Animates 0 → 1 at 1 ms steps and reports how far it ever left 0..1. */
    private fun sample(spec: FiniteAnimationSpec<Float>): Run {
        val v = spec.vectorize(Float.VectorConverter)
        val from = AnimationVector1D(0f)
        val to = AnimationVector1D(1f)
        val velocity = AnimationVector1D(0f)
        val durationNs = v.getDurationNanos(from, to, velocity)
        var max = 0f
        var min = 0f
        var t = 0L
        while (t <= durationNs) {
            val x = v.getValueFromNanos(t, from, to, velocity).value
            max = maxOf(max, x)
            min = minOf(min, x)
            t += 1_000_000L
        }
        return Run(overshoot = max - 1f, undershoot = -min, durationMs = durationNs / 1_000_000L)
    }
}
