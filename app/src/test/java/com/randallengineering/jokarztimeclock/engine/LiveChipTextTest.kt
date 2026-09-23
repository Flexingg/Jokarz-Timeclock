package com.randallengineering.jokarztimeclock.engine

import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** The pure text / timer choice behind the live notification. Default settings, plant zone. */
class LiveChipTextTest {

    private val ny = TimeZone.getTimeZone("America/New_York")
    private lateinit var previous: TimeZone
    // Field init runs before @Before pins the default zone, so the parser must carry the
    // plant zone itself: otherwise the wall-clock strings parse in the build machine's zone
    // and every expectation shifts (NY is UTC-4/5, CI runs UTC).
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = ny }

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
    fun beforeClockOutCountsDownToTheTargetAndShowsElapsedAndOutTime() {
        val state = clockedIn("2026-09-21 05:10")
        val chip = LiveChipText.build(state, at("2026-09-21 11:51"))
        assertEquals("Elapsed 6h 41m • Out 3:40 PM", chip.contentText)
        assertEquals("Shift Active", chip.title)
        assertTrue(chip.countsDown)
        // What goes into setWhen() is exactly the clock-out target.
        assertEquals(at("2026-09-21 15:40"), chip.chronometerWhenMs)
        assertEquals(ShiftClockOutTarget.clockOutAtMs(state, at("2026-09-21 11:51")), chip.chronometerWhenMs)
        assertFalse(chip.contentText.contains("$"))
    }

    @Test
    fun pastClockOutFallsBackToElapsedChronometerAndSaysPast() {
        val state = clockedIn("2026-09-21 05:10")
        val chip = LiveChipText.build(state, at("2026-09-21 16:30")) // banking buffer, no OT yet
        assertEquals("Elapsed 11h 20m • Past out 3:40 PM", chip.contentText)
        assertFalse(chip.countsDown)
        assertEquals(state.currentSessionStart, chip.chronometerWhenMs)
        assertFalse(chip.contentText.contains("$"))
    }

    @Test
    fun inOvertimeTheMoneyIsShown() {
        val chip = LiveChipText.build(clockedIn("2026-09-21 05:10"), at("2026-09-21 18:10"))
        assertEquals("Elapsed 13h 0m • OT 2.5h • $155.00 • Past out 3:40 PM", chip.contentText)
    }

    @Test
    fun moneyRisesMinuteByMinuteInOvertime() {
        val state = clockedIn("2026-09-21 05:10")
        val a = LiveChipText.build(state, at("2026-09-21 18:10"))
        val b = LiveChipText.build(state, at("2026-09-21 18:11"))
        assertNotEquals(a, b)
        assertTrue(b.contentText, b.contentText.contains("$156.03")) // 2.51666h * 62
    }

    @Test
    fun hiddenMoneyIsLeftOutOfTheChip() {
        val chip = LiveChipText.build(clockedIn("2026-09-21 05:10", AppSettings(hideMoneyAmounts = true)), at("2026-09-21 18:10"))
        assertEquals("Elapsed 13h 0m • OT 2.5h • Past out 3:40 PM", chip.contentText)
    }

    @Test
    fun weekendShowsOvertimeMoneyWhileStillCountingDown() {
        val chip = LiveChipText.build(clockedIn("2026-09-26 05:10"), at("2026-09-26 06:10"))
        assertEquals("Elapsed 1h 0m • OT 1.0h • $62.00 • Out 3:40 PM", chip.contentText)
        assertTrue(chip.countsDown)
        assertEquals(at("2026-09-26 15:40"), chip.chronometerWhenMs)
    }

    @Test
    fun onBreakKeepsTheExistingWording() {
        val state = clockedIn("2026-09-21 05:10").copy(isOnBreak = true, breakStartTime = at("2026-09-21 12:01"))
        val chip = LiveChipText.build(state, at("2026-09-21 12:20"))
        assertEquals("Shift Paused", chip.title)
        assertEquals("On break since 12:01 PM • Out 3:40 PM", chip.contentText)
        assertTrue(chip.countsDown)
    }

    @Test
    fun sameInputsGiveEqualChipsSoNoRedundantRepost() {
        val state = clockedIn("2026-09-21 05:10")
        // Within the same minute the rendered content is identical.
        assertEquals(
            LiveChipText.build(state, at("2026-09-21 11:51")),
            LiveChipText.build(state, at("2026-09-21 11:51") + 30_000L)
        )
    }
}
