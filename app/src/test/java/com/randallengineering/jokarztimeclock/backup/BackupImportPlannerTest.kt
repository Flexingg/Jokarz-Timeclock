package com.randallengineering.jokarztimeclock.backup

import com.randallengineering.jokarztimeclock.backup.BackupFixtures.NOW
import com.randallengineering.jokarztimeclock.backup.BackupFixtures.at
import com.randallengineering.jokarztimeclock.backup.BackupFixtures.richState
import com.randallengineering.jokarztimeclock.backup.BackupFixtures.session
import com.randallengineering.jokarztimeclock.data.backup.BackupImportPlanner
import com.randallengineering.jokarztimeclock.data.backup.ImportMode
import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.AuditEntry
import com.randallengineering.jokarztimeclock.data.models.PtoEntry
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupImportPlannerTest {

    /** Clocked in, with a/b/c on record. */
    private val current = richState()

    /** A backup from another phone: an edited copy of b, the same c, and two shifts this phone lacks. */
    private val incoming = TimeclockState(
        grossRate = 99.0,
        settings = AppSettings(otMultiplier = 2.0),
        sessions = listOf(
            current.sessions[1].copy(note = "edited elsewhere"),
            current.sessions[2],
            session("sess_new1", "2026-09-08T10:00:00Z", "2026-09-08T20:00:00Z"),
            session("sess_new2", "2026-09-09T10:00:00Z", "2026-09-09T20:00:00Z")
        ),
        ptoEntries = listOf(
            current.ptoEntries[0].copy(hours = 8.0),
            PtoEntry(id = "pto_new", date = at("2026-09-20T04:00:00Z"), hours = 10.0)
        ),
        auditLog = listOf(AuditEntry(action = "FROM_BACKUP", timestamp = 1L))
    )

    @Test
    fun mergeUpdatesCollisionsByIdAndAppendsNewSessions() {
        val plan = BackupImportPlanner.plan(current, incoming, ImportMode.MERGE, NOW)
        val result = plan.resultingState

        assertEquals(listOf("sess_a", "sess_b", "sess_c", "sess_new1", "sess_new2"), result.sessions.map { it.id })
        assertEquals("edited elsewhere", result.sessions[1].note)
        assertEquals(2, plan.sessionsAdded)
        // sess_c is identical in both, so only sess_b counts as updated.
        assertEquals(1, plan.sessionsUpdated)
        assertEquals(1, plan.ptoAdded)
        assertEquals(1, plan.ptoUpdated)
        assertEquals(8.0, result.ptoEntries[0].hours, 0.0)
        assertEquals(listOf("pto_1", "pto_2", "pto_new"), result.ptoEntries.map { it.id })
    }

    @Test
    fun mergeKeepsSettingsRatesAndTheRunningShift() {
        val plan = BackupImportPlanner.plan(current, incoming, ImportMode.MERGE, NOW)
        val result = plan.resultingState

        assertEquals(current.settings, result.settings)
        assertEquals(current.grossRate, result.grossRate, 0.0)
        assertTrue(result.isClockedIn)
        assertEquals(current.currentSessionStart, result.currentSessionStart)
        assertEquals(current.isOnBreak, result.isOnBreak)
        assertEquals(current.breakStartTime, result.breakStartTime)
        assertEquals(current.accumulatedBreakMs, result.accumulatedBreakMs)
        assertTrue(plan.keepsRunningShift)
        assertEquals(
            "MERGE by id: 2 new shifts will be added, 1 existing shift will be updated, " +
                "1 new and 1 changed PTO entries, your settings stay, and your running shift is kept.",
            plan.summary
        )
    }

    @Test
    fun mergeAuditLogIsImportThenIncomingThenCurrentCappedAtFifty() {
        val big = current.copy(auditLog = (1..60).map { AuditEntry(action = "OLD_$it", timestamp = it.toLong()) })
        val log = BackupImportPlanner.plan(big, incoming, ImportMode.MERGE, NOW).resultingState.auditLog

        assertEquals(50, log.size)
        assertEquals(AuditEntry(action = "IMPORT_BACKUP_MERGE", timestamp = NOW), log[0])
        assertEquals("FROM_BACKUP", log[1].action)
        assertEquals("OLD_1", log[2].action)
    }

    @Test
    fun replaceTakesTheBackupWholesaleAndSaysTheRunningShiftEnds() {
        val plan = BackupImportPlanner.plan(current, incoming, ImportMode.REPLACE, NOW)
        val result = plan.resultingState

        assertEquals(incoming.copy(auditLog = result.auditLog), result)
        assertEquals("IMPORT_BACKUP_REPLACE", result.auditLog[0].action)
        assertFalse(result.isClockedIn)
        assertFalse(plan.keepsRunningShift)
        assertEquals(
            "REPLACE everything: 3 shifts become 4, 2 PTO entries become 2, settings and rates come " +
                "from the backup, and the running shift will be ended.",
            plan.summary
        )
    }

    @Test
    fun replaceWhenNotClockedInDoesNotMentionAnEndedShift() {
        val idle = current.copy(isClockedIn = false, currentSessionStart = null, isOnBreak = false, breakStartTime = null)
        val plan = BackupImportPlanner.plan(idle, incoming, ImportMode.REPLACE, NOW)
        assertFalse(plan.summary.contains("running shift"))
        assertTrue(plan.summary.endsWith("come from the backup."))
    }

    @Test
    fun reimportingTheSameBackupChangesNothing() {
        val plan = BackupImportPlanner.plan(current, current, ImportMode.MERGE, NOW)
        assertEquals(0, plan.sessionsAdded)
        assertEquals(0, plan.sessionsUpdated)
        assertEquals(current.sessions, plan.resultingState.sessions)
    }
}
