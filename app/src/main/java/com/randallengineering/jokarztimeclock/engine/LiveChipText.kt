package com.randallengineering.jokarztimeclock.engine

import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the live shift notification shows, as plain values. Pure JVM, so the wording and the
 * timer choice are unit-tested; [LiveShiftService.buildNotification] only turns this into Android
 * objects.
 *
 * Content text shapes (short enough for a lockscreen line; the status bar capsule shows the timer):
 *  - `Elapsed 6h 41m • Out 3:40 PM`
 *  - `Elapsed 10h 45m • Past out 3:40 PM` (Mon–Thu banking buffer)
 *  - `Elapsed 13h 0m • OT 2.5h • $155.00 • Past out 3:40 PM`
 *  - `On break since 12:01 PM • Out 3:40 PM`
 * The money part is omitted (not masked) when `hideMoneyAmounts` is on, as the hero pill does.
 *
 * Data-class equality is the "did the rendered content change?" test the service uses to skip
 * redundant re-posts.
 */
data class LiveChipText(
    val title: String,
    val contentText: String,
    /** Hand to `setWhen`: the clock-out instant while counting down, else the session start. */
    val chronometerWhenMs: Long,
    /** True → `setChronometerCountDown(true)`; false → the plain elapsed chronometer. */
    val countsDown: Boolean,
    /** [ShiftClockOutTarget.clockOutAtMs], null when not clocked in. */
    val clockOutAtMs: Long?,
    /** Amount of money earned today (e.g. "$496.00 earned"), or null if hidden or unclocked. */
    val earnedMoneyText: String? = null
) {
    companion object {

        fun build(state: TimeclockState, nowMs: Long): LiveChipText {
            val startMs = state.currentSessionStart ?: nowMs
            val target = ShiftClockOutTarget.clockOutAtMs(state, nowMs)
            val past = ShiftClockOutTarget.targetIsAlreadyPast(state, nowMs)
            // Count down only to a future target; otherwise fall back to the elapsed chronometer
            // from the session start, so the chip never shows a negative or meaningless countdown.
            val countsDown = target != null && !past

            val parts = mutableListOf<String>()
            parts += if (state.isOnBreak) {
                val since = state.breakStartTime?.let { wallTime(it) }
                if (since != null) "On break since $since" else "On break"
            } else {
                "Elapsed ${PayrollEngine.formatDurationShort(nowMs - startMs)}"
            }

            val otHours = LiveOvertimePay.overtimeHoursElapsed(state, nowMs)
            if (otHours > 0.0) {
                parts += "OT ${String.format(Locale.US, "%.1f", otHours)}h"
                if (!state.settings.hideMoneyAmounts) {
                    parts += LiveOvertimePay.formatMoney(LiveOvertimePay.overtimeMoneySoFar(state, nowMs))
                }
            }

            if (target != null) {
                parts += if (past) "Past out ${wallTime(target)}" else "Out ${wallTime(target)}"
            }

            val title = when {
                !state.isClockedIn || target == null -> {
                    if (state.isOnBreak) "Shift Paused" else "Shift Active"
                }
                countsDown -> {
                    val remainingMs = (target - nowMs).coerceAtLeast(0L)
                    val totalMins = (remainingMs + 59_999L) / 60_000L
                    val hours = totalMins / 60L
                    val mins = totalMins % 60L
                    String.format(Locale.US, "%d:%02d", hours, mins)
                }
                past -> {
                    "0:00"
                }
                else -> {
                    if (state.isOnBreak) "Shift Paused" else "Shift Active"
                }
            }

            val earnedMoneyText = if (!state.settings.hideMoneyAmounts && state.isClockedIn) {
                val totals = PayrollEngine.calculatePeriodTotals(state, nowMs)
                "${PayrollEngine.formatMoney(totals.todayEarnings)} earned"
            } else null

            return LiveChipText(
                title = title,
                contentText = parts.joinToString(" • "),
                chronometerWhenMs = if (countsDown) target else startMs,
                countsDown = countsDown,
                clockOutAtMs = target,
                earnedMoneyText = earnedMoneyText
            )
        }

        /** Local wall-clock time, e.g. "3:40 PM" (phone's timezone, as elsewhere in the app). */
        fun wallTime(instantMs: Long): String = SimpleDateFormat("h:mm a", Locale.US).format(Date(instantMs))
    }
}
