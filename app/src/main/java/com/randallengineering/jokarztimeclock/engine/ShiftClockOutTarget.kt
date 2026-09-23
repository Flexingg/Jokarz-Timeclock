package com.randallengineering.jokarztimeclock.engine

import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import java.util.Calendar

/**
 * "When can I go home?" — the instant the standard (non-overtime) shift ends. Pure JVM, so it is
 * unit-testable; the live notification and the tests both call it, and nothing else in the
 * notification path does this arithmetic.
 *
 * ## Definition
 *
 * It is derived from the existing shift settings, not a new one, and matches the hero's
 * "Standard Shift: … remaining" pill (`GoogleClockHero.kt`) so the countdown in the notification
 * and the pill on screen cannot disagree:
 *
 * `target = sessionStart + (standardShiftHours + (unpaidMealDuration if autoBreakDeduction) - bank)`
 *
 *  - **Mon–Thu**: `bank` is [PayrollEngine.getPreviousBankedHoursForCurrentWeek] for the session
 *    start, exactly as the hero passes it. Hours banked earlier in the week pull the target
 *    earlier; a deficit pushes it later.
 *  - **Fri–Sun**: there is no standard segment — every worked hour is overtime — and the hero
 *    shows "Weekend OT" instead of a remaining pill. The banking rules only apply Mon–Thu, so the
 *    target is the plain standard length with **no bank adjustment**: `sessionStart + standard
 *    target`. It is a "normal day's length" marker, not an overtime boundary.
 *
 * Breaks do not move the target: like the hero and [PayrollEngine]'s Mon–Thu rules, the standard
 * shift is measured in clocked (wall) time from the session start, and the unpaid meal is already
 * part of the target length.
 *
 * The day that decides Mon–Thu vs Fri–Sun is the local day of the session **start**, the same
 * bucketing rule [PayrollEngine.calculateDayStats] uses.
 */
object ShiftClockOutTarget {

    /**
     * The instant he can go home, or null when not clocked in. [nowMs] does not move the target
     * (it is fixed by the start and the settings); it is taken so callers pass one consistent
     * "now" to this and [targetIsAlreadyPast].
     */
    @Suppress("UNUSED_PARAMETER")
    fun clockOutAtMs(state: TimeclockState, nowMs: Long): Long? {
        val start = state.currentSessionStart
        if (!state.isClockedIn || start == null) return null
        return start + ShiftTimeMath.hoursToMs(targetHours(state, start))
    }

    /** True once [nowMs] has reached the target, so the UI can say "past" instead of counting negative. */
    fun targetIsAlreadyPast(state: TimeclockState, nowMs: Long): Boolean {
        val target = clockOutAtMs(state, nowMs) ?: return false
        return nowMs >= target
    }

    /** Standard length in hours for a shift starting at [startMs], bank-adjusted Mon–Thu only. */
    private fun targetHours(state: TimeclockState, startMs: Long): Double {
        val settings = state.settings
        val meal = if (settings.autoBreakDeduction) settings.unpaidMealDuration else 0.0
        val standard = settings.standardShiftHours + meal
        return if (isMonThu(startMs)) {
            standard - PayrollEngine.getPreviousBankedHoursForCurrentWeek(startMs, state)
        } else {
            standard
        }
    }

    fun isMonThu(startMs: Long): Boolean =
        Calendar.getInstance().apply { timeInMillis = startMs }
            .get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.THURSDAY
}
