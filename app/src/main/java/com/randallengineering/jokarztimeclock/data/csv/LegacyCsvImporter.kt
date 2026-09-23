package com.randallengineering.jokarztimeclock.data.csv

import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.engine.ShiftTimeMath
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Reads the owner's legacy payroll export
 * (`Date,Day,Start Time,End Time,Break (Mins),Tech Duration (Hours),Tech Duration (Formatted),Notes`)
 * into [Session]s. Pure JVM, no Android, so it is unit-testable against his real file.
 *
 * Rules that protect his numbers:
 *  - Duration is always recomputed as `end - start`. The export's `Tech Duration` columns are never
 *    used to set, round or adjust a duration; the hours column is read only as a cross-check so a
 *    row whose own figure disagrees can be flagged ([AnomalyKind.MISSED_CLOCK_OUT]).
 *  - Every row becomes its own entry: split shifts, 2-minute geofence blips and overlaps are all
 *    imported and, where odd, flagged — never merged, deduped or dropped.
 *  - One unreadable row fails the WHOLE parse. A partial list would be a partial import, which is
 *    exactly what the atomic apply path exists to prevent.
 */
object LegacyCsvImporter {

    /** A recomputed shift longer than this is flagged [AnomalyKind.IMPLAUSIBLE_DURATION]. */
    const val IMPLAUSIBLE_AFTER_MS = 16L * ShiftTimeMath.MS_PER_HOUR

    /** An entry shorter than this is flagged [AnomalyKind.VERY_SHORT_ENTRY] (still imported). */
    const val VERY_SHORT_UNDER_MS = 5L * ShiftTimeMath.MS_PER_MINUTE

    /**
     * How far the export's own `Tech Duration (Hours)` may disagree with the recomputed duration
     * before the row is flagged [AnomalyKind.MISSED_CLOCK_OUT]. Wide enough that the export's
     * 2-decimal rounding never trips it.
     */
    const val EXPORT_MISMATCH_TOLERANCE_MS = 60L * ShiftTimeMath.MS_PER_MINUTE

    /** Prefix of every id this importer creates; the rest is derived from the entry's content. */
    const val ID_PREFIX = "csv_"

    private const val COL_DATE = "date"
    private const val COL_START = "start time"
    private const val COL_END = "end time"
    private const val COL_BREAK = "break (mins)"
    private const val COL_HOURS = "tech duration (hours)"
    private const val COL_FORMATTED = "tech duration (formatted)"
    private const val COL_NOTES = "notes"

    private val DATE = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val TIME = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")

