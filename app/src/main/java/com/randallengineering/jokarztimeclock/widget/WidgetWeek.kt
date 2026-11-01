package com.randallengineering.jokarztimeclock.widget

import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import com.randallengineering.jokarztimeclock.engine.ShiftTimeMath

/** This week's totals, the same figures as the app's "Current Week" card. */
internal data class WeekSummary(val clockedMs: Long, val systemInputHours: Double, val bankedHours: Double)

internal fun currentWeekSummary(state: TimeclockState, nowMs: Long = System.currentTimeMillis()): WeekSummary {
    val weekStart = PayrollEngine.getStartOfWeekDate(java.util.Date(nowMs))
    val todayStart = PayrollEngine.getStartOfDay(java.util.Date(nowMs))
    var clocked = 0L
    var input = 0.0
    var banked = 0.0
    var day = weekStart
    repeat(7) {
        if (day <= todayStart) {
            val stats = PayrollEngine.calculateDayStats(day, excludeActive = false, state = state, nowMs = nowMs)
            clocked += stats.clockedMs
            input += stats.systemInput
            banked += stats.bankedHours
        }
        day = ShiftTimeMath.addDays(day, 1)
    }
    return WeekSummary(clocked, input, banked)
}
