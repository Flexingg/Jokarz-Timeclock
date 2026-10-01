package com.randallengineering.jokarztimeclock

import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PayrollEngineTest {

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    @Test
    fun testStandardMondayShiftWithAutoBreak() {
        // Monday 6:00 AM to 4:30 PM (10.5h clocked -> auto 30m break -> 10.0h payable base, 0 OT, 0 bank)
        val start = sdf.parse("2026-08-24 06:00")!!.time
        val end = sdf.parse("2026-08-24 16:30")!!.time
        val state = TimeclockState(
            sessions = listOf(Session(start = start, end = end)),
            settings = AppSettings(autoBreakDeduction = true, standardShiftHours = 10.0)
        )
        val dayStart = PayrollEngine.getStartOfDay(Date(start))
        val stats = PayrollEngine.calculateDayStats(dayStart, excludeActive = true, state = state)

        assertEquals(10.5, stats.clockedHours, 0.01)
        assertEquals(10.0, stats.workedHours, 0.01)
        assertEquals(10.0, stats.payableHours, 0.01)
        assertEquals(0.0, stats.otHours, 0.01)
        assertEquals(0.0, stats.bankedHours, 0.01)
    }

    @Test
    fun testStandardMondayShiftWithoutAutoBreak() {
        // Monday 6:00 AM to 4:00 PM (10.0h clocked with NO auto break -> exactly 10.0h payable base, 0 OT, 0 bank)
        val start = sdf.parse("2026-08-24 06:00")!!.time
        val end = sdf.parse("2026-08-24 16:00")!!.time
        val state = TimeclockState(
            sessions = listOf(Session(start = start, end = end)),
            settings = AppSettings(autoBreakDeduction = false, standardShiftHours = 10.0)
        )
        val dayStart = PayrollEngine.getStartOfDay(Date(start))
        val stats = PayrollEngine.calculateDayStats(dayStart, excludeActive = true, state = state)

        assertEquals(10.0, stats.clockedHours, 0.01)
        assertEquals(10.0, stats.workedHours, 0.01)
        assertEquals(10.0, stats.payableHours, 0.01)
        assertEquals(0.0, stats.otHours, 0.01)
        assertEquals(0.0, stats.bankedHours, 0.01)
    }

    @Test
    fun testBankingBufferMondayShift() {
        // Monday 6:00 AM to 5:30 PM (11.5h clocked -> 10.0h payable base, 0 OT, +1.0h bank)
        val start = sdf.parse("2026-08-24 06:00")!!.time
        val end = sdf.parse("2026-08-24 17:30")!!.time
        val state = TimeclockState(
            sessions = listOf(Session(start = start, end = end)),
            settings = AppSettings(autoBreakDeduction = true, standardShiftHours = 10.0)
        )
        val dayStart = PayrollEngine.getStartOfDay(Date(start))
        val stats = PayrollEngine.calculateDayStats(dayStart, excludeActive = true, state = state)

        assertEquals(11.5, stats.clockedHours, 0.01)
        assertEquals(11.0, stats.workedHours, 0.01)
        assertEquals(10.0, stats.payableHours, 0.01)
        assertEquals(0.0, stats.otHours, 0.01)
        assertEquals(1.0, stats.bankedHours, 0.01)
    }

    @Test
    fun testOvertimeCliffMondayShift() {
        // Monday 6:00 AM to 7:00 PM (13.0h clocked -> >= 12.5h cliff -> 12.5h payable, 2.5h OT, 0 bank)
        val start = sdf.parse("2026-08-24 06:00")!!.time
        val end = sdf.parse("2026-08-24 19:00")!!.time
        val state = TimeclockState(
            sessions = listOf(Session(start = start, end = end)),
            settings = AppSettings(autoBreakDeduction = true, standardShiftHours = 10.0)
        )
        val dayStart = PayrollEngine.getStartOfDay(Date(start))
        val stats = PayrollEngine.calculateDayStats(dayStart, excludeActive = true, state = state)

        assertEquals(13.0, stats.clockedHours, 0.01)
        assertEquals(12.5, stats.workedHours, 0.01)
        assertEquals(12.5, stats.payableHours, 0.01)
        assertEquals(2.5, stats.otHours, 0.01)
        assertEquals(0.0, stats.bankedHours, 0.01)
    }

    @Test
    fun testWeekendOvertimeShift() {
        // Saturday 7:00 AM to 1:00 PM (6.0h clocked -> 5.5h payable OT)
        val start = sdf.parse("2026-08-29 07:00")!!.time
        val end = sdf.parse("2026-08-29 13:00")!!.time
        val state = TimeclockState(
            sessions = listOf(Session(start = start, end = end)),
            settings = AppSettings(autoBreakDeduction = true)
        )
        val dayStart = PayrollEngine.getStartOfDay(Date(start))
        val stats = PayrollEngine.calculateDayStats(dayStart, excludeActive = true, state = state)

        assertEquals(6.0, stats.clockedHours, 0.01)
        assertEquals(5.5, stats.workedHours, 0.01)
        assertEquals(5.5, stats.payableHours, 0.01)
        assertEquals(5.5, stats.otHours, 0.01)
    }

    @Test
    fun testCalculateSessionOtAndGetOtSessions() {
        val monStart = sdf.parse("2026-08-24 06:00")!!.time
        val monEnd = sdf.parse("2026-08-24 19:15")!!.time // 13.25h -> 13.25 - 10.5 = 2.75h OT

        val satStart = sdf.parse("2026-08-29 07:00")!!.time
        val satEnd = sdf.parse("2026-08-29 12:00")!!.time // 5.0h -> 4.5h OT

        val regularStart = sdf.parse("2026-08-25 06:00")!!.time
        val regularEnd = sdf.parse("2026-08-25 16:30")!!.time // 10.5h -> 0.0h OT

        val sessMon = Session(start = monStart, end = monEnd)
        val sessSat = Session(start = satStart, end = satEnd)
        val sessReg = Session(start = regularStart, end = regularEnd)

        val state = TimeclockState(
            sessions = listOf(sessMon, sessSat, sessReg),
            settings = AppSettings(autoBreakDeduction = true, standardShiftHours = 10.0, cliffHours = 12.5)
        )

        assertEquals(2.75, PayrollEngine.calculateSessionOt(sessMon, state), 0.01)
        assertEquals(4.5, PayrollEngine.calculateSessionOt(sessSat, state), 0.01)
        assertEquals(0.0, PayrollEngine.calculateSessionOt(sessReg, state), 0.01)

        val otSessions = PayrollEngine.getOtSessions(state)
        assertEquals(2, otSessions.size)
        assertEquals(satStart, otSessions[0].second.start)
        assertEquals(monStart, otSessions[1].second.start)
    }

    @Test
    fun testStandardShiftChangeUniversalApplicationPastAndHistoricalShifts() {
        // Monday shift: 6:00 AM to 4:30 PM (10.5h clocked -> auto 30m break -> 10.0h worked)
        val monStart = sdf.parse("2026-08-24 06:00")!!.time
        val monEnd = sdf.parse("2026-08-24 16:30")!!.time
        val sessMon = Session(start = monStart, end = monEnd)

        // 1. With standardShiftHours = 10.0 (default cliff 12.5)
        val state10 = TimeclockState(
            sessions = listOf(sessMon),
            settings = AppSettings(standardShiftHours = 10.0, cliffHours = 12.5, autoBreakDeduction = true)
        )
        val stats10 = PayrollEngine.calculateDayStats(PayrollEngine.getStartOfDay(Date(monStart)), excludeActive = true, state = state10)
        assertEquals(10.0, stats10.baseHours, 0.01)
        assertEquals(0.0, stats10.otHours, 0.01)
        assertEquals(0.0, stats10.bankedHours, 0.01)
        assertEquals(0.0, PayrollEngine.calculateSessionOt(sessMon, state10), 0.01)
        assertEquals(0, PayrollEngine.getOtSessions(state10).size)

        // 2. Change standardShiftHours to 8.0: applies UNIVERSALLY to this past shift!
        val state8 = state10.copy(settings = state10.settings.copy(standardShiftHours = 8.0))
        val stats8 = PayrollEngine.calculateDayStats(PayrollEngine.getStartOfDay(Date(monStart)), excludeActive = true, state = state8)
        assertEquals(8.0, stats8.baseHours, 0.01)
        // 10.5h clocked >= 10.5h cliff -> 10.5 - 8.5 = 2.0h OT
        assertEquals(2.0, stats8.otHours, 0.01)
        assertEquals(0.0, stats8.bankedHours, 0.01)
        assertEquals(10.0, stats8.payableHours, 0.01)
        assertEquals(2.0, PayrollEngine.calculateSessionOt(sessMon, state8), 0.01)
        val otSessions8 = PayrollEngine.getOtSessions(state8)
        assertEquals(1, otSessions8.size)
        assertEquals(sessMon.id, otSessions8[0].second.id)
    }

    @Test
    fun testEffectiveCliffHoursCalculation() {
        // Default 10h shift with 12.5h cliff
        val s10 = AppSettings(standardShiftHours = 10.0, cliffHours = 12.5)
        assertEquals(12.5, s10.effectiveCliffHours, 0.01)

        // Standard shift changed to 8.0, but cliffHours left at 12.5 -> automatically 8.0 + 0.5 + 2.0 = 10.5
        val s8 = AppSettings(standardShiftHours = 8.0, cliffHours = 12.5)
        assertEquals(10.5, s8.effectiveCliffHours, 0.01)

        // Explicit custom cliff: e.g. 11.0
        val sCustom = AppSettings(standardShiftHours = 8.0, cliffHours = 11.0)
        assertEquals(11.0, sCustom.effectiveCliffHours, 0.01)

        // Without auto break: min cliff is 8.0 + 2.0 = 10.0
        val sNoBreak = AppSettings(standardShiftHours = 8.0, cliffHours = 12.5, autoBreakDeduction = false)
        assertEquals(10.0, sNoBreak.effectiveCliffHours, 0.01)
    }
}