    /**
     * Parses [text] (the whole file, already decoded as UTF-8). Instants are built as local
     * wall-clock times in [tz], the same way the rest of the app stores them.
     *
     * Note the ids depend on [tz]: re-importing the same file on a phone set to another zone yields
     * different instants and therefore different ids — correctly, since they are different instants.
     */
    fun parse(text: String, tz: TimeZone = TimeZone.getDefault()): CsvParseResult {
        val records = when (val split = CsvRecords.split(text)) {
            is CsvRecords.Result.Failure -> return CsvParseResult.Failure(split.line, split.reason)
            is CsvRecords.Result.Ok -> split.records.filterNot { it.isBlank() }
        }
        val header = records.firstOrNull()
            ?: return CsvParseResult.Failure(1, "The file is empty — there is no header row and no shifts.")

        val columns = header.fields.map { it.trim().lowercase(Locale.ROOT) }
        val missing = listOf(COL_DATE, COL_START, COL_END).filter { it !in columns }
        if (missing.isNotEmpty()) {
            return CsvParseResult.Failure(
                header.line,
                "Line ${header.line} is not a recognised header: it has no " +
                    missing.joinToString(" or ") { "\"${displayName(it)}\"" } + " column. " +
                    "Nothing was imported."
            )
        }
        fun indexOf(name: String) = columns.indexOf(name).takeIf { it >= 0 }
        val iDate = indexOf(COL_DATE)!!
        val iStart = indexOf(COL_START)!!
        val iEnd = indexOf(COL_END)!!
        val iBreak = indexOf(COL_BREAK)
        val iHours = indexOf(COL_HOURS)
        val iFormatted = indexOf(COL_FORMATTED)
        val iNotes = indexOf(COL_NOTES)

        val entries = mutableListOf<CsvEntry>()
        val anomalies = mutableListOf<Anomaly>()
        val usedIds = HashMap<String, Int>()

        for (record in records.drop(1)) {
            val line = record.line
            fun fail(reason: String) = CsvParseResult.Failure(line, "Line $line: $reason Nothing was imported.")

            // A row with a different field count is either half-written or has an unquoted comma
            // that shifted every column after it; both would put wrong values in wrong fields.
            if (record.fields.size != columns.size) {
                return fail(
                    "expected ${columns.size} fields like the header, found ${record.fields.size} " +
                        "(the row is truncated or has an unquoted comma)."
                )
            }
            val f = record.fields
            val dateText = f[iDate].trim()
            val date = parseDate(dateText) ?: return fail("\"$dateText\" is not a date in yyyy-MM-dd form.")
            val startTime = parseTime(f[iStart].trim())
                ?: return fail("start time \"${f[iStart].trim()}\" is not HH:mm:ss or HH:mm.")
            val endTime = parseTime(f[iEnd].trim())
                ?: return fail("end time \"${f[iEnd].trim()}\" is not HH:mm:ss or HH:mm.")
            val breakMs = if (iBreak == null) 0L else {
                parseBreakMs(f[iBreak].trim())
                    ?: return fail("break \"${f[iBreak].trim()}\" is not a number of minutes.")
            }

            val startMs = localInstant(date, startTime, tz)
            val sameDayEndMs = localInstant(date, endTime, tz)
            // A clock-out at or before the clock-in can only mean the next day; reading it on the
            // same day would produce a negative (or zero) shift. Calendar day arithmetic, not +24h,
            // so the wall-clock time survives a DST change.
            val rolled = sameDayEndMs <= startMs
            val endMs = if (rolled) ShiftTimeMath.addDays(sameDayEndMs, 1, tz) else sameDayEndMs

            val baseId = "$ID_PREFIX${startMs}_$endMs"
            // Identical rows are still two entries; the occurrence suffix keeps their ids distinct
            // and stable across re-imports of the same file.
            val occurrence = (usedIds[baseId] ?: 0) + 1
            usedIds[baseId] = occurrence
            val id = if (occurrence == 1) baseId else "${baseId}_$occurrence"

            val entry = CsvEntry(
                line = line,
                date = dateText,
                startText = f[iStart].trim(),
                endText = f[iEnd].trim(),
                session = Session(
                    id = id,
                    start = startMs,
                    end = endMs,
                    breakMs = breakMs,
                    note = if (iNotes == null) "" else f[iNotes]
                ),
                endRolledToNextDay = rolled,
                exportHours = iHours?.let { f[it].trim().toDoubleOrNull() },
                exportFormatted = iFormatted?.let { f[it].trim() }?.takeIf { it.isNotEmpty() }
            )
            entries += entry
            anomalies += rowAnomalies(entry, tz)
        }

        anomalies += overlaps(entries)
        return CsvParseResult.Success(
            rowsInFile = records.size - 1,
            entries = entries,
            anomalies = anomalies.sortedBy { it.line }
        )
    }

    // ---------------------------------------------------------------- anomalies

    private fun rowAnomalies(e: CsvEntry, tz: TimeZone): List<Anomaly> {
        val out = mutableListOf<Anomaly>()
        val duration = e.durationMs
        val span = "${e.startText} to ${e.endText}"
        fun add(kind: AnomalyKind, message: String) = out.add(Anomaly(kind, e.line, e.date, listOf(e.session.id), message))

        if (e.endRolledToNextDay) {
            add(
                AnomalyKind.OVERNIGHT_ROLLED,
                "${e.date}: the clock-out ${e.endText} is not after the clock-in ${e.startText}, so it " +
                    "was read as ${formatDate(e.session.end, tz)} (the next day), making the shift " +
                    "${ShiftTimeMath.formatDurationShort(duration)}."
            )
        }
        val hours = e.exportHours
        if (hours != null && hours.isFinite()) {
            val exportMs = (hours * ShiftTimeMath.MS_PER_HOUR).roundToLong()
            // The export may have reported either clocked time or time net of the break; only a
            // disagreement with both counts.
            val offGross = abs(exportMs - duration) > EXPORT_MISMATCH_TOLERANCE_MS
            val offNet = abs(exportMs - (duration - e.session.breakMs)) > EXPORT_MISMATCH_TOLERANCE_MS
            if (offGross && offNet) {
                val fileSays = e.exportFormatted?.let { "$it (${formatHours(hours)} h)" } ?: "${formatHours(hours)} h"
                add(
                    AnomalyKind.MISSED_CLOCK_OUT,
                    "${e.date}: the file says this shift lasted $fileSays, but $span is " +
                        "${ShiftTimeMath.formatDurationShort(duration)} — you probably missed a clock-out. " +
                        "It is imported as ${ShiftTimeMath.formatDurationShort(duration)}; nothing was " +
                        "corrected for you, so fix it by hand if the file's figure was right."
                )
            }
        }
        if (duration > IMPLAUSIBLE_AFTER_MS) {
            add(
                AnomalyKind.IMPLAUSIBLE_DURATION,
                "${e.date}: $span is ${ShiftTimeMath.formatDurationShort(duration)}, longer than " +
                    "${ShiftTimeMath.formatDurationShort(IMPLAUSIBLE_AFTER_MS)} — check the clock-out time."
            )
        }
        if (duration < VERY_SHORT_UNDER_MS) {
            add(
                AnomalyKind.VERY_SHORT_ENTRY,
                "${e.date}: $span is only ${formatMinSec(duration)} — probably a geofence blip. " +
                    "It is imported as-is; delete it if it was not real work."
            )
        }
        return out
    }

