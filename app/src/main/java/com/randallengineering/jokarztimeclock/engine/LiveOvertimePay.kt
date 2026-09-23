package com.randallengineering.jokarztimeclock.engine

import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.TimeclockState

/**
 * Overtime earned so far in the running shift. Pure JVM; no new overtime definition.
 *
 * The open session is treated as if it were clocked out at `now` and handed to
 * [PayrollEngine.calculateSessionOt], so the rules are exactly the payroll ones:
 *  - Mon–Thu: nothing until clocked time reaches `cliffHours`, then
 *    `clocked - (standardShiftHours + unpaidMealDuration)` (meal only with autoBreakDeduction).
 *  - Fri–Sun: every worked hour, i.e. clocked minus breaks (or the auto meal once past
 *    `unpaidMealThreshold` when no break was taken).
 *
 * Money is `hours * rate * otMultiplier` with [PayrollEngine.displayRate] — the same gross/net
 * rate the rest of the app displays.
 */
object LiveOvertimePay {

    fun overtimeHoursElapsed(state: TimeclockState, nowMs: Long): Double {
        val start = state.currentSessionStart
        if (!state.isClockedIn || start == null || nowMs <= start) return 0.0
        // Break time so far, including a break that is still running, as calculateDayStats counts it.
        val runningBreak = if (state.isOnBreak && state.breakStartTime != null) {
            (nowMs - state.breakStartTime).coerceAtLeast(0L)
        } else 0L
        val openSession = Session(
            id = "live",
            start = start,
            end = nowMs,
            breakMs = state.accumulatedBreakMs + runningBreak
        )
        return PayrollEngine.calculateSessionOt(openSession, state)
    }

    /** Overtime money so far; 0.0 when money is hidden in Settings (see [moneyText] for the masked form). */
    fun overtimeMoneySoFar(state: TimeclockState, nowMs: Long): Double {
        if (state.settings.hideMoneyAmounts) return 0.0
        return overtimeHoursElapsed(state, nowMs) * PayrollEngine.displayRate(state) * state.settings.otMultiplier
    }

    /** Display form: the amount, or the app's standard mask when `hideMoneyAmounts` is on. */
    fun moneyText(state: TimeclockState, nowMs: Long): String =
        PayrollEngine.formatMoney(overtimeMoneySoFar(state, nowMs), hide = state.settings.hideMoneyAmounts)

    fun formatMoney(amount: Double): String = PayrollEngine.formatMoney(amount)
}
