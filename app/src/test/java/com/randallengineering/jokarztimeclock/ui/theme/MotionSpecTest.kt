package com.randallengineering.jokarztimeclock.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSpecTest {

    @Test
    fun stagePoseFollowsTheShiftState() {
        assertEquals(StagePose.READY, StagePose.of(isClockedIn = false, isOnBreak = false))
        assertEquals(StagePose.WORKING, StagePose.of(isClockedIn = true, isOnBreak = false))
        assertEquals(StagePose.BREAK, StagePose.of(isClockedIn = true, isOnBreak = true))
        // A stale break flag after clocking out must not leave the stage in its break pose.
        assertEquals(StagePose.READY, StagePose.of(isClockedIn = false, isOnBreak = true))
    }

    @Test
    fun everyStateChangeActuallyMovesTheStage() {
        val poses = StagePose.values()
        for (a in poses) for (b in poses) if (a != b) {
            val same = a.topStart == b.topStart && a.topEnd == b.topEnd &&
                a.bottomEnd == b.bottomEnd && a.bottomStart == b.bottomStart && a.waveAmplitude == b.waveAmplitude
            assertTrue("$a and $b are indistinguishable", !same)
        }
    }

    @Test
    fun confirmationStaysWithinBudgetAndEndsInvisible() {
        assertTrue(ConfirmationMotion.DURATION_MS < 600)
        assertEquals(0f, ConfirmationMotion.morphAt(0f), 0f)
        assertEquals(1f, ConfirmationMotion.morphAt(1f), 0f)
        assertEquals(1f, ConfirmationMotion.scaleAt(1f), 1e-6f)
        assertEquals(1f, ConfirmationMotion.alphaAt(0f), 0f)
        assertEquals(0f, ConfirmationMotion.alphaAt(1f), 1e-6f)
    }

    @Test
    fun confirmationTimelineIsMonotonic() {
        var morph = -1f
        var scale = -1f
        var alpha = 2f
        for (i in 0..100) {
            val t = i / 100f
            val m = ConfirmationMotion.morphAt(t)
            val s = ConfirmationMotion.scaleAt(t)
            val a = ConfirmationMotion.alphaAt(t)
            assertTrue(m >= morph && s >= scale && a <= alpha)
            assertTrue(m in 0f..1f && a in 0f..1f)
            morph = m; scale = s; alpha = a
        }
        // The shape has finished morphing while the badge is still fully visible.
        assertEquals(1f, ConfirmationMotion.morphAt(0.6f), 1e-6f)
        assertEquals(1f, ConfirmationMotion.alphaAt(0.6f), 0f)
    }
}
