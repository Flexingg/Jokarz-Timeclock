package com.randallengineering.jokarztimeclock.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Covers the date-editing contract: absolute-instant storage, midnight crossing, validation and
 * DST behaviour. All times below are wall-clock times in America/New_York (the plant's zone),
 * pinned with [TimeZone.setDefault] so the assertions do not depend on the build machine.
 *
 * DST dates used: spring forward Sun 2026-03-08 (02:00 -> 03:00), fall back Sun 2026-11-01.
 */
class ShiftTimeMathTest {

    private val ny = TimeZone.getTimeZone("America/New_York")
    private lateinit var previous: TimeZone
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

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

    // ------------------------------------------------------------------ midnight crossing

    @Test
    fun overnightShiftDurationIsEightHoursNotNegative() {
        val start = at("2026-03-02 22:00") // Monday night
        val end = at("2026-03-03 06:00")   // Tuesday morning
        assertEquals(8 * ShiftTimeMath.MS_PER_HOUR, ShiftTimeMath.durationMs(start, end))
        assertTrue(ShiftTimeMath.isOvernight(start, end))
        assertTrue(ShiftTimeMath.isValid(start, end))
    }

    @Test
    fun overnightShiftEndOnFollowingDayIsKeptWhenRevalidating() {
        // Guards the classic bug: comparing only the time-of-day would call 22:00 -> 06:00 invalid.
        val start = at("2026-03-02 22:00")
        val end = at("2026-03-03 06:00")
        assertTrue(end > start)
        assertEquals(ShiftTimeMath.Validation.OK, ShiftTimeMath.validate(start, end))
        assertEquals(0L, ShiftTimeMath.dayIndex(start) - (ShiftTimeMath.dayIndex(end) - 1))
    }

    @Test
    fun nextDaySameTimeMovesStopToTheFollowingDateKeepingWallClock() {
        val end = at("2026-03-03 06:00")
        val moved = ShiftTimeMath.nextDaySameTime(end)
        assertEquals("2026-03-04 06:00", sdf.format(moved))
    }

    // ------------------------------------------------------------------ validation

    @Test
    fun stopBeforeStartIsRejected() {
        val start = at("2026-03-02 06:00")
        val end = at("2026-03-02 05:00")
        assertEquals(ShiftTimeMath.Validation.END_NOT_AFTER_START, ShiftTimeMath.validate(start, end))
        assertFalse(ShiftTimeMath.isValid(start, end))
        assertTrue(ShiftTimeMath.errorMessage(ShiftTimeMath.validate(start, end))!!.isNotEmpty())
    }

    @Test
    fun zeroLengthShiftIsRejected() {
        val start = at("2026-03-02 06:00")
        assertEquals(ShiftTimeMath.Validation.END_NOT_AFTER_START, ShiftTimeMath.validate(start, start))
    }

    @Test
    fun shiftLongerThanADayIsRejected() {
        val start = at("2026-03-02 06:00")
        val end = at("2026-03-04 06:00") // 48h
        assertEquals(ShiftTimeMath.Validation.TOO_LONG, ShiftTimeMath.validate(start, end))
    }

    // ------------------------------------------------------------------ picker round-trips

    @Test
    fun applyPickedDateKeepsWallClockTimeAndMovesTheDate() {
        val original = at("2026-03-02 06:00")
        val picked = ShiftTimeMath.startOfDayMs(at("2026-03-05 13:00")) // DatePicker gives UTC midnight of the day
        val utcMidnightOfDay = utcMidnight("2026-03-05")
        val result = ShiftTimeMath.applyPickedDate(original, utcMidnightOfDay)
        assertEquals("2026-03-05 06:00", sdf.format(result))
        assertNotEquals(picked, result) // time-of-day preserved, not snapped to midnight
    }

    @Test
    fun applyPickedTimeKeepsTheDateAndClearsSubMinutePrecision() {
        val start = at("2026-03-02 06:00") + 37_000L
        val shifted = ShiftTimeMath.applyPickedTime(start, 22, 15)
        assertEquals("2026-03-02 22:15", sdf.format(shifted))
        assertEquals(0L, shifted % ShiftTimeMath.MS_PER_MINUTE)
    }

    @Test
    fun pickerDateRoundTripsToTheSameLocalDay() {
        val instant = at("2026-03-05 22:30")
        val pickerValue = ShiftTimeMath.toPickerDateMs(instant)

        val utcFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        assertEquals("2026-03-05 00:00", utcFormat.format(Date(pickerValue)))
        assertEquals("2026-03-05 22:30", sdf.format(Date(ShiftTimeMath.applyPickedDate(instant, pickerValue))))
    }

    @Test
    fun stopCanBeMovedToTheNextDateAndRemainsValid() {
        var start = at("2026-03-02 22:00")
        var end = at("2026-03-03 06:00")

        // Owner edits the START date only -> keep the 8h length on the new date.
        val duration = ShiftTimeMath.durationMs(start, end)
        start = ShiftTimeMath.applyPickedDate(start, utcMidnight("2026-03-09"))
        end = start + duration
        assertEquals("2026-03-09 22:00", sdf.format(start))
        assertEquals("2026-03-10 06:00", sdf.format(end))
        assertEquals(8 * ShiftTimeMath.MS_PER_HOUR, ShiftTimeMath.durationMs(start, end))

        // Owner edits the STOP time only, on the following night -> 06:00 next day.
        val newEnd = ShiftTimeMath.applyPickedTime(start, 6, 30)
        val rolled = ShiftTimeMath.nextDaySameTime(newEnd)
        assertEquals("2026-03-10 06:30", sdf.format(rolled))
        assertTrue(ShiftTimeMath.isValid(start, rolled))
    }

    // ------------------------------------------------------------------ DST

    @Test
    fun springForwardDoesNotInventAPhantomHour() {
        // Wall clock 01:00 -> 03:00 on the spring-forward night is only 1 hour of real time.
        val start = at("2026-03-08 01:00")
        val end = at("2026-03-08 03:00")
        assertEquals(1 * ShiftTimeMath.MS_PER_HOUR, ShiftTimeMath.durationMs(start, end))
        assertEquals(1.0, (ShiftTimeMath.durationMs(start, end) / 3_600_000.0), 0.0001)
    }

    @Test
    fun fallBackCountsTheRepeatedHourOnce() {
        // Wall clock 00:30 -> 02:30 on the fall-back night spans the repeated 01:00 hour: 3 real hours.
        val start = at("2026-11-01 00:30")
        val end = at("2026-11-01 02:30")
        assertEquals(3 * ShiftTimeMath.MS_PER_HOUR, ShiftTimeMath.durationMs(start, end))
    }

    @Test
    fun addDaysKeepsLocalMidnightAcrossDst() {
        var day = ShiftTimeMath.startOfDayMs(at("2026-03-02 06:00"))
        repeat(10) { day = ShiftTimeMath.addDays(day, 1) }
        // 10 calendar days after Mon 2026-03-02 is Thu 2026-03-12, still exactly local midnight.
        assertEquals("2026-03-12 00:00", sdf.format(day))
        assertEquals(day, ShiftTimeMath.startOfDayMs(day))
    }

    @Test
    fun dayBucketAfterDstIsTwentyFourRealHours() {
        val dayAfterDst = ShiftTimeMath.addDays(ShiftTimeMath.startOfDayMs(at("2026-03-08 12:00")), 1)
        val end = ShiftTimeMath.endOfDayMs(dayAfterDst)
        assertEquals(24 * ShiftTimeMath.MS_PER_HOUR, end - dayAfterDst)
        assertTrue(ShiftTimeMath.isInDayOf(dayAfterDst, dayAfterDst + 45 * ShiftTimeMath.MS_PER_MINUTE))
    }

    /** UTC midnight of a local calendar date, exactly as Material3's DatePicker returns it. */
    private fun utcMidnight(localDate: String): Long {
        val local = sdf.parse("$localDate 12:00")!!
        val cal = Calendar.getInstance(ny).apply { timeInMillis = local.time }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }
}