    /** Every pair of entries whose instants overlap (touching end-to-start is not an overlap). */
    private fun overlaps(entries: List<CsvEntry>): List<Anomaly> {
        val sorted = entries.sortedBy { it.session.start }
        val out = mutableListOf<Anomaly>()
        for (i in sorted.indices) {
            val a = sorted[i]
            var j = i + 1
            while (j < sorted.size && sorted[j].session.start < a.session.end) {
                val b = sorted[j]
                val (first, second) = if (a.line <= b.line) a to b else b to a
                out += Anomaly(
                    AnomalyKind.OVERLAPPING_ENTRIES,
                    first.line,
                    first.date,
                    listOf(first.session.id, second.session.id),
                    "${first.date}: ${first.startText}–${first.endText} (line ${first.line}) overlaps " +
                        "${second.date} ${second.startText}–${second.endText} (line ${second.line}). Both " +
                        "are imported, so the overlap would be counted twice until you fix one."
                )
                j++
            }
        }
        return out
    }

    // ---------------------------------------------------------------- field parsing

    private class LocalDate(val year: Int, val month: Int, val day: Int)
    private class LocalTime(val hour: Int, val minute: Int, val second: Int)

    private fun parseDate(s: String): LocalDate? {
        val m = DATE.matchEntire(s) ?: return null
        val (y, mo, d) = m.destructured
        val year = y.toInt(); val month = mo.toInt(); val day = d.toInt()
        if (month !in 1..12 || day < 1) return null
        // Reject 2026-02-30 instead of letting a lenient Calendar roll it into March.
        val maxDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(year, month - 1, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
        return if (day > maxDay) null else LocalDate(year, month, day)
    }

    private fun parseTime(s: String): LocalTime? {
        val m = TIME.matchEntire(s) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val sec = m.groupValues[3].ifEmpty { "0" }.toInt()
        return if (h in 0..23 && min in 0..59 && sec in 0..59) LocalTime(h, min, sec) else null
    }

    /** Blank means no break. Negative or non-numeric fails the parse rather than guessing. */
    private fun parseBreakMs(s: String): Long? {
        if (s.isEmpty()) return 0L
        val minutes = s.toDoubleOrNull() ?: return null
        if (!minutes.isFinite() || minutes < 0) return null
        return (minutes * ShiftTimeMath.MS_PER_MINUTE).roundToLong()
    }

    /**
     * Local wall-clock → instant via [Calendar], like [ShiftTimeMath.applyPickedTime]: a time in
     * the spring-forward gap rolls forward, a fall-back time resolves to the first occurrence.
     */
    private fun localInstant(d: LocalDate, t: LocalTime, tz: TimeZone): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(d.year, d.month - 1, d.day, t.hour, t.minute, t.second)
        }.timeInMillis

    private fun displayName(col: String) = when (col) {
        COL_DATE -> "Date"
        COL_START -> "Start Time"
        COL_END -> "End Time"
        else -> col
    }

