package com.randallengineering.jokarztimeclock.backup

import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.AuditEntry
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.PaySchedule
import com.randallengineering.jokarztimeclock.data.models.PtoEntry
import com.randallengineering.jokarztimeclock.data.models.PtoType
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.ThemeMode
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import java.time.Instant

/** Fixed instants and a deliberately rich state, so no test depends on the build machine's clock. */
object BackupFixtures {
    fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    val NOW = at("2026-09-23T12:00:00Z")
    const val HOUR = 3_600_000L

    fun session(id: String, startIso: String, endIso: String, breakMs: Long = 0L, note: String = "") =
        Session(id = id, start = at(startIso), end = at(endIso), breakMs = breakMs, note = note)

    fun richState(): TimeclockState = TimeclockState(
        isClockedIn = true,
        currentSessionStart = at("2026-09-23T10:00:00Z"),
        isOnBreak = true,
        breakStartTime = at("2026-09-23T11:30:00Z"),
        accumulatedBreakMs = 900_000L,
        grossRate = 62.5,
        netRate = 31.25,
        displayMode = PayMode.NET,
        sessions = listOf(
            session("sess_a", "2026-09-01T10:00:00Z", "2026-09-01T20:30:00Z", breakMs = HOUR / 2, note = "Line 3 <pump> & \"seal\""),
            session("sess_b", "2026-09-02T10:00:00Z", "2026-09-02T22:45:00Z", breakMs = HOUR),
            // Overnight shift across midnight.
            session("sess_c", "2026-09-03T22:00:00Z", "2026-09-04T08:00:00Z").copy(jobCode = "WO-1182", isPutInSystem = true)
        ),
        ptoEntries = listOf(
            PtoEntry(id = "pto_1", date = at("2026-09-07T04:00:00Z"), hours = 10.0, type = PtoType.HOLIDAY, note = "Labor Day"),
            PtoEntry(id = "pto_2", date = at("2026-09-15T04:00:00Z"), hours = 4.5, type = PtoType.SICK)
        ),
        settings = AppSettings(
            theme = ThemeMode.AMOLED,
            paySchedule = PaySchedule.BI_WEEKLY,
            standardShiftHours = 10.0,
            cliffHours = 12.0,
            otMultiplier = 1.5,
            unpaidMealDuration = 0.5,
            geofenceEnabled = true,
            runTaskerTaskOnClock = true,
            taskerTaskName = "Plant Clock",
            workLatitude = 41.123456,
            workLongitude = -87.654321,
            geofenceRadiusMeters = 175.5f,
            workAddressName = "Plant 2 — North Gate"
        ),
        auditLog = listOf(
            AuditEntry(action = "CLOCK_IN", timestamp = at("2026-09-23T10:00:00Z"), payloadJson = "{\"start\": 1}"),
            AuditEntry(action = "EDIT_SESSION", timestamp = at("2026-09-22T09:00:00Z"), payloadJson = "{\"a\"=\"b\"}")
        )
    )
}
