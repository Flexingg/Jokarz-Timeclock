package com.randallengineering.jokarztimeclock.csv

import com.randallengineering.jokarztimeclock.data.backup.ImportMode
import com.randallengineering.jokarztimeclock.data.csv.AnomalyKind
import com.randallengineering.jokarztimeclock.data.csv.CsvImportPreview
import com.randallengineering.jokarztimeclock.data.csv.CsvParseResult
import com.randallengineering.jokarztimeclock.data.csv.LegacyCsvImporter
import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.PaySchedule
import com.randallengineering.jokarztimeclock.data.models.PtoEntry
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.ShiftTimeMath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The CSV import engine against the owner's real legacy payroll export
 * (`src/test/fixtures/legacy-export-2026-09.csv`, read from disk — never pasted in here).
 * The default time zone is pinned so wall-clock assertions do not depend on the build machine.
 */
class LegacyCsvImporterTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val ny = TimeZone.getTimeZone("America/New_York")
    private lateinit var previous: TimeZone

    private val now = 1_790_000_000_000L
    private val hour = ShiftTimeMath.MS_PER_HOUR
    private val minute = ShiftTimeMath.MS_PER_MINUTE

    @Before
    fun pinTimeZone() {
        previous = TimeZone.getDefault()
        TimeZone.setDefault(ny)
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(previous)
    }

    // ---------------------------------------------------------------- his real file

    @Test
    fun fixtureIsTheOwnersUnmodifiedFile() {
        val bytes = fixture().readBytes()
        assertEquals(1829, bytes.size)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("8fff65ffa5c96ac4ba92512ff106a1f72a1b7c3e7cb8e06b69417adc18444544", sha)
    }

    @Test
    fun realFileParsesToExactly24Entries() {
        val parsed = parseFixture()
        assertEquals(24, parsed.rowsInFile)
        assertEquals(24, parsed.entries.size)
        assertEquals(24, parsed.sessions.map { it.id }.toSet().size)
        assertEquals("2026-08-24", parsed.entries.first().date)
        assertEquals("2026-09-22", parsed.entries.last().date)
        // First row: 05:10:00 to 15:12:07 is 10h 2m 7s, recomputed from the instants.
        assertEquals(10 * hour + 2 * minute + 7_000L, parsed.entries.first().durationMs)
        assertTrue(parsed.sessions.all { it.breakMs == 0L && it.end > it.start })
    }

    @Test
    fun realFileRaisesExactlyTheExpectedFlags() {
        val flags = parseFixture().anomalies.map { it.kind to it.date }.toSet()
        assertEquals(
            setOf(
                AnomalyKind.VERY_SHORT_ENTRY to "2026-09-10",
                AnomalyKind.MISSED_CLOCK_OUT to "2026-09-21",
                AnomalyKind.VERY_SHORT_ENTRY to "2026-09-22"
            ),
            flags
        )
    }

    @Test
    fun splitShiftDaysKeepBothEntriesWithTheirWallTimes() {
        val parsed = parseFixture()
        fun wall(date: String) = parsed.entries.filter { it.date == date }
            .map { local(it.session.start) to local(it.session.end) }

        assertEquals(
            listOf(
                "2026-09-08 05:15:00" to "2026-09-08 12:15:01",
                "2026-09-08 13:19:40" to "2026-09-08 16:31:40"
            ),
            wall("2026-09-08")
        )
        assertEquals(
            listOf(
                "2026-09-22 05:11:13" to "2026-09-22 05:14:07",
                "2026-09-22 05:14:13" to "2026-09-22 16:21:16"
            ),
            wall("2026-09-22")
        )
    }

    @Test
    fun missedClockOutRowIsFlaggedButStillImportedWithRecomputedDuration() {
        val parsed = parseFixture()
        val flag = parsed.anomalies.single { it.kind == AnomalyKind.MISSED_CLOCK_OUT }
        assertEquals("2026-09-21", flag.date)
        assertTrue(flag.message, flag.message.contains("2026-09-21"))
        assertTrue(flag.message, flag.message.contains("34h"))
        assertTrue(flag.message, flag.message.contains("10h 36m"))
        assertTrue(flag.message, flag.message.contains("missed a clock-out"))

        val entry = parsed.entries.single { it.date == "2026-09-21" }
        assertEquals(flag.sessionIds, listOf(entry.session.id))
        // 05:23:55 to 16:00:44 — the file's 34.61 h is NOT used.
        assertEquals(10 * hour + 36 * minute + 49_000L, entry.durationMs)
        assertEquals("2026-09-21 05:23:55", local(entry.session.start))
        assertEquals("2026-09-21 16:00:44", local(entry.session.end))

        val preview = CsvImportPreview.build(parsed, TimeclockState(), ImportMode.MERGE, now)
        assertTrue(preview.stateToApply.sessions.any { it.id == entry.session.id })
    }

    @Test
    fun geofenceNotesSurviveVerbatimOnTheExpectedRows() {
        val parsed = parseFixture()
        val geofenceLines = parsed.entries.filter { it.session.note.isNotEmpty() }.map { it.line }
        // Physical lines (header is line 1) whose Notes column says Auto Clock Out via Geofence.
        assertEquals(listOf(4, 5, 7, 9, 12, 13, 15, 20, 22, 24), geofenceLines)
        parsed.entries.filter { it.line in geofenceLines }.forEach {
            assertEquals("Auto Clock Out via Geofence", it.session.note)
        }
    }

    @Test
    fun twoAndThreeMinuteEntriesAreImportedNotDroppedOrMerged() {
        val parsed = parseFixture()
        val threeMin = parsed.entries.single { local(it.session.start) == "2026-09-10 04:59:05" }
        assertEquals("2026-09-10 05:03:03", local(threeMin.session.end))
        assertEquals(3 * minute + 58_000L, threeMin.durationMs)
        val twoMin = parsed.entries.single { local(it.session.start) == "2026-09-22 05:11:13" }
        assertEquals("2026-09-22 05:14:07", local(twoMin.session.end))
        assertEquals(2 * minute + 54_000L, twoMin.durationMs)
        // Their neighbouring shifts on the same days are still separate entries.
        assertEquals(2, parsed.entries.count { it.date == "2026-09-10" })
        assertEquals(2, parsed.entries.count { it.date == "2026-09-22" })

        val shortIds = parsed.anomalies.filter { it.kind == AnomalyKind.VERY_SHORT_ENTRY }.flatMap { it.sessionIds }
        assertEquals(listOf(threeMin.session.id, twoMin.session.id), shortIds)
    }

    // ---------------------------------------------------------------- derived columns are ignored

    @Test
    fun techDurationColumnsNeverAffectTheImportedDuration() {
        val original = parseFixture()
        val lines = fixture().readText().split("\n")
        var n = 0
        val scrambled = lines.mapIndexed { i, line ->
            if (i == 0 || line.isEmpty()) line else {
                // His file has no commas inside quotes, so a plain split is exact for this rewrite.
                val f = line.split(",").toMutableList()
                n++
                f[5] = listOf("999.99", "banana", "-5", "0", "")[n % 5]
                f[6] = listOf("\"99h 99m\"", "\"nonsense\"", "\"\"", "\"-3h\"", "\"0h 1m\"")[n % 5]
                f.joinToString(",")
            }
        }.joinToString("\n")
        val copy = tmpFolder.newFile("scrambled.csv").apply { writeText(scrambled) }
        assertNotEquals(fixture().readText(), copy.readText())

        val reparsed = parse(copy.readText())
        assertEquals(original.entries.size, reparsed.entries.size)
        assertEquals(original.entries.map { it.durationMs }, reparsed.entries.map { it.durationMs })
        assertEquals(original.sessions, reparsed.sessions)
    }

    // ---------------------------------------------------------------- tolerance

    @Test
    fun bomCrlfBlankLinesCaseExtraColumnAndNoNotesColumnStillParse() {
        val original = parseFixture()
        val rows = fixture().readText().trimEnd('\n').split("\n").drop(1)
        val header = "  DATE ,day,start TIME,End time,BREAK (MINS),Tech Duration (Hours),tech duration (formatted),Site Code"
        val body = rows.mapIndexed { i, line ->
            // Replace the Notes column with an unknown column; unquote the date on alternate rows.
            val f = line.split(",").toMutableList()
            f[7] = "\"Plant 4, Bay \"\"B\"\"\""
            if (i % 2 == 0) f[0] = f[0].trim('"')
            f.joinToString(",")
        }
        val text = "﻿" + header + "\r\n" + body.take(12).joinToString("\r\n") + "\r\n\r\n" +
            body.drop(12).joinToString("\r\n") + "\r\n\r\n"
        val file = tmpFolder.newFile("variant.csv").apply { writeText(text) }
        assertTrue(file.readBytes().take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

        val parsed = parse(file.readText())
        assertEquals(24, parsed.entries.size)
        assertEquals(original.entries.map { it.durationMs }, parsed.entries.map { it.durationMs })
        assertEquals(original.sessions.map { it.start to it.end }, parsed.sessions.map { it.start to it.end })
        assertTrue(parsed.sessions.all { it.note == "" })
    }

    @Test
    fun quotedCommasEscapedQuotesAndHourMinuteTimesParse() {
        val parsed = parse(
            "Date,Start Time,End Time,Notes\n" +
                "2026-09-01,06:00,14:30,\"Pump 3, \"\"seal\"\" swap\"\n" +
                "2026-09-02,6:05,14:30:15,plain note"
        )
        assertEquals(2, parsed.entries.size)
        assertEquals("Pump 3, \"seal\" swap", parsed.sessions[0].note)
        assertEquals(8 * hour + 30 * minute, parsed.entries[0].durationMs)
        assertEquals("2026-09-02 06:05:00", local(parsed.sessions[1].start))
        assertEquals("plain note", parsed.sessions[1].note)
    }

    @Test
    fun breakMinutesBecomeBreakMsWithoutTouchingTheClockedDuration() {
        val parsed = parse("Date,Start Time,End Time,Break (Mins)\n2026-09-01,06:00:00,16:00:00,30\n")
        assertEquals(30 * minute, parsed.sessions.single().breakMs)
        assertEquals(10 * hour, parsed.entries.single().durationMs)
    }

    // ---------------------------------------------------------------- overnight, thresholds, overlaps

    @Test
    fun endAtOrBeforeStartRollsToTheNextCalendarDayAndIsFlagged() {
        val parsed = parse("Date,Start Time,End Time\n2026-09-30,22:00:00,06:15:00\n")
        val entry = parsed.entries.single()
        assertEquals("2026-09-30 22:00:00", local(entry.session.start))
        assertEquals("2026-10-01 06:15:00", local(entry.session.end))
        assertEquals(8 * hour + 15 * minute, entry.durationMs)
        assertTrue(entry.endRolledToNextDay)
        val flag = parsed.anomalies.single()
        assertEquals(AnomalyKind.OVERNIGHT_ROLLED, flag.kind)
        assertTrue(flag.kind.informational)
        assertTrue(flag.message, flag.message.contains("2026-10-01"))
    }

    @Test
    fun overnightRollAcrossDstFallBackIsRealElapsedTime() {
        // 2026-11-01 01:00 EDT falls back to EST: 22:00 to 06:00 wall clock is 9h of real time.
        val entry = parse("Date,Start Time,End Time\n2026-10-31,22:00:00,06:00:00\n").entries.single()
        assertEquals("2026-11-01 06:00:00", local(entry.session.end))
        assertEquals(9 * hour, entry.durationMs)
    }

    @Test
    fun implausibleThresholdIsSixteenHoursAndIsExclusive() {
        assertEquals(16 * hour, LegacyCsvImporter.IMPLAUSIBLE_AFTER_MS)
        val parsed = parse(
            "Date,Start Time,End Time\n" +
                "2026-09-01,04:00:00,20:00:00\n" +   // exactly 16h: not flagged
                "2026-09-02,04:00:00,20:00:01\n"     // 16h 0m 1s: flagged
        )
        val flagged = parsed.anomalies.filter { it.kind == AnomalyKind.IMPLAUSIBLE_DURATION }
        assertEquals(listOf("2026-09-02"), flagged.map { it.date })
        assertFalse(flagged.single().kind.informational)
        assertEquals(2, parsed.entries.size)
    }

    @Test
    fun veryShortThresholdIsFiveMinutesAndIsExclusive() {
        assertEquals(5 * minute, LegacyCsvImporter.VERY_SHORT_UNDER_MS)
        val parsed = parse(
            "Date,Start Time,End Time\n" +
                "2026-09-01,05:00:00,05:05:00\n" +   // exactly 5m: not flagged
                "2026-09-02,05:00:00,05:04:59\n"     // 4m 59s: flagged
        )
        assertEquals(
            listOf("2026-09-02"),
            parsed.anomalies.filter { it.kind == AnomalyKind.VERY_SHORT_ENTRY }.map { it.date }
        )
    }

    @Test
    fun missedClockOutToleranceIsSixtyMinutes() {
        assertEquals(60 * minute, LegacyCsvImporter.EXPORT_MISMATCH_TOLERANCE_MS)
        val parsed = parse(
            "Date,Start Time,End Time,Tech Duration (Hours)\n" +
                "2026-09-01,06:00:00,16:00:00,11.00\n" +   // off by exactly 60m: not flagged
                "2026-09-02,06:00:00,16:00:00,11.02\n"     // off by 61.2m: flagged
        )
        assertEquals(
            listOf("2026-09-02"),
            parsed.anomalies.filter { it.kind == AnomalyKind.MISSED_CLOCK_OUT }.map { it.date }
        )
        assertEquals(listOf(10 * hour, 10 * hour), parsed.entries.map { it.durationMs })
    }

    @Test
    fun overlappingAndDuplicateEntriesAreAllImportedAndFlagged() {
        val parsed = parse(
            "Date,Start Time,End Time\n" +
                "2026-09-01,05:00:00,12:00:00\n" +
                "2026-09-01,11:00:00,15:00:00\n" +
                "2026-09-01,15:00:00,16:00:00\n" +  // touches the previous end: not an overlap
                "2026-09-02,05:00:00,06:00:00\n" +
                "2026-09-02,05:00:00,06:00:00\n"    // exact duplicate row: still two entries
        )
        assertEquals(5, parsed.entries.size)
        assertEquals(5, parsed.sessions.map { it.id }.toSet().size)
        val overlaps = parsed.anomalies.filter { it.kind == AnomalyKind.OVERLAPPING_ENTRIES }
        assertEquals(listOf(2, 5), overlaps.map { it.line })
        assertEquals(listOf(parsed.sessions[0].id, parsed.sessions[1].id), overlaps[0].sessionIds)
        assertTrue(overlaps[0].message, overlaps[0].message.contains("line 3"))
    }

    // ---------------------------------------------------------------- failures are values

    @Test
    fun truncatedOrHeaderlessFilesFailWithALineNumberAndNoEntries() {
        val header = fixture().readText().lineSequence().first()
        val cases = mapOf(
            // Half-written row cut inside a quoted field.
            "$header\n\"2026-09-21\",\"Mon\",\"05:2" to 2,
            // Half-written row cut between fields.
            "$header\n\"2026-09-21\",\"Mon\",\"05:23:55\"," to 2,
            // His rows with the header line missing.
            fixture().readText().lines().drop(1).joinToString("\n") to 1,
            // One good row, then a bad date.
            "$header\n" + fixture().readText().lines()[1] + "\n\"2026-02-30\",\"Mon\",\"05:00:00\",\"06:00:00\",0,1.00,\"1h 0m\",\"\"\n" to 3,
            // Bad time.
            "Date,Start Time,End Time\n2026-09-01,25:00:00,06:00:00\n" to 2,
            // Negative break.
            "Date,Start Time,End Time,Break (Mins)\n2026-09-01,05:00:00,06:00:00,-10\n" to 2,
            "" to 1
        )
        for ((text, line) in cases) {
            val result = LegacyCsvImporter.parse(text)
            if (result !is CsvParseResult.Failure) fail("Expected failure for:\n$text\nbut got $result")
            result as CsvParseResult.Failure
            assertEquals(text, line, result.line)
            if (text.isNotEmpty()) assertTrue(result.reason, result.reason.contains("Line $line"))
        }
    }

    // ---------------------------------------------------------------- preview / planner

    @Test
    fun previewShowsEveryEntryWithLocalTimesDurationBreakAndNote() {
        val preview = CsvImportPreview.build(parseFixture(), TimeclockState(), ImportMode.MERGE, now)
        assertEquals(24, preview.rows.size)
        assertEquals(24, preview.rowsInFile)
        assertEquals(24, preview.entriesParsed)
        assertEquals(3, preview.entriesFlagged)
        assertEquals(1, preview.entriesNeedingAttention)
        assertEquals(24, preview.sessionsAdded)
        assertEquals(0, preview.sessionsUpdated)
        assertTrue(preview.modeStatement, preview.modeStatement.startsWith("MERGE by id: 24 new shifts"))

        val row = preview.rows.single { it.localDate == "2026-08-26" }
        assertEquals("05:21:21", row.localStart)
        assertEquals("15:25:42", row.localEnd)
        assertEquals("2026-08-26", row.localEndDate)
        assertEquals("10h 4m", row.durationText)
        assertEquals("0h 0m", row.breakText)
        assertEquals("Auto Clock Out via Geofence", row.note)
        assertEquals(
            listOf(AnomalyKind.MISSED_CLOCK_OUT),
            preview.rows.single { it.localDate == "2026-09-21" }.anomalyKinds
        )
    }

    @Test
    fun reimportingTheSameFileInMergeModeAddsAndUpdatesNothing() {
        val first = CsvImportPreview.build(parseFixture(), TimeclockState(), ImportMode.MERGE, now)
        assertEquals(24, first.sessionsAdded)
        val imported = first.stateToApply

        val again = CsvImportPreview.build(parseFixture(), imported, ImportMode.MERGE, now + 1)
        assertEquals(0, again.sessionsAdded)
        assertEquals(0, again.sessionsUpdated)
        assertEquals(imported.sessions, again.stateToApply.sessions)
        // A shift is never flagged as overlapping its own earlier import.
        assertTrue(again.anomalies.none { it.kind == AnomalyKind.OVERLAPPING_ENTRIES })
    }

    @Test
    fun mergeFlagsEntriesThatOverlapShiftsAlreadyInTheApp() {
        val manual = Session(id = "sess_manual", start = at("2026-09-21 05:30:00"), end = at("2026-09-21 06:00:00"))
        val preview = CsvImportPreview.build(parseFixture(), TimeclockState(sessions = listOf(manual)), ImportMode.MERGE, now)
        val overlap = preview.anomalies.single { it.kind == AnomalyKind.OVERLAPPING_ENTRIES }
        assertEquals("2026-09-21", overlap.date)
        assertTrue(overlap.sessionIds.contains("sess_manual"))
        assertEquals(25, preview.stateToApply.sessions.size)
    }

    @Test
    fun replaceSwapsTheShiftListButKeepsRatesSettingsPtoAndTheRunningShift() {
        val settings = AppSettings(
            paySchedule = PaySchedule.BI_WEEKLY, standardShiftHours = 12.0, cliffHours = 13.0,
            otMultiplier = 1.5, geofenceEnabled = true, workLatitude = 41.5, workLongitude = -81.7
        )
        val pto = PtoEntry(id = "pto_keep", date = at("2026-09-07 00:00:00"), hours = 10.0)
        val current = TimeclockState(
            isClockedIn = true,
            currentSessionStart = at("2026-09-23 05:10:00"),
            accumulatedBreakMs = 60_000L,
            grossRate = 71.25,
            netRate = 40.5,
            displayMode = PayMode.NET,
            sessions = listOf(Session(id = "sess_old", start = at("2026-07-01 05:00:00"), end = at("2026-07-01 15:00:00"))),
            ptoEntries = listOf(pto),
            settings = settings
        )
        val parsed = parseFixture()
        val preview = CsvImportPreview.build(parsed, current, ImportMode.REPLACE, now)
        val result = preview.stateToApply

        assertEquals(parsed.sessions, result.sessions)
        assertEquals(71.25, result.grossRate, 0.0)
        assertEquals(40.5, result.netRate, 0.0)
        assertEquals(PayMode.NET, result.displayMode)
        assertEquals(settings, result.settings)
        assertEquals(listOf(pto), result.ptoEntries)
        assertTrue(result.isClockedIn)
        assertEquals(current.currentSessionStart, result.currentSessionStart)
        assertEquals(60_000L, result.accumulatedBreakMs)
        assertTrue(preview.keepsRunningShift)
        assertEquals("IMPORT_BACKUP_REPLACE", result.auditLog.first().action)
        // Everything except the shift list and the audit entry is untouched.
        assertEquals(current.copy(sessions = result.sessions, auditLog = result.auditLog), result)
        assertTrue(preview.modeStatement, preview.modeStatement.startsWith("REPLACE everything: 1 shift become 24"))
        assertTrue(preview.keepsStatement, preview.keepsStatement.contains("rates"))
    }

    // ---------------------------------------------------------------- helpers

    private fun parseFixture(): CsvParseResult.Success = parse(fixture().readText(Charsets.UTF_8))

    private fun parse(text: String): CsvParseResult.Success =
        when (val r = LegacyCsvImporter.parse(text)) {
            is CsvParseResult.Success -> r
            is CsvParseResult.Failure -> throw AssertionError("Expected a successful parse, got: ${r.reason}")
        }

    /** `yyyy-MM-dd HH:mm:ss` in the pinned zone. */
    private fun local(instantMs: Long): String {
        val c = Calendar.getInstance(ny).apply { timeInMillis = instantMs }
        return String.format(
            Locale.US, "%04d-%02d-%02d %02d:%02d:%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)
        )
    }

    private fun at(localText: String): Long {
        val (d, t) = localText.split(" ")
        val (y, mo, day) = d.split("-").map { it.toInt() }
        val (h, mi, s) = t.split(":").map { it.toInt() }
        return Calendar.getInstance(ny).apply { clear(); set(y, mo - 1, day, h, mi, s) }.timeInMillis
    }

    /**
     * Gradle runs unit tests with `app/` as the working directory; running from the repo root is
     * also supported. A test that silently skips his file proves nothing, so absence is a failure.
     */
    private fun fixture(): File {
        val name = "legacy-export-2026-09.csv"
        val candidates = listOf(File("src/test/fixtures/$name"), File("app/src/test/fixtures/$name"))
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError(
                "Cannot find the owner's CSV fixture $name. Tried: " + candidates.joinToString { it.absolutePath }
            )
    }
}
