package com.randallengineering.jokarztimeclock.engine

import com.randallengineering.jokarztimeclock.data.models.DayStats
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.PaySchedule
import com.randallengineering.jokarztimeclock.data.models.PeriodTotals
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

object PayrollEngine {

    fun getStartOfDay(date: Date = Date()): Long {
        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getStartOfWeekDate(date: Date = Date()): Long {
        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val day = get(Calendar.DAY_OF_WEEK)
            val diff = if (day == Calendar.SUNDAY) -6 else Calendar.MONDAY - day
            add(Calendar.DAY_OF_MONTH, diff)
        }
        return cal.timeInMillis
    }

    fun getStartOfPayPeriod(date: Date = Date(), state: TimeclockState): Long {
        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (state.settings.paySchedule) {
            PaySchedule.SEMI_MONTHLY -> {
                if (cal.get(Calendar.DAY_OF_MONTH) <= 15) {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                } else {
                    cal.set(Calendar.DAY_OF_MONTH, 16)
                }
                cal.timeInMillis
            }
            PaySchedule.WEEKLY -> getStartOfWeekDate(date)
            PaySchedule.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.timeInMillis
            }
            PaySchedule.BI_WEEKLY -> {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val anchorCal = Calendar.getInstance().apply {
                        time = sdf.parse(state.settings.biweeklyAnchorDate) ?: Date()
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val diffDays = ((cal.timeInMillis - anchorCal.timeInMillis) / 86400000L).toInt()
                    val periodIndex = diffDays / 14
                    anchorCal.timeInMillis + (periodIndex * 14L * 86400000L)
                } catch (e: Exception) {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.timeInMillis
                }
            }
        }
    }

    fun getEndOfPayPeriod(date: Date = Date(), state: TimeclockState): Long {
        val cal = Calendar.getInstance().apply {
            time = date
        }

        return when (state.settings.paySchedule) {
            PaySchedule.SEMI_MONTHLY -> {
                if (cal.get(Calendar.DAY_OF_MONTH) <= 15) {
                    cal.set(Calendar.DAY_OF_MONTH, 15)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                } else {
                    val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, lastDay)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                }
                cal.timeInMillis
            }
            PaySchedule.WEEKLY -> {
                val start = getStartOfWeekDate(date)
                start + (6L * 86400000L) + (86399999L)
            }
            PaySchedule.MONTHLY -> {
                val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, lastDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                cal.timeInMillis
            }
            PaySchedule.BI_WEEKLY -> {
                val start = getStartOfPayPeriod(date, state)
                start + (13L * 86400000L) + (86399999L)
            }
        }
    }

    fun calculateDayStats(dayStartMs: Long, excludeActive: Boolean = false, state: TimeclockState): DayStats {
        var clockedMs = 0L
        var totalBreakMs = 0L
        val dayEndMs = dayStartMs + 86400000L

        state.sessions.forEach { sess ->
            if (sess.start in dayStartMs until dayEndMs) {
                clockedMs += (sess.end - sess.start)
                totalBreakMs += sess.breakMs
            }
        }

        if (!excludeActive && state.isClockedIn && state.currentSessionStart != null) {
            val curStart = state.currentSessionStart
            if (curStart in dayStartMs until dayEndMs) {
                clockedMs += (System.currentTimeMillis() - curStart)
                var curBreak = state.accumulatedBreakMs
                if (state.isOnBreak && state.breakStartTime != null) {
                    curBreak += (System.currentTimeMillis() - state.breakStartTime)
                }
                totalBreakMs += curBreak
            }
        }

        val settings = state.settings
        val standardSalaryHours = settings.standardShiftHours
        val unpaidMealThreshold = settings.unpaidMealThreshold
        val unpaidMealDuration = settings.unpaidMealDuration
        val cliffHours = settings.cliffHours
        val otMultiplier = settings.otMultiplier

        val clockedHours = clockedMs / 3600000.0
        var breakHours = totalBreakMs / 3600000.0
        if (breakHours == 0.0 && settings.autoBreakDeduction && clockedHours > unpaidMealThreshold) {
            breakHours = unpaidMealDuration
        }

        val workedHours = max(0.0, clockedHours - breakHours)

        val cal = Calendar.getInstance().apply { timeInMillis = dayStartMs }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        var baseHours = 0.0
        var otHours = 0.0
        var bankedHours = 0.0
        var type = ""

        var ptoHours = 0.0
        state.ptoEntries.forEach { p ->
            val pCal = Calendar.getInstance().apply {
                timeInMillis = p.date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (pCal.timeInMillis == dayStartMs) {
                ptoHours += p.hours
            }
        }

        val isMonThu = dayOfWeek in Calendar.MONDAY..Calendar.THURSDAY
        if (isMonThu) {
            baseHours = standardSalaryHours
            val targetStandardShift = standardSalaryHours + unpaidMealDuration

            if (clockedHours >= cliffHours) {
                otHours = clockedHours - targetStandardShift
                bankedHours = 0.0
                type = "Base + OT"
            } else if (clockedHours > 0.1) {
                otHours = 0.0
                bankedHours = workedHours - standardSalaryHours
                type = "Salary Base"
            } else {
                otHours = 0.0
                bankedHours = 0.0
                type = if (ptoHours > 0.0) "PTO / Holiday" else "Salary Base"
            }
        } else {
            baseHours = 0.0
            otHours = workedHours
            bankedHours = 0.0
            type = if (clockedHours > 0.0) "Overtime" else if (ptoHours > 0.0) "PTO / Holiday" else "Off"
        }

        val payableHours = baseHours + otHours + ptoHours
        val systemInput = otHours

        return DayStats(
            dayStartMs = dayStartMs,
            clockedMs = clockedMs,
            clockedHours = clockedHours,
            workedHours = workedHours,
            breakHours = breakHours,
            baseHours = baseHours,
            otHours = otHours,
            ptoHours = ptoHours,
            bankedHours = bankedHours,
            payableHours = payableHours,
            systemInput = systemInput,
            type = type,
            otMultiplier = otMultiplier
        )
    }

    fun getPreviousBankedHoursForCurrentWeek(targetTimeMs: Long, state: TimeclockState): Double {
        val targetStartOfDay = getStartOfDay(Date(targetTimeMs))
        val startOfWeek = getStartOfWeekDate(Date(targetTimeMs))

        var totalPrevBanked = 0.0
        var cur = startOfWeek
        while (cur < targetStartOfDay) {
            val stats = calculateDayStats(cur, excludeActive = true, state = state)
            totalPrevBanked += stats.bankedHours
            cur += 86400000L
        }
        return totalPrevBanked
    }

    fun calculatePeriodTotals(state: TimeclockState): PeriodTotals {
        val startOfDay = getStartOfDay()
        val startOfPeriod = getStartOfPayPeriod(Date(), state)
        val endOfPeriod = getEndOfPayPeriod(Date(), state)

        var totalClockedMsPeriod = 0L
        var totalClockedHoursPeriod = 0.0
        var totalPayableHoursPeriod = 0.0
        var totalOtHoursPeriod = 0.0
        var totalPtoHoursPeriod = 0.0
        var todayStats: DayStats? = null

        var cur = startOfPeriod
        while (cur <= minOf(startOfDay, endOfPeriod)) {
            val stats = calculateDayStats(cur, excludeActive = false, state = state)
            totalClockedMsPeriod += stats.clockedMs
            totalClockedHoursPeriod += stats.clockedHours
            totalPayableHoursPeriod += stats.payableHours
            totalOtHoursPeriod += stats.otHours
            totalPtoHoursPeriod += stats.ptoHours

            if (cur == startOfDay) {
                todayStats = stats
            }
            cur += 86400000L
        }

        val rate = if (state.displayMode == PayMode.GROSS) state.grossRate else state.netRate
        val otMult = state.settings.otMultiplier

        val regularHours = max(0.0, totalPayableHoursPeriod - totalOtHoursPeriod)
        val periodEarnings = (regularHours * rate) + (totalOtHoursPeriod * rate * otMult)
        val periodGrossEarnings = (regularHours * state.grossRate) + (totalOtHoursPeriod * state.grossRate * otMult)
        val periodNetEarnings = (regularHours * state.netRate) + (totalOtHoursPeriod * state.netRate * otMult)

        val todayOtEarnings = todayStats?.let { it.otHours * rate * otMult } ?: 0.0
        val todayEarnings = todayStats?.let { ((it.payableHours - it.otHours) * rate) + todayOtEarnings } ?: 0.0

        return PeriodTotals(
            todayStats = todayStats,
            totalClockedMsPeriod = totalClockedMsPeriod,
            totalClockedHoursPeriod = totalClockedHoursPeriod,
            totalPayableHoursPeriod = totalPayableHoursPeriod,
            totalOtHoursPeriod = totalOtHoursPeriod,
            totalPtoHoursPeriod = totalPtoHoursPeriod,
            rate = rate,
            todayEarnings = todayEarnings,
            todayOtEarnings = todayOtEarnings,
            periodGrossEarnings = periodGrossEarnings,
            periodNetEarnings = periodNetEarnings,
            periodEarnings = periodEarnings,
            startOfPeriod = startOfPeriod,
            endOfPeriod = endOfPeriod
        )
    }

    fun formatDuration(ms: Long): String {
        val totalSecs = max(0L, ms / 1000L)
        val hours = totalSecs / 3600L
        val mins = (totalSecs % 3600L) / 60L
        val secs = totalSecs % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    }

    fun formatDurationShort(ms: Long): String {
        val totalMins = max(0L, ms / 60000L)
        val hours = totalMins / 60L
        val mins = totalMins % 60L
        return "${hours}h ${mins}m"
    }

    fun formatMoney(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale.US)
        return format.format(max(0.0, amount))
    }
}
