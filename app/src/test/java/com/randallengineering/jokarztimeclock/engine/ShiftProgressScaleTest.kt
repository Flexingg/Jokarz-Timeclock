package com.randallengineering.jokarztimeclock.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the single "you are here" progress point and the bar fill of the live notification. */
class ShiftProgressScaleTest {

    private val max = ShiftProgressScale.MAX_MINUTES

    private fun minutes(m: Long) = m * 60_000L
    private fun plan(elapsedMs: Long) = ShiftProgressScale.plan(elapsedMs, targetHours = 10.5, cliffHours = 12.5)

    @Test
    fun beforeShift_pointAndFillAtLeftEnd() {
        val p = plan(-5_000L)
        assertEquals(0, p.pointMark)
        assertEquals(0, p.barFill)
        assertEquals(0, ShiftProgressScale.pointMark(-minutes(30), 10.5))
    }

    @Test
    fun atShiftStart_pointAtLeftEnd() {
        assertEquals(0, ShiftProgressScale.pointMark(0L, 10.5))
        assertEquals(0, plan(0L).pointMark)
    }

    @Test
    fun midShift_pointIsElapsedMinutes() {
        val p = plan(minutes(5 * 60))
        assertEquals(300, p.pointMark)
        assertTrue(p.pointMark > 0)
        assertTrue(p.pointMark < p.targetMark)
        assertEquals(p.pointMark, p.barFill)
    }

    @Test
    fun atClockOutTarget_pointPinnedAndLineFull() {
        val p = plan(minutes(630))
        assertEquals(max, p.pointMark)
        assertEquals(max, p.barFill)
    }

    @Test
    fun oneMinuteBeforeTarget_stillShortOfEnd() {
        val p = plan(minutes(629))
        assertEquals(p.targetMark - 1, p.pointMark)
        assertTrue(p.pointMark < max)
        assertEquals(p.progress, p.barFill)
    }

    @Test
    fun inOvertime_pointPinnedAndLineFull() {
        val p = plan(minutes(11 * 60))
        assertEquals(max, p.pointMark)
        assertEquals(max, p.barFill)
        // progress keeps its old meaning: the clamped elapsed minutes.
        assertEquals(660, p.progress)
    }

    @Test
    fun pointNeverMovesBackwards() {
        val elapsed = listOf(-60L, 0L, 1L, 30L, 300L, 629L, 630L, 631L, 750L, 765L, 900L, 1_440L).map(::minutes)
        val points = elapsed.map { ShiftProgressScale.pointMark(it, 10.5) }
        points.zipWithNext().forEach { (a, b) -> assertTrue("point went backwards: $points", b >= a) }
    }

    @Test
    fun existingInvariantsStillHold() {
        val early = plan(minutes(60))
        assertEquals(listOf(max), early.segmentLengths)
        assertEquals(max, early.segmentLengths.sum())
        assertEquals(60, early.progress)

        val over = ShiftProgressScale.plan(minutes(20 * 60), targetHours = 14.0, cliffHours = 16.0)
        assertEquals(max, over.segmentLengths.sum())
        assertEquals(max, over.progress)
        assertEquals(630, early.targetMark)
        assertEquals(750, early.cliffMark)
    }

    @Test
    fun pointMovesLeftToRight() {
        val start = plan(0L).pointMark
        val mid = plan(minutes(5 * 60)).pointMark
        assertTrue(start < mid)
        assertTrue(mid < max)
    }

    @Test
    fun pureFunctionAgreesWithPlan() {
        listOf(-1L, 0L, 300L, 629L, 630L, 660L).map(::minutes).forEach {
            assertEquals(ShiftProgressScale.pointMark(it, 10.5), plan(it).pointMark)
        }
    }
}
