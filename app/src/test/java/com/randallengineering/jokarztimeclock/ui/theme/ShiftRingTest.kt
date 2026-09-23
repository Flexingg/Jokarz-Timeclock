package com.randallengineering.jokarztimeclock.ui.theme

import androidx.compose.material3.WavyProgressIndicatorDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftRingTest {

    private val hour = 3_600_000L
    private val standard = 9 * hour // 8.5h shift + 0.5h meal
    private val cliff = 12 * hour

    /** The formula the hand-drawn Canvas arc used before the wavy indicator replaced it. */
    private fun legacyProgress(elapsed: Long): Float = if (elapsed <= standard) {
        (elapsed.toFloat() / standard.toFloat()).coerceIn(0f, 1f)
    } else {
        ((elapsed - standard).toFloat() / (cliff - standard).toFloat()).coerceIn(0f, 1f)
    }

    @Test
    fun progressMatchesTheOldArcAcrossTheWholeShift() {
        var t = 0L
        while (t <= 14 * hour) {
            assertEquals("at ${t / 60_000} min", legacyProgress(t), ShiftRing.progress(t, standard, cliff), 0f)
            t += 5 * 60_000L
        }
    }

    @Test
    fun progressFillsTheStandardShiftThenRestartsForTheBankingSegment() {
        assertEquals(0f, ShiftRing.progress(0L, standard, cliff), 0f)
        assertEquals(0.5f, ShiftRing.progress(standard / 2, standard, cliff), 1e-6f)
        assertEquals(1f, ShiftRing.progress(standard, standard, cliff), 0f)
        assertEquals(0f, ShiftRing.progress(standard + 1, standard, cliff), 1e-6f)
        assertEquals(0.5f, ShiftRing.progress(standard + (cliff - standard) / 2, standard, cliff), 1e-6f)
        assertEquals(1f, ShiftRing.progress(cliff, standard, cliff), 0f)
        assertEquals(1f, ShiftRing.progress(cliff + 5 * hour, standard, cliff), 0f)
    }

    @Test
    fun degenerateTargetsNeverProduceNaN() {
        // Cliff at or below the standard target, or a zero-length standard shift.
        for (p in listOf(
            ShiftRing.progress(10 * hour, standard, standard),
            ShiftRing.progress(10 * hour, standard, 8 * hour),
            ShiftRing.progress(0L, 0L, cliff),
            ShiftRing.progress(hour, 0L, 0L)
        )) {
            assertTrue("progress $p", !p.isNaN() && p in 0f..1f)
        }
    }

    @Test
    fun colourTierKeepsItsThresholds() {
        assertEquals(ShiftRing.Tier.STANDARD, ShiftRing.tier(0L, standard, cliff))
        assertEquals(ShiftRing.Tier.STANDARD, ShiftRing.tier(standard - 1, standard, cliff))
        assertEquals(ShiftRing.Tier.BANKING, ShiftRing.tier(standard, standard, cliff))
        assertEquals(ShiftRing.Tier.BANKING, ShiftRing.tier(cliff - 1, standard, cliff))
        assertEquals(ShiftRing.Tier.CLIFF, ShiftRing.tier(cliff, standard, cliff))
        assertEquals(ShiftRing.Tier.CLIFF, ShiftRing.tier(cliff + 3 * hour, standard, cliff))
    }

    @Test
    fun reducedMotionFlattensTheWave() {
        val flat = ShiftRing.amplitude(reducedMotion = true)
        for (i in 0..100) assertEquals(0f, flat(i / 100f), 0f)
        // One shared instance, so the indicator is not invalidated by a fresh lambda every second.
        assertSame(flat, ShiftRing.amplitude(reducedMotion = true))
    }

    @Test
    fun normalMotionUsesTheOfficialAmplitude() {
        val wavy = ShiftRing.amplitude(reducedMotion = false)
        assertSame(WavyProgressIndicatorDefaults.indicatorAmplitude, wavy)
        // Mid-shift the ring actually wiggles.
        assertTrue(wavy(0.5f) > 0f)
    }
}
