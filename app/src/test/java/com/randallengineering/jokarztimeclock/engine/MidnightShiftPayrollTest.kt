package com.randallengineering.jokarztimeclock.engine

import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Payroll-level tests for date editing: overnight (midnight-crossing) entries, day/week
 * re-aggregation after an edit, and DST-safe day bucketing.
 *
 * Bucketing rule under test: a session belongs to the local calendar day of its **start**; its
 * full (absolute) duration counts there. Overnight shifts therefore land entirely on the start
 * day — this is the pre-existing payroll semantics (Mon–Thu 10h salary base + cliff) and is kept.
 */
class MidnightShiftPayrollTest {

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

    private fun dayStart(text: String): Long = PayrollEngine.getStartOfDay(Date(at(text)))

    /** Sums clocked milliseconds for the 7 local days of the week containing [anyDayInWeek]. */
    private fun weekClockedMs(anyDayInWeek: String, state: TimeclockState): Long {
        val weekStart = PayrollEngine.getStartOfWeekDate(Date(at(anyDayInWeek)))
        var total = 0L
        var day = weekStart
        repeat(7) {
            total += PayrollEngine.calculateDayStats(day, excludeActive = true, state = state).clockedMs
            day = ShiftTimeMath.addDays(day, 1)
        }
        return total
    }

    // ---------------------------------------------------------------- midnight crossing

    @Test
    fun overnightShiftCountsEightHoursOnItsStartDayAndNothingOnTheNextDay() {
        val start = at("2026-03-02 22:00") // Mon night
        val end = at("2026-03-03 06:00")   // Tue morning
        val state = TimeclockState(
            sessions = listOf(Session(start = start, end = end)),
            settings = AppSettings(autoBreakDeduction = false, standardShiftHours = 10.0)
        )

        val monday = PayrollEngine.calculateDayStats(dayStart("2026-03-02 06:00"), excludeActive = true, state = state)
        val tuesday = PayrollEngine.calculateDayStats(dayStart("2026-03-03 06:00"), excludeActive = true, state = state)

        assertEquals(8.0, monday.clockedHours, 0.001)
        assertEquals(0.0, tuesday.clockedHours, 0.001)
        assertEquals(8 * ShiftTimeMath.MS_PER_HOUR, monday.clockedMs)
    }

    @Test
    fun weekTotalContainsTheWholeOvernightShiftOnce() {
        val state = TimeclockState(
            sessions = listOf(Session(start = at("2026-03-02 22:00"), end = at("2026-03-03 06:00"))),
            settings = AppSettings(autoBreakDeduction = false)
        )
        assertEquals(8 * ShiftTimeMath.MS_PER_HOUR, weekClockedMs("2026-03-02 12:00", state))
    }

    @Test
    fun overnightShiftAcrossTheWeekBoundaryIsCountedInItsStartWeek() {
        // Sunday night into Monday: start day (Sunday) is in the previous week.
        val state = TimeclockState(
            sessions = listOf(Session(start = at("2026-03-08 22:00"), end = at("2026-03-09 06:00"))),
            settings = AppSettings(autoBreakDeduction = false)
        )
        assertEquals(8 * ShiftTimeMath.MS_PER_HOUR, weekClockedMs("2026-03-08 12:00", state))
        assertEquals(0L, weekClockedMs("2026-03-09 12:00", state))
    }

    // ---------------------------------------------------------------- re-aggregation after an edit

    @Test
    fun editingAShiftToAnotherDayMovesDayAndWeekTotals() {
        val original = Session(start = at("2026-03-02 06:00"), end = at("2026-03-02 16:30")) // Mon 10.5h
        val before = TimeclockState(sessions = listOf(original), settings = AppSettings(autoBreakDeduction = true))

        assertEquals(10.5, PayrollEngine.calculateDayStats(dayStart("2026-03-02 12:00"), true, before).clockedHours, 0.001)
        assertEquals(0.0, PayrollEngine.calculateDayStats(dayStart("2026-03-04 12:00"), true, before).clockedHours, 0.001)

        // The owner edits ONLY the start date (Wed) — the repository keeps the duration.
        val movedStart = ShiftTimeMath.applyPickedDate(
            original.start,
            utcMidnightOfLocalDate("2026-03-04")
        )
        val edited = original.copy(start = movedStart, end = movedStart + (original.end - original.start))
        val after = before.copy(sessions = listOf(edited))

        assertEquals("2026-03-04 06:00", sdf.format(edited.start))
        assertEquals(0.0, PayrollEngine.calculateDayStats(dayStart("2026-03-02 12:00"), true, after).clockedHours, 0.001)
        assertEquals(10.5, PayrollEngine.calculateDayStats(dayStart("2026-03-04 12:00"), true, after).clockedHours, 0.001)
        // Week total is unchanged by the move (same week), and equals the edited duration.
        assertEquals((10.5 * ShiftTimeMath.MS_PER_HOUR).toLong(), weekClockedMs("2026-03-04 12:00", after))
    }

