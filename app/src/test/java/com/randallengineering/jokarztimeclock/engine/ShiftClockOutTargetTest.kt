package com.randallengineering.jokarztimeclock.engine

import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The clock-out target behind the notification countdown. Uses the shipped defaults (10.0h
 * standard, 0.5h unpaid meal, auto break deduction on, 12.5h cliff) and plant-zone wall times.
 * Week of Mon 2026-09-21.
 */
class ShiftClockOutTargetTest {

    private val ny = TimeZone.getTimeZone("America/New_York")
    private lateinit var previous: TimeZone
    // Field init runs before @Before pins the default zone, so the parser must carry the
    // plant zone itself: otherwise the wall-clock strings parse in the build machine's zone
    // and every expectation shifts (NY is UTC-4/5, CI runs UTC).
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = ny }

    @Before
    fun setUp() {
        previous = TimeZone.getDefault()
        TimeZone.setDefault(ny)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(previous)
    }

    private fun at(text: String): Long = sdf.parse(text)!!.time

    private fun clockedIn(start: String, sessions: List<Session> = emptyList(), settings: AppSettings = AppSettings()) =
        TimeclockState(isClockedIn = true, currentSessionStart = at(start), sessions = sessions, settings = settings)

    @Test
    fun mondayDefaultSettingsFromTenPastFiveIsTwentyToFour() {
        val state = clockedIn("2026-09-21 05:10")
        assertEquals(at("2026-09-21 15:40"), ShiftClockOutTarget.clockOutAtMs(state, at("2026-09-21 06:00")))
    }

    @Test
    fun withoutAutoBreakDeductionTheMealIsNotAdded() {
        val state = clockedIn("2026-09-21 05:10", settings = AppSettings(autoBreakDeduction = false))
        assertEquals(at("2026-09-21 15:10"), ShiftClockOutTarget.clockOutAtMs(state, at("2026-09-21 06:00")))
    }

    @Test
    fun hoursBankedEarlierInTheWeekPullTheTargetEarlier() {
        // Mon 05:10-17:10 = 12h clocked (under the 12.5h cliff): 11.5h worked, +1.5h banked.
        val monday = Session(start = at("2026-09-21 05:10"), end = at("2026-09-21 17:10"))
        val state = clockedIn("2026-09-22 05:10", sessions = listOf(monday))
        assertEquals(at("2026-09-22 14:10"), ShiftClockOutTarget.clockOutAtMs(state, at("2026-09-22 06:00")))
    }

    @Test
    fun aDeficitEarlierInTheWeekPushesTheTargetLater() {
        // Mon 05:10-14:10 = 9h clocked: 8.5h worked, -1.5h banked.
        val monday = Session(start = at("2026-09-21 05:10"), end = at("2026-09-21 14:10"))
        val state = clockedIn("2026-09-22 05:10", sessions = listOf(monday))
        assertEquals(at("2026-09-22 17:10"), ShiftClockOutTarget.clockOutAtMs(state, at("2026-09-22 06:00")))
    }

    /** Same formula as GoogleClockHero's "Standard Shift: … remaining" pill, so the two cannot disagree. */
    @Test
    fun matchesTheHeroStandardShiftPill() {
        val monday = Session(start = at("2026-09-21 05:03"), end = at("2026-09-21 16:47"))
        val state = clockedIn("2026-09-23 04:58", sessions = listOf(monday))
        val startMs = state.currentSessionStart!!
        val s = state.settings
        val prevBanked = PayrollEngine.getPreviousBankedHoursForCurrentWeek(startMs, state)
        val heroStandardMs = (((s.standardShiftHours + s.unpaidMealDuration) - prevBanked) * 3600000.0).toLong()
        assertEquals(startMs + heroStandardMs, ShiftClockOutTarget.clockOutAtMs(state, startMs))
    }

    /** Fri-Sun: documented as the plain standard length from the start, with no bank adjustment. */
    @Test
    fun fridayAndSundayUseThePlainStandardTargetIgnoringTheBank() {
        val monday = Session(start = at("2026-09-21 05:10"), end = at("2026-09-21 17:10")) // +1.5h bank
        val friday = clockedIn("2026-09-25 05:10", sessions = listOf(monday))
        assertEquals(at("2026-09-25 15:40"), ShiftClockOutTarget.clockOutAtMs(friday, at("2026-09-25 06:00")))
        val sunday = clockedIn("2026-09-27 05:10", sessions = listOf(monday))
        assertEquals(at("2026-09-27 15:40"), ShiftClockOutTarget.clockOutAtMs(sunday, at("2026-09-27 06:00")))
    }

    @Test
    fun nullWhenNotClockedIn() {
        assertNull(ShiftClockOutTarget.clockOutAtMs(TimeclockState(), at("2026-09-21 06:00")))
        // A stale start with isClockedIn = false is still "not clocked in".
        val stale = TimeclockState(isClockedIn = false, currentSessionStart = at("2026-09-21 05:10"))
        assertNull(ShiftClockOutTarget.clockOutAtMs(stale, at("2026-09-21 06:00")))
        assertFalse(ShiftClockOutTarget.targetIsAlreadyPast(stale, at("2026-09-21 23:00")))
    }

    @Test
    fun pastTargetDetection() {
        val state = clockedIn("2026-09-21 05:10")
        assertFalse(ShiftClockOutTarget.targetIsAlreadyPast(state, at("2026-09-21 15:39")))
        assertTrue(ShiftClockOutTarget.targetIsAlreadyPast(state, at("2026-09-21 15:40")))
        assertTrue(ShiftClockOutTarget.targetIsAlreadyPast(state, at("2026-09-21 18:00")))
    }

    @Test
    fun breaksDoNotMoveTheTarget() {
        val state = clockedIn("2026-09-21 05:10").copy(accumulatedBreakMs = 30 * 60_000L)
        assertEquals(at("2026-09-21 15:40"), ShiftClockOutTarget.clockOutAtMs(state, at("2026-09-21 12:00")))
    }
}
