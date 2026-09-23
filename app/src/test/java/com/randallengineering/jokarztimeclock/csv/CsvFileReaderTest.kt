package com.randallengineering.jokarztimeclock.csv

import com.randallengineering.jokarztimeclock.data.backup.ImportMode
import com.randallengineering.jokarztimeclock.data.csv.AnomalyKind
import com.randallengineering.jokarztimeclock.data.csv.CsvFileReader
import com.randallengineering.jokarztimeclock.data.csv.CsvImportPreview
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.TimeZone

/**
 * What the Settings "Choose CSV file" button does with the picked file's bytes before the preview:
 * either every entry reaches the preview, or a plain reason is shown and nothing can be applied.
 */
class CsvFileReaderTest {

    private val ny = TimeZone.getTimeZone("America/New_York")
    private val header = "Date,Day,Start Time,End Time,Break (Mins),Tech Duration (Hours),Tech Duration (Formatted),Notes\n"

    private fun ready(result: CsvFileReader.Result): CsvFileReader.Result.Ready =
        result as? CsvFileReader.Result.Ready ?: throw AssertionError("expected Ready, got $result")

    private fun refused(result: CsvFileReader.Result): String =
        (result as? CsvFileReader.Result.Refused)?.reason ?: throw AssertionError("expected Refused, got $result")

    @Test
    fun realFileReachesThePreviewWithEveryEntryAndFlag() {
        val parsed = ready(CsvFileReader.read(fixture().inputStream(), ny)).parsed
        assertEquals(24, parsed.entries.size)

        val preview = CsvImportPreview.build(parsed, TimeclockState(), ImportMode.MERGE, 1_790_000_000_000L, ny)
        // Nothing is summarised away: one preview row per entry, in file order.
        assertEquals(parsed.entries.map { it.line }, preview.rows.map { it.line })
        // Split shifts on the same day are both there.
        assertEquals(2, preview.rows.count { it.localDate == "2026-09-08" })
        assertEquals(2, preview.rows.count { it.localDate == "2026-09-22" })
        // The 2-3 minute geofence blips are there, with their notes.
        val blip = preview.rows.single { it.localDate == "2026-09-22" && it.localStart == "05:11:13" }
        assertEquals("05:14:07", blip.localEnd)
        assertEquals("Auto Clock Out via Geofence", blip.note)
        assertTrue(AnomalyKind.VERY_SHORT_ENTRY in blip.anomalyKinds)
        // The missed clock-out on 2026-09-21 is flagged on its row and has a message to show.
        val missed = preview.rows.single { it.localDate == "2026-09-21" }
        assertTrue(AnomalyKind.MISSED_CLOCK_OUT in missed.anomalyKinds)
        assertTrue(preview.anomalies.any { it.kind == AnomalyKind.MISSED_CLOCK_OUT && it.date == "2026-09-21" && it.message.isNotBlank() })
        // What would be applied carries exactly the entries shown.
        assertEquals(preview.rows.map { it.sessionId }, preview.stateToApply.sessions.map { it.id })
    }

    @Test
    fun streamDeliveredInSmallChunksIsReadWhole() {
        val bytes = fixture().readBytes()
        val trickle = object : InputStream() {
            private var i = 0
            override fun read(): Int = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (i >= bytes.size) return -1
                val n = minOf(len, 7, bytes.size - i)
                System.arraycopy(bytes, i, b, off, n)
                i += n
                return n
            }
        }
        assertEquals(24, ready(CsvFileReader.read(trickle, ny)).parsed.entries.size)
    }

    @Test
    fun fileThatCannotBeOpenedIsRefused() {
        assertTrue(refused(CsvFileReader.read(null, ny)).contains("could not be opened"))
    }

    @Test
    fun fileOverTheCapIsRefusedWithoutParsing() {
        val row = "\"2026-09-01\",\"Tue\",\"05:00:00\",\"15:00:00\",0,10.00,\"10h 0m\",\"\"\n"
        val big = StringBuilder(header)
        while (big.length <= CsvFileReader.MAX_CSV_BYTES) big.append(row)
        val reason = refused(CsvFileReader.read(ByteArrayInputStream(big.toString().toByteArray()), ny))
        assertTrue(reason, reason.contains("too large"))
    }

    @Test
    fun notUtf8IsRefusedRatherThanImportedWithMangledNotes() {
        // "Café" in Windows-1252: 0xE9 alone is not valid UTF-8.
        val bytes = (header + "\"2026-09-01\",\"Tue\",\"05:00:00\",\"15:00:00\",0,10.00,\"10h 0m\",\"Caf").toByteArray() +
            byteArrayOf(0xE9.toByte()) + "\"\n".toByteArray()
        assertTrue(refused(CsvFileReader.decode(bytes, ny)).contains("UTF-8"))
    }

    @Test
    fun oneBadRowRefusesTheWholeFileWithTheImportersReason() {
        val text = header +
            "\"2026-09-01\",\"Tue\",\"05:00:00\",\"15:00:00\",0,10.00,\"10h 0m\",\"\"\n" +
            "\"2026-09-02\",\"Wed\",\"05:00:00\"\n"
        val reason = refused(CsvFileReader.decode(text.toByteArray(), ny))
        assertTrue(reason, reason.startsWith("Line 3:"))
        assertTrue(reason, reason.contains("Nothing was imported"))
    }

    @Test
    fun headerOnlyFileIsRefusedSoReplaceCannotEmptyTheShiftList() {
        val reason = refused(CsvFileReader.decode(header.toByteArray(), ny))
        assertTrue(reason, reason.contains("no shifts"))
    }

    @Test
    fun replacePreviewKeepsEverythingButTheShiftList() {
        val parsed = ready(CsvFileReader.read(fixture().inputStream(), ny)).parsed
        val current = TimeclockState(sessions = listOf(Session(id = "old", start = 1_000L, end = 2_000L)))
        val preview = CsvImportPreview.build(parsed, current, ImportMode.REPLACE, 1_790_000_000_000L, ny)
        assertEquals(24, preview.stateToApply.sessions.size)
        assertTrue(preview.stateToApply.sessions.none { it.id == "old" })
        assertEquals(current.settings, preview.stateToApply.settings)
        assertTrue(preview.modeStatement.startsWith("REPLACE"))
    }

    private fun fixture(): File {
        val name = "legacy-export-2026-09.csv"
        val candidates = listOf(File("src/test/fixtures/$name"), File("app/src/test/fixtures/$name"))
        return candidates.firstOrNull { it.isFile }
            ?: run { fail("Cannot find the owner's CSV fixture $name"); throw IllegalStateException() }
    }
}
