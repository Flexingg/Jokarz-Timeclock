package com.randallengineering.jokarztimeclock.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app-side refresh may never be faster than once per wall-clock minute. */
class LiveRefreshCadenceTest {

    private val minute = LiveRefreshCadence.MIN_REFRESH_INTERVAL_MS

    @Test
    fun minimumIntervalIsOneMinute() {
        assertEquals(60_000L, LiveRefreshCadence.MIN_REFRESH_INTERVAL_MS)
    }

    @Test
    fun perSecondIntervalsAreRejected() {
        assertFalse(LiveRefreshCadence.isAllowedRefreshInterval(1_000L))
        assertFalse(LiveRefreshCadence.isAllowedRefreshInterval(5_000L))
        assertFalse(LiveRefreshCadence.isAllowedRefreshInterval(59_999L))
        assertTrue(LiveRefreshCadence.isAllowedRefreshInterval(60_000L))
        assertTrue(LiveRefreshCadence.isAllowedRefreshInterval(300_000L))
    }

    @Test
    fun nextMinuteBoundaryIsStrictlyAfterNowAndOnTheMinute() {
        val t = 1_790_000_040_000L // a whole minute
        assertEquals(t + minute, LiveRefreshCadence.nextMinuteBoundaryMs(t))
        assertEquals(t + minute, LiveRefreshCadence.nextMinuteBoundaryMs(t + 1))
        assertEquals(t + minute, LiveRefreshCadence.nextMinuteBoundaryMs(t + 59_999))
        assertEquals(0L, LiveRefreshCadence.nextMinuteBoundaryMs(t + 12_345) % minute)
    }

    @Test
    fun delayLandsOnTheBoundaryAndNeverNearZero() {
        val t = 1_790_000_040_000L
        assertEquals(minute, LiveRefreshCadence.delayUntilNextMinuteBoundary(t))
        assertEquals(47_655L, LiveRefreshCadence.delayUntilNextMinuteBoundary(t + 12_345))
        // A wakeup a hair before a boundary skips to the next one instead of posting twice.
        assertEquals(minute + 500L, LiveRefreshCadence.delayUntilNextMinuteBoundary(t + 59_500))
    }

    @Test
    fun wakesAtTheClockOutInstantWhenItComesBeforeTheBoundary() {
        val t = 1_790_000_040_000L
        assertEquals(20_000L, LiveRefreshCadence.nextWakeDelayMs(t + 10_000, t + 30_000))
        // Target already past or absent: plain minute boundary.
        assertEquals(50_000L, LiveRefreshCadence.nextWakeDelayMs(t + 10_000, t))
        assertEquals(50_000L, LiveRefreshCadence.nextWakeDelayMs(t + 10_000, null))
    }

    /** Simulate a whole 14h shift: at most one wakeup per wall-clock minute, plus one at clock-out. */
    @Test
    fun aWholeShiftWakesAboutOncePerMinuteNeverPerSecond() {
        val start = 1_790_000_040_000L + 23_456L
        val target = start + (10.5 * 3_600_000).toLong()
        val end = start + 14 * 3_600_000L
        var now = start
        var wakes = 0
        val minutesSeen = HashSet<Long>()
        while (now < end) {
            val d = LiveRefreshCadence.nextWakeDelayMs(now, target)
            assertTrue("delay must be positive", d > 0)
            now += d
            // The last iteration lands after the shift has ended; only wakeups inside it count.
            if (now <= end) wakes++
            if (now != target) {
                assertEquals("non-target wakeups land on a minute boundary", 0L, now % minute)
                assertTrue("two wakeups in the same minute", minutesSeen.add(now / minute))
            }
        }
        // 14h = 840 minute boundaries, plus the one wakeup at clock-out.
        assertEquals(14 * 60 + 1, wakes)
    }
}
