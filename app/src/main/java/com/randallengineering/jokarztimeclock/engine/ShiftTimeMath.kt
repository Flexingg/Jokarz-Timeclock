package com.randallengineering.jokarztimeclock.engine

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Pure-JVM time maths for shifts (no Android APIs, so it is unit-testable).
 *
 * ## Storage / timezone policy
 *
 * A [com.randallengineering.jokarztimeclock.data.models.Session] stores two **absolute instants**
 * (epoch milliseconds, i.e. UTC) plus a break duration. No wall-clock string is ever persisted.
 * Local wall-clock rendering happens only at the UI / notification layer using
 * [TimeZone.getDefault].
 *
 * Consequences that this file guarantees:
 *  - Duration is always `endInstant - startInstant`: an absolute, DST-proof quantity.
 *    A shift that starts 22:00 and ends 06:00 **the next day** is simply 8h — never negative,
 *    never zero.
 *  - DST transitions cannot create phantom hours. On a spring-forward night the wall clock jumps
 *    01:59 -> 03:00, so "01:00 to 03:00" is 1h of real elapsed time and that is exactly what is
 *    stored and paid. On a fall-back night "00:30 to 02:30" is 3h of real elapsed time.
 *  - Day / week bucketing uses [Calendar] day arithmetic ([addDays]) and never adds a raw
 *    86_400_000 ms step, which would drift by an hour across a DST transition and silently
 *    mis-bucket (or drop) entries.
 *
 * Editing a local wall-clock time that does not exist (the spring-forward gap) or that exists
 * twice (the fall-back overlap) is resolved by [Calendar] itself: a gap time rolls forward to the
 * first valid instant, an overlap time resolves to the first (DST) occurrence.
 */
object ShiftTimeMath {

    const val MS_PER_MINUTE = 60_000L
    const val MS_PER_HOUR = 3_600_000L
    const val MS_PER_DAY = 86_400_000L

    /** Longest single shift we accept when saving an edit. */
    const val MAX_SHIFT_MS = 24L * MS_PER_HOUR

    enum class Validation {
        OK,

        /** The stop instant is at or before the start instant. */
        END_NOT_AFTER_START,

        /** Shift longer than [MAX_SHIFT_MS]. */
        TOO_LONG
    }

    /**
     * Duration of a shift in milliseconds. Instants are absolute, so a stop on the following
     * calendar date simply yields a larger positive number. Degenerate input is clamped to 0 —
     * callers must reject it via [validate] before persisting.
     */
    fun durationMs(startMs: Long, endMs: Long): Long = (endMs - startMs).coerceAtLeast(0L)

    /** Validates a start/stop pair before it is written to storage. */
    fun validate(startMs: Long, endMs: Long): Validation = when {
        endMs <= startMs -> Validation.END_NOT_AFTER_START
        (endMs - startMs) > MAX_SHIFT_MS -> Validation.TOO_LONG
        else -> Validation.OK
    }

    fun isValid(startMs: Long, endMs: Long): Boolean = validate(startMs, endMs) == Validation.OK

    /** Human-readable inline error for a failed validation, or null when valid. */
    fun errorMessage(validation: Validation): String? = when (validation) {
        Validation.OK -> null
        Validation.END_NOT_AFTER_START ->
            "End must be after start — a shift cannot finish before it begins."
        Validation.TOO_LONG ->
            "That is more than 24 hours — check the dates on both ends."
    }

    // ---------------------------------------------------------------- calendar helpers

    /** Local midnight (start of day) containing [instantMs]. */
    fun startOfDayMs(instantMs: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        return Calendar.getInstance(tz).apply {
            timeInMillis = instantMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Local midnight after the day containing [instantMs]. */
    fun endOfDayMs(instantMs: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        addDays(startOfDayMs(instantMs, tz), 1, tz)

    /** True when [instantMs] is inside the local calendar day that starts at [dayStartMs]. */
    fun isInDayOf(dayStartMs: Long, instantMs: Long, tz: TimeZone = TimeZone.getDefault()): Boolean {
        val start = startOfDayMs(dayStartMs, tz)
        return instantMs >= start && instantMs < addDays(start, 1, tz)
    }

    /**
     * Adds whole calendar days. This is deliberately *not* `instantMs + days * 86_400_000`, which
     * would slide the wall-clock time by an hour whenever a DST transition is crossed.
     */
    fun addDays(instantMs: Long, days: Int, tz: TimeZone = TimeZone.getDefault()): Long {
        return Calendar.getInstance(tz).apply {
            timeInMillis = instantMs
            add(Calendar.DAY_OF_MONTH, days)
        }.timeInMillis
    }

    /** Local calendar day index (days since epoch) — used for bucketing/comparison. */
    fun dayIndex(instantMs: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        startOfDayMs(instantMs, tz) / MS_PER_DAY

    /** True when start and stop fall on different local calendar dates. */
    fun isOvernight(startMs: Long, endMs: Long, tz: TimeZone = TimeZone.getDefault()): Boolean =
        startOfDayMs(startMs, tz) != startOfDayMs(endMs, tz)

    // ---------------------------------------------------------------- editing helpers

    /**
     * Material3 `DatePicker` hands back the selected day as **UTC midnight** millis.
     * Re-anchor [anchorMs] onto that calendar date while keeping its local wall-clock time.
     */
    fun applyPickedDate(anchorMs: Long, pickedUtcDateMs: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtcDateMs }
        return Calendar.getInstance(tz).apply {
            timeInMillis = anchorMs
            // Set day first to avoid month-overflow normalisation when switching months.
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.YEAR, utc.get(Calendar.YEAR))
            set(Calendar.MONTH, utc.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    /**
     * The inverse of [applyPickedDate]: the UTC-midnight millis that `DatePicker` needs as its
     * initial selection for the local calendar date containing [localInstantMs].
     */
    fun toPickerDateMs(localInstantMs: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val local = Calendar.getInstance(tz).apply { timeInMillis = localInstantMs }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH),
                0, 0, 0
            )
        }.timeInMillis
    }

    /** Local wall-clock hour/minute applied to [anchorMs], seconds and millis zeroed. */
    fun applyPickedTime(
        anchorMs: Long,
        hour: Int,
        minute: Int,
        tz: TimeZone = TimeZone.getDefault()
    ): Long {
        return Calendar.getInstance(tz).apply {
            timeInMillis = anchorMs
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * The "overnight" shortcut: move a stop instant to the next local calendar day, same wall time.
     */
    fun nextDaySameTime(instantMs: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        addDays(instantMs, 1, tz)

    // ---------------------------------------------------------------- formatting

    /** `07:31:04`-style clock string (negative input renders as 00:00:00). */
    fun formatDurationClock(ms: Long): String {
        val totalSecs = (ms.coerceAtLeast(0L)) / 1000L
        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            totalSecs / 3600L,
            (totalSecs % 3600L) / 60L,
            totalSecs % 60L
        )
    }

    /** `8h 05m`-style compact duration. */
    fun formatDurationShort(ms: Long): String {
        val totalMins = ms.coerceAtLeast(0L) / MS_PER_MINUTE
        return "${totalMins / 60L}h ${totalMins % 60L}m"
    }
}
