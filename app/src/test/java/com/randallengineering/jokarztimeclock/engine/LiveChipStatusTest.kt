package com.randallengineering.jokarztimeclock.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the Live Update verdict rules and the fixed progress-bar scale. */
class LiveChipStatusTest {

    @Test
    fun belowAndroid16_chipNotSupported() {
        // Even if every other fact looked positive, the OS cannot draw a promoted chip.
        val v = LiveChipStatus.evaluate(35, canPostPromoted = true, isOngoing = true, promotable = true, promotedFlag = true)
        assertFalse(v.statusBarChipPossible)
        assertFalse(v.promoted)
        assertTrue(v.headline.contains("not supported"))
    }

    @Test
    fun android16_userSwitchedOffLiveUpdates() {
        val v = LiveChipStatus.evaluate(36, canPostPromoted = false, isOngoing = true, promotable = true, promotedFlag = false)
        assertFalse(v.statusBarChipPossible)
        assertFalse(v.promoted)
        assertTrue(v.headline.contains("switched off"))
    }

    @Test
    fun android16_promotedFlagSet_chipIsLive() {
        val v = LiveChipStatus.evaluate(36, canPostPromoted = true, isOngoing = true, promotable = true, promotedFlag = true)
        assertTrue(v.statusBarChipPossible)
        assertTrue(v.promoted)
        assertTrue(v.headline.contains("live"))
    }

    @Test
    fun android16_allowedButNotPromoted_blamesPhoneMakerHonestly() {
        val v = LiveChipStatus.evaluate(36, canPostPromoted = true, isOngoing = true, promotable = true, promotedFlag = false)
        assertTrue(v.statusBarChipPossible)
        assertFalse(v.promoted)
        assertTrue(v.detail.contains("phone makers only promote their own apps"))
        assertTrue(v.detail.contains("normal ongoing"))
    }

    @Test
    fun android16_notificationMissingRequirement_isReportedAsAppBug() {
        val v = LiveChipStatus.evaluate(36, canPostPromoted = true, isOngoing = true, promotable = false, promotedFlag = false)
        assertFalse(v.promoted)
        assertTrue(v.headline.contains("does not qualify"))
    }

    @Test
    fun android16_noNotificationPosted_saysSo() {
        val v = LiveChipStatus.evaluate(36, canPostPromoted = true, isOngoing = false, promotable = false, promotedFlag = false)
        assertTrue(v.statusBarChipPossible)
        assertFalse(v.promoted)
        assertTrue(v.headline.contains("No live shift notification"))
    }

    @Test
    fun progressScale_isFixedAndSegmentsSumToMax() {
        val early = ShiftProgressScale.plan(elapsedMs = 60 * 60_000L, targetHours = 10.5, cliffHours = 12.5)
        val late = ShiftProgressScale.plan(elapsedMs = 11 * 60 * 60_000L, targetHours = 10.5, cliffHours = 12.5)
        assertEquals(ShiftProgressScale.MAX_MINUTES, early.segmentLengths.sum())
        assertEquals(early.segmentLengths, late.segmentLengths)
        assertEquals(60, early.progress)
        assertEquals(660, late.progress)
        assertEquals(630, early.targetMark)
        assertEquals(750, early.cliffMark)
    }

    @Test
    fun progressScale_clampsProgressAndMarks() {
        val over = ShiftProgressScale.plan(elapsedMs = 20 * 60 * 60_000L, targetHours = 14.0, cliffHours = 16.0)
        assertEquals(ShiftProgressScale.MAX_MINUTES, over.progress)
        assertEquals(ShiftProgressScale.MAX_MINUTES, over.segmentLengths.sum())
        assertTrue(over.segmentLengths.all { it > 0 })

        val negative = ShiftProgressScale.plan(elapsedMs = -5_000L, targetHours = 10.5, cliffHours = 12.5)
        assertEquals(0, negative.progress)
    }
}
