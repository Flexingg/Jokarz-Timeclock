package com.randallengineering.jokarztimeclock.engine

import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Live overtime money. Rates are the shipped defaults ($62 gross / $30 net, 1.0x) unless a test
 * says otherwise; the overtime rules are PayrollEngine's.
 */
class LiveOvertimePayTest {

    private val ny = TimeZone.getTimeZone("America/New_York")
    private lateinit var previous: TimeZone
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    @Before
    fun setUp() {
        previous = TimeZone.getDefault()
        TimeZone.setDefault(ny)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(previous)
    }

    private fun at(text: String): Long = sdf.parse(text)!!.time

    private fun clockedIn(start: String, settings: AppSettings = AppSettings()) =
        TimeclockState(isClockedIn = true, currentSessionStart = at(start), settings = settings)

    @Test
    fun mondayThirteenHoursIsTwoAndAHalfHoursOfOvertime() {
        val state = clockedIn("2026-09-21 05:10")
        val now = at("2026-09-21 18:10")
        assertEquals(2.5, LiveOvertimePay.overtimeHoursElapsed(state, now), 1e-9)
        assertEquals(155.00, LiveOvertimePay.overtimeMoneySoFar(state, now), 1e-9) // 2.5h * $62 * 1.0
        assertEquals("$155.00", LiveOvertimePay.moneyText(state, now))
    }

    @Test
    fun usesTheDisplayedNetRateAndTheMultiplier() {
        val state = clockedIn("2026-09-21 05:10", AppSettings(otMultiplier = 1.5)).copy(displayMode = PayMode.NET)
        assertEquals(112.50, LiveOvertimePay.overtimeMoneySoFar(state, at("2026-09-21 18:10")), 1e-9) // 2.5 * 30 * 1.5
    }

    @Test
    fun nothingBeforeTheCliffOnMondayToThursday() {
        val state = clockedIn("2026-09-21 05:10")
        // 12h 20m clocked: past the 15:40 standard end but under the 12.5h cliff (banking buffer).
        val now = at("2026-09-21 17:30")
        assertEquals(0.0, LiveOvertimePay.overtimeHoursElapsed(state, now), 0.0)
        assertEquals(0.0, LiveOvertimePay.overtimeMoneySoFar(state, now), 0.0)
    }

    @Test
    fun atTheCliffOvertimeCountsFromTheStandardEndAsPayrollDoes() {
        val state = clockedIn("2026-09-21 05:10")
        // 12.5h clocked -> 12.5 - 10.5 = 2.0h, the PayrollEngine Mon-Thu rule.
        assertEquals(124.00, LiveOvertimePay.overtimeMoneySoFar(state, at("2026-09-21 17:40")), 1e-9)
    }

    @Test
    fun saturdayEveryWorkedHourIsOvertime() {
        val state = clockedIn("2026-09-26 05:10")
        // 6h clocked, no break taken -> auto 0.5h meal deducted -> 5.5h * $62.
        assertEquals(341.00, LiveOvertimePay.overtimeMoneySoFar(state, at("2026-09-26 11:10")), 1e-9)
        // A recorded 1h break is deducted instead of the auto meal.
        val withBreak = state.copy(accumulatedBreakMs = 3_600_000L)
        assertEquals(5.0, LiveOvertimePay.overtimeHoursElapsed(withBreak, at("2026-09-26 11:10")), 1e-9)
    }

    @Test
    fun agreesWithPayrollEngineCalculateSessionOt() {
        val state = clockedIn("2026-09-22 04:57")
        val now = at("2026-09-22 18:33")
        val closed = com.randallengineering.jokarztimeclock.data.models.Session(start = state.currentSessionStart!!, end = now)
        assertEquals(PayrollEngine.calculateSessionOt(closed, state), LiveOvertimePay.overtimeHoursElapsed(state, now), 0.0)
    }

    @Test
    fun hiddenMoneyIsZeroAndMasked() {
        val state = clockedIn("2026-09-21 05:10", AppSettings(hideMoneyAmounts = true))
        val now = at("2026-09-21 18:10")
        assertEquals(2.5, LiveOvertimePay.overtimeHoursElapsed(state, now), 1e-9)
        assertEquals(0.0, LiveOvertimePay.overtimeMoneySoFar(state, now), 0.0)
        assertEquals(PayrollEngine.formatMoney(0.0, hide = true), LiveOvertimePay.moneyText(state, now))
        assertEquals("$ ••••••", LiveOvertimePay.moneyText(state, now))
    }

    @Test
    fun notClockedInIsZero() {
        assertEquals(0.0, LiveOvertimePay.overtimeMoneySoFar(TimeclockState(), at("2026-09-21 18:10")), 0.0)
    }

    @Test
    fun formatMoneyIsPayrollEngines() {
        assertEquals(PayrollEngine.formatMoney(61.2), LiveOvertimePay.formatMoney(61.2))
        assertEquals("$61.20", LiveOvertimePay.formatMoney(61.2))
    }
}
