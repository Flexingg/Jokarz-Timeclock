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
}
