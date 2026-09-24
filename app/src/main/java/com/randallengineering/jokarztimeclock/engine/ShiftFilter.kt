package com.randallengineering.jokarztimeclock.engine

import com.randallengineering.jokarztimeclock.data.models.Session
import java.util.TimeZone

/**
 * Search / filter for the shift history: a date range plus free text. Pure JVM (no Android), so it is
 * unit tested; the list only renders what [apply] returns.
 *
 * Attribution: a shift belongs to the local day it STARTED on (the same rule the day stats use), so
 * an overnight shift 22:00 → 06:00 appears under the evening it began, never twice. Ranges are
 * half-open instants `[fromMs, toMs)` built from whole local days with [ShiftTimeMath.addDays], so a
 * DST change inside the range cannot drop or double an hour.
 */
object ShiftFilter {

    /** The quick ranges offered above the list. */
    enum class Preset(val label: String) {
        ALL("All"),
        THIS_WEEK("This week"),
        PAY_PERIOD("Pay period"),
        LAST_30_DAYS("30 days"),
        CUSTOM("Custom")
    }

    /** What to show. `null` bounds are open; [query] matches the note or job code, case-insensitively. */
    data class Criteria(
        val fromMs: Long? = null,
        val toMs: Long? = null,
        val query: String = ""
    ) {
        val isActive: Boolean get() = fromMs != null || toMs != null || query.isNotBlank()
    }

    /** Count and clocked time of the shifts that match, shown as the filter's running total. */
    data class Totals(val count: Int, val clockedMs: Long)

    fun matches(session: Session, criteria: Criteria): Boolean {
        if (criteria.fromMs != null && session.start < criteria.fromMs) return false
        if (criteria.toMs != null && session.start >= criteria.toMs) return false
        val q = criteria.query.trim()
        if (q.isEmpty()) return true
        return session.note.contains(q, ignoreCase = true) || session.jobCode.contains(q, ignoreCase = true)
    }

    /**
     * The matching shifts, newest first, each paired with its index in [sessions] (the edit / delete
     * calls address a shift by that index, so filtering must never renumber them).
     */
    fun apply(sessions: List<Session>, criteria: Criteria): List<IndexedValue<Session>> =
        sessions.withIndex()
            .filter { matches(it.value, criteria) }
            .sortedByDescending { it.value.start }

    fun totals(matching: List<IndexedValue<Session>>): Totals =
        Totals(matching.size, matching.sumOf { ShiftTimeMath.durationMs(it.value.start, it.value.end) })

    /**
     * Whole local days [firstDayMs] … [lastDayMs] (any instant inside each; order does not matter) as
     * the half-open range `[start of the first day, start of the day after the last)`.
     */
    fun dayRange(firstDayMs: Long, lastDayMs: Long, tz: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val a = ShiftTimeMath.startOfDayMs(minOf(firstDayMs, lastDayMs), tz)
        val b = ShiftTimeMath.startOfDayMs(maxOf(firstDayMs, lastDayMs), tz)
        return a to ShiftTimeMath.addDays(b, 1, tz)
    }

    /** Today and the [days] − 1 days before it. */
    fun lastDays(days: Int, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        require(days >= 1) { "days must be >= 1" }
        return dayRange(ShiftTimeMath.addDays(nowMs, -(days - 1), tz), nowMs, tz)
    }

    /**
     * The range for a Material `DateRangePicker` selection, which reports each end as UTC-midnight
     * millis of the chosen calendar date (see [ShiftTimeMath.applyPickedDate]). A missing end date
     * means a single day.
     */
    fun fromPicker(startUtcDateMs: Long, endUtcDateMs: Long?, tz: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val first = ShiftTimeMath.applyPickedDate(0L, startUtcDateMs, tz)
        val last = ShiftTimeMath.applyPickedDate(0L, endUtcDateMs ?: startUtcDateMs, tz)
        return dayRange(first, last, tz)
    }
}
