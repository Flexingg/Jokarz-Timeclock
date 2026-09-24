package com.randallengineering.jokarztimeclock.engine

import com.randallengineering.jokarztimeclock.data.models.Session
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftFilterTest {

    private val chicago: TimeZone = TimeZone.getTimeZone("America/Chicago")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        Calendar.getInstance(chicago).apply { clear(); set(y, m - 1, d, h, min, 0) }.timeInMillis

    private fun shift(y: Int, m: Int, d: Int, fromH: Int, hours: Int, note: String = "", job: String = "") =
        Session(id = "s$d-$fromH", start = at(y, m, d, fromH), end = at(y, m, d, fromH) + hours * 3_600_000L, note = note, jobCode = job)

    private val sessions = listOf(
        shift(2026, 9, 1, 6, 10, note = "Transfer Dock"),
        shift(2026, 9, 3, 22, 8, note = "night cover"), // overnight into the 4th
        shift(2026, 9, 7, 6, 11, job = "JK-114"),
        shift(2026, 9, 14, 6, 10, note = "Auto Clock Out via Geofence")
    )

    @Test
    fun noCriteriaKeepsEverythingNewestFirstWithOriginalIndices() {
        val all = ShiftFilter.apply(sessions, ShiftFilter.Criteria())
        assertEquals(listOf(3, 2, 1, 0), all.map { it.index })
        assertFalse(ShiftFilter.Criteria().isActive)
    }

    @Test
    fun dayRangeIsInclusiveOfBothDaysAndHalfOpenInInstants() {
        val (from, to) = ShiftFilter.dayRange(at(2026, 9, 3, 12), at(2026, 9, 7, 9), chicago)
        assertEquals(at(2026, 9, 3, 0), from)
        assertEquals(at(2026, 9, 8, 0), to)
        val hit = ShiftFilter.apply(sessions, ShiftFilter.Criteria(from, to))
        assertEquals(listOf(2, 1), hit.map { it.index })
        // Reversed arguments give the same range.
        assertEquals(from to to, ShiftFilter.dayRange(at(2026, 9, 7, 9), at(2026, 9, 3, 12), chicago))
    }

    @Test
    fun anOvernightShiftBelongsToTheDayItStarted() {
        val third = ShiftFilter.dayRange(at(2026, 9, 3, 1), at(2026, 9, 3, 1), chicago)
        val fourth = ShiftFilter.dayRange(at(2026, 9, 4, 1), at(2026, 9, 4, 1), chicago)
        assertEquals(listOf(1), ShiftFilter.apply(sessions, ShiftFilter.Criteria(third.first, third.second)).map { it.index })
        assertTrue(ShiftFilter.apply(sessions, ShiftFilter.Criteria(fourth.first, fourth.second)).isEmpty())
    }

    @Test
    fun textSearchMatchesNoteOrJobCodeIgnoringCase() {
        assertEquals(listOf(0), ShiftFilter.apply(sessions, ShiftFilter.Criteria(query = "transfer")).map { it.index })
        assertEquals(listOf(2), ShiftFilter.apply(sessions, ShiftFilter.Criteria(query = " jk-114 ")).map { it.index })
        assertTrue(ShiftFilter.apply(sessions, ShiftFilter.Criteria(query = "nothing like this")).isEmpty())
        assertTrue(ShiftFilter.Criteria(query = "x").isActive)
        // Blank text is no filter at all.
        assertEquals(4, ShiftFilter.apply(sessions, ShiftFilter.Criteria(query = "   ")).size)
    }

    @Test
    fun rangeAndTextCombine() {
        val (from, to) = ShiftFilter.dayRange(at(2026, 9, 1, 0), at(2026, 9, 30, 0), chicago)
        val hit = ShiftFilter.apply(sessions, ShiftFilter.Criteria(from, to, query = "geofence"))
        assertEquals(listOf(3), hit.map { it.index })
    }

    @Test
    fun totalsSumTheMatchingShifts() {
        val t = ShiftFilter.totals(ShiftFilter.apply(sessions, ShiftFilter.Criteria()))
        assertEquals(4, t.count)
        assertEquals((10 + 8 + 11 + 10) * 3_600_000L, t.clockedMs)
        assertEquals(ShiftFilter.Totals(0, 0L), ShiftFilter.totals(emptyList()))
    }

    @Test
    fun lastDaysCountsTodayAndIsDstSafe() {
        // 2026-11-01 is the US fall-back day: the range still spans whole local days.
        val (from, to) = ShiftFilter.lastDays(7, at(2026, 11, 3, 15), chicago)
        assertEquals(at(2026, 10, 28, 0), from)
        assertEquals(at(2026, 11, 4, 0), to)
        // 7 calendar days, one of them 25 h long.
        assertEquals((7 * 24 + 1) * 3_600_000L, to - from)
    }

    @Test
    fun pickerDatesAreReadAsCalendarDatesNotUtcInstants() {
        // DateRangePicker reports UTC midnight; in Chicago that instant is the previous evening.
        val utc = TimeZone.getTimeZone("UTC")
        fun utcMidnight(y: Int, m: Int, d: Int) = Calendar.getInstance(utc).apply { clear(); set(y, m - 1, d) }.timeInMillis
        val (from, to) = ShiftFilter.fromPicker(utcMidnight(2026, 9, 3), utcMidnight(2026, 9, 7), chicago)
        assertEquals(at(2026, 9, 3, 0), from)
        assertEquals(at(2026, 9, 8, 0), to)
        // No end date yet = that single day.
        val single = ShiftFilter.fromPicker(utcMidnight(2026, 9, 14), null, chicago)
        assertEquals(at(2026, 9, 14, 0) to at(2026, 9, 15, 0), single)
    }
}