    @Test
    fun editingTimesRecomputesTheRunningOverTimeForTheDay() {
        val session = Session(start = at("2026-08-24 06:00"), end = at("2026-08-24 16:30")) // 10.5h Mon
        val state = TimeclockState(sessions = listOf(session), settings = AppSettings(autoBreakDeduction = true, standardShiftHours = 10.0, cliffHours = 12.5))
        assertEquals(0.0, PayrollEngine.calculateDayStats(dayStart("2026-08-24 12:00"), true, state).otHours, 0.001)

        // Owner corrects the stop to 19:00 -> 13h -> over the 12.5h cliff.
        val corrected = session.copy(end = at("2026-08-24 19:00"))
        val after = state.copy(sessions = listOf(corrected))
        val stats = PayrollEngine.calculateDayStats(dayStart("2026-08-24 12:00"), true, after)
        assertEquals(13.0, stats.clockedHours, 0.001)
        assertEquals(2.5, stats.otHours, 0.001)
        assertEquals(2.5, PayrollEngine.calculateSessionOt(corrected, after), 0.001)
    }

    // ---------------------------------------------------------------- DST day bucketing

    @Test
    fun earlyMondayEntryAfterSpringForwardIsBucketedToMondayNotSunday() {
        // Mon 2026-03-09 00:40 -> 06:40 (6h clocked, 5.5h worked after the auto 30m meal).
        val state = TimeclockState(
            sessions = listOf(Session(start = at("2026-03-09 00:40"), end = at("2026-03-09 06:40"))),
            settings = AppSettings(autoBreakDeduction = true, standardShiftHours = 10.0)
        )

        val monday = PayrollEngine.calculateDayStats(dayStart("2026-03-09 12:00"), true, state)
        val sunday = PayrollEngine.calculateDayStats(dayStart("2026-03-08 12:00"), true, state)
        assertEquals(6.0, monday.clockedHours, 0.001)
        assertEquals(0.0, sunday.clockedHours, 0.001)
        assertEquals(-4.5, monday.bankedHours, 0.001)

        // Running sum across the week must use the same buckets: only Monday contributes bank.
        val target = at("2026-03-10 10:00")
        assertEquals(-4.5, PayrollEngine.getPreviousBankedHoursForCurrentWeek(target, state), 0.001)
    }

    @Test
    fun weeklyPeriodEndIsLocalSundayMidnightAcrossSpringForward() {
        val state = TimeclockState(settings = AppSettings(paySchedule = com.randallengineering.jokarztimeclock.data.models.PaySchedule.WEEKLY))
        val end = PayrollEngine.getEndOfPayPeriod(Date(at("2026-03-03 09:00")), state)
        val start = PayrollEngine.getStartOfWeekDate(Date(at("2026-03-03 09:00")))
        assertEquals("2026-03-08 23:59", sdf.format(end))
        assertEquals(ShiftTimeMath.addDays(start, 7) - 1L, end)
    }

    @Test
    fun localMidnightsStayOnMidnightAcrossSpringForward() {
        val from = PayrollEngine.getStartOfWeekDate(Date(at("2026-03-03 09:00"))) // Mon 2026-03-02
        val days = PayrollEngine.localMidnightsBetween(from, ShiftTimeMath.addDays(from, 9))

        assertEquals(
            listOf(
                "2026-03-02 00:00", "2026-03-03 00:00", "2026-03-04 00:00", "2026-03-05 00:00",
                "2026-03-06 00:00", "2026-03-07 00:00", "2026-03-08 00:00", "2026-03-09 00:00",
                "2026-03-10 00:00"
            ),
            days.map { sdf.format(it) }
        )
        // Every boundary really is local midnight (a drifted 01:00 boundary would fail this).
        days.forEach { assertEquals(it, ShiftTimeMath.startOfDayMs(it)) }
    }

    @Test
    fun localMidnightsStayOnMidnightAcrossFallBack() {
        val from = PayrollEngine.getStartOfWeekDate(Date(at("2026-10-28 09:00"))) // Mon 2026-10-26
        val days = PayrollEngine.localMidnightsBetween(from, ShiftTimeMath.addDays(from, 8))

        assertEquals(8, days.size)
        assertEquals("2026-10-26 00:00", sdf.format(days.first()))
        assertEquals("2026-11-02 00:00", sdf.format(days.last()))
        days.forEach { assertEquals(it, ShiftTimeMath.startOfDayMs(it)) }
    }

    /** UTC midnight of a local calendar date — the value Material3's DatePicker returns. */
    private fun utcMidnightOfLocalDate(localDate: String): Long {
        val cal = java.util.Calendar.getInstance(ny).apply { timeInMillis = at("$localDate 12:00") }
        return java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH),
                0, 0, 0
            )
        }.timeInMillis
    }
}
