package com.randallengineering.jokarztimeclock.backup

import com.randallengineering.jokarztimeclock.backup.BackupFixtures.HOUR
import com.randallengineering.jokarztimeclock.backup.BackupFixtures.NOW
import com.randallengineering.jokarztimeclock.backup.BackupFixtures.richState
import com.randallengineering.jokarztimeclock.backup.BackupFixtures.session
import com.randallengineering.jokarztimeclock.data.backup.BackupValidator
import com.randallengineering.jokarztimeclock.data.models.PtoEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class BackupValidatorTest {

    private fun problems(state: com.randallengineering.jokarztimeclock.data.models.TimeclockState) =
        BackupValidator.validate(state, NOW, ZoneOffset.UTC)

    private fun withSessions(vararg extra: com.randallengineering.jokarztimeclock.data.models.Session) =
        richState().let { it.copy(sessions = it.sessions + extra) }

    @Test
    fun richStateIsValid() {
        assertEquals(emptyList<String>(), problems(richState()))
    }

    @Test
    fun sessionProblemsNameTheSessionAndItsTimes() {
        assertEquals(
            listOf("Session #4 (start 2026-09-21T06:00, end 2026-09-21T05:00): the stop is at or before the start."),
            problems(withSessions(session("x", "2026-09-21T06:00:00Z", "2026-09-21T05:00:00Z")))
        )
        assertTrue(problems(withSessions(session("x", "2026-09-21T06:00:00Z", "2026-09-21T08:00:00Z", breakMs = -1)))
            .single().endsWith("the break is negative."))
        assertTrue(problems(withSessions(session("x", "2026-09-21T06:00:00Z", "2026-09-21T08:00:00Z", breakMs = 2 * HOUR)))
            .single().endsWith("the break is as long as the shift."))
        assertTrue(problems(withSessions(session("x", "1999-12-31T06:00:00Z", "1999-12-31T08:00:00Z")))
            .single().endsWith("dated before 2000."))
        assertTrue(problems(withSessions(session("x", "2026-09-25T06:00:00Z", "2026-09-25T08:00:00Z")))
            .single().endsWith("dated in the future."))
    }

    @Test
    fun ptoAndSettingsRangesAreChecked() {
        val s = richState()
        val bad = s.copy(
            ptoEntries = s.ptoEntries + PtoEntry(id = "p", date = NOW, hours = 25.0),
            settings = s.settings.copy(otMultiplier = 5.0, cliffHours = 9.0),
            grossRate = -1.0
        )
        val found = problems(bad)
        assertEquals(4, found.size)
        assertTrue(found[0], found[0].startsWith("PTO entry #3"))
        assertTrue(found.any { it.startsWith("Settings: OT cliff") })
        assertTrue(found.any { it.startsWith("Settings: OT multiplier") })
        assertTrue(found.any { it.startsWith("Gross rate") })
    }

    @Test
    fun atMostFiveMessagesPlusACount() {
        val bad = (1..8).map { session("x$it", "2026-09-21T06:00:00Z", "2026-09-21T05:00:00Z") }.toTypedArray()
        val found = problems(withSessions(*bad))
        assertEquals(6, found.size)
        assertEquals("…and 3 more problem(s).", found.last())
    }

    @Test
    fun invalidStandardShiftIsReported() {
        val s = richState()
        assertTrue(problems(s.copy(settings = s.settings.copy(standardShiftHours = 0.5)))
            .single().startsWith("Settings: standard shift"))
    }
}