    private fun formatDate(instantMs: Long, tz: TimeZone): String {
        val c = Calendar.getInstance(tz).apply { timeInMillis = instantMs }
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun formatHours(h: Double) = String.format(Locale.US, "%.2f", h)

    private fun formatMinSec(ms: Long): String {
        val secs = ms / 1000L
        return "${secs / 60L}m ${secs % 60L}s"
    }
}

/** One imported row. [session] carries the recomputed instants; the rest is for the preview. */
data class CsvEntry(
    /** 1-based physical line in the file where the row starts. */
    val line: Int,
    /** The row's `Date` exactly as written (yyyy-MM-dd). */
    val date: String,
    val startText: String,
    val endText: String,
    val session: Session,
    val endRolledToNextDay: Boolean,
    /** The export's own `Tech Duration (Hours)`, a flag hint only; null when absent/unreadable. */
    val exportHours: Double?,
    val exportFormatted: String?
) {
    val durationMs: Long get() = ShiftTimeMath.durationMs(session.start, session.end)
}

enum class AnomalyKind(
    /** True when the flag is only for visibility and needs no action. */
    val informational: Boolean
) {
    /** The export's own duration disagrees with the recomputed one by more than the tolerance. */
    MISSED_CLOCK_OUT(false),
    /** Longer than [LegacyCsvImporter.IMPLAUSIBLE_AFTER_MS]. */
    IMPLAUSIBLE_DURATION(false),
    /** The clock-out was not after the clock-in, so it was read as the next day. */
    OVERNIGHT_ROLLED(true),
    /** Shorter than [LegacyCsvImporter.VERY_SHORT_UNDER_MS]; likely a geofence blip. */
    VERY_SHORT_ENTRY(true),
    /** Two entries cover overlapping instants. */
    OVERLAPPING_ENTRIES(false)
}

data class Anomaly(
    val kind: AnomalyKind,
    /** Line of the (first) row concerned. */
    val line: Int,
    val date: String,
    /** Ids of the entries concerned (two for an overlap). */
    val sessionIds: List<String>,
    /** Plain language, safe to show to the user verbatim. */
    val message: String
)

sealed class CsvParseResult {
    data class Success(
        /** Non-blank data rows in the file; equals `entries.size` on success. */
        val rowsInFile: Int,
        val entries: List<CsvEntry>,
        val anomalies: List<Anomaly>
    ) : CsvParseResult() {
        val sessions: List<Session>
            get() = entries.map { it.session }
    }

    /** Nothing was parsed; [reason] names the line and is safe to show verbatim. */
    data class Failure(val line: Int, val reason: String) : CsvParseResult()
}

/**
 * RFC 4180-style record splitter: quoted fields may contain commas, `""` escapes and even line
 * breaks; CRLF and LF are both accepted; a leading UTF-8 BOM is dropped. Records keep the physical
 * line they start on so errors can name it.
 */
internal object CsvRecords {

    class Record(val line: Int, val fields: List<String>) {
        /** A blank line splits into one empty field; so does a whitespace-only line. */
        fun isBlank() = fields.size == 1 && fields[0].isBlank()
    }

    sealed class Result {
        class Ok(val records: List<Record>) : Result()
        class Failure(val line: Int, val reason: String) : Result()
    }

    fun split(input: String): Result {
        val text = input.removePrefix("﻿")
        val records = mutableListOf<Record>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var line = 1
        var recordLine = 1
        var inQuotes = false
        var quoteLine = 0
        var fieldWasQuoted = false
        var i = 0

        fun endRecord() {
            fields.add(field.toString())
            records.add(Record(recordLine, fields))
            fields = mutableListOf()
            field.setLength(0)
            fieldWasQuoted = false
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> {
                        if (c == '\n') line++
                        field.append(c)
                    }
                }
            } else {
                when (c) {
                    '"' -> {
                        // A quote may only open a field; `ab"c` or `"a"b` means the writer was
                        // interrupted or broken, and guessing would misplace text.
                        if (field.isNotEmpty() || fieldWasQuoted) {
                            return Result.Failure(line, "Line $line: a stray quote mark inside a field. Nothing was imported.")
                        }
                        inQuotes = true
                        fieldWasQuoted = true
                        quoteLine = line
                    }
                    ',' -> { fields.add(field.toString()); field.setLength(0); fieldWasQuoted = false }
                    '\r' -> if (i + 1 < text.length && text[i + 1] == '\n') Unit else {
                        endRecord(); line++; recordLine = line
                    }
                    '\n' -> { endRecord(); line++; recordLine = line }
                    else -> {
                        if (fieldWasQuoted && !c.isWhitespace()) {
                            return Result.Failure(line, "Line $line: text after a closing quote. Nothing was imported.")
                        }
                        if (!fieldWasQuoted) field.append(c)
                    }
                }
            }
            i++
        }
        if (inQuotes) {
            return Result.Failure(
                quoteLine,
                "Line $quoteLine: a quoted field is never closed — the file looks truncated. Nothing was imported."
            )
        }
        // No trailing newline: the last record still counts. A trailing newline leaves nothing behind.
        if (fields.isNotEmpty() || field.isNotEmpty() || fieldWasQuoted) endRecord()
        return Result.Ok(records)
    }
}
