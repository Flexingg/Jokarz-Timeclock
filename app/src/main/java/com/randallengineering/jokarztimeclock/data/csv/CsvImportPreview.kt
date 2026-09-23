package com.randallengineering.jokarztimeclock.data.csv

import com.randallengineering.jokarztimeclock.data.backup.BackupImportPlanner
import com.randallengineering.jokarztimeclock.data.backup.ImportMode
import com.randallengineering.jokarztimeclock.data.backup.ImportPlan
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.ShiftTimeMath
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * What a CSV import WOULD do, built before anything is written so the owner can read every entry
 * and every flag first. Pure: it never touches the repository; the caller applies [stateToApply]
 * through `TimeclockRepository.importBackup`, which writes to disk atomically or not at all.
 */
data class CsvImportPreview(
    val mode: ImportMode,
    /** The planner's own one-line statement of the mode — one source of truth for that wording. */
    val modeStatement: String,
    /**
     * What a CSV import keeps that the planner's backup-oriented sentence cannot know about
     * (see [build]); shown alongside [modeStatement].
     */
    val keepsStatement: String,
    val rows: List<PreviewRow>,
    val anomalies: List<Anomaly>,
    val rowsInFile: Int,
    val entriesParsed: Int,
    /** Entries carrying at least one anomaly, informational ones included. */
    val entriesFlagged: Int,
    /** Entries carrying at least one anomaly that is not merely informational. */
    val entriesNeedingAttention: Int,
    val sessionsAdded: Int,
    val sessionsUpdated: Int,
    /**
     * Read this, not `plan.keepsRunningShift`: the planner assumes a REPLACE swaps the running
     * shift for the backup's, but a CSV REPLACE carries the current one over.
     */
    val keepsRunningShift: Boolean,
    val plan: ImportPlan
) {
    val stateToApply: TimeclockState get() = plan.resultingState

    data class PreviewRow(
        val line: Int,
        val sessionId: String,
        /** Local wall-clock rendering of the stored instants (yyyy-MM-dd / HH:mm:ss). */
        val localDate: String,
        val localStart: String,
        val localEnd: String,
        /** Local date of the clock-out; differs from [localDate] only for an overnight entry. */
        val localEndDate: String,
        val durationMs: Long,
        val durationText: String,
        val breakMs: Long,
        val breakText: String,
        val note: String,
        val anomalyKinds: List<AnomalyKind>
    )

    companion object {

        fun build(
            parsed: CsvParseResult.Success,
            current: TimeclockState,
            mode: ImportMode,
            nowMs: Long,
            tz: TimeZone = TimeZone.getDefault()
        ): CsvImportPreview {
            val sessions = parsed.sessions
            // A CSV export carries shift history ONLY. The planner's REPLACE takes the incoming
            // state wholesale, so a bare TimeclockState(sessions = ...) would silently reset his
            // rates to the defaults, wipe his shift configuration and settings, delete his PTO
            // (the file has none to bring back), discard a running shift and empty the audit log.
            // So REPLACE here means "replace the shift list": everything else is carried over from
            // the current state. copy() rather than listing fields so a field added to
            // TimeclockState later is carried over too instead of silently defaulting.
            // MERGE already keeps all of that; its incoming state must NOT carry the current audit
            // log, or the planner would concatenate it with itself.
            val incoming = when (mode) {
                ImportMode.REPLACE -> current.copy(sessions = sessions)
                ImportMode.MERGE -> TimeclockState(sessions = sessions)
            }
            val plan = BackupImportPlanner.plan(current, incoming, mode, nowMs)

            val anomalies = (parsed.anomalies + overlapsWithExisting(parsed, current, mode))
                .sortedBy { it.line }
            val kindsById = HashMap<String, MutableList<AnomalyKind>>()
            for (a in anomalies) for (id in a.sessionIds) kindsById.getOrPut(id) { mutableListOf() } += a.kind

            val rows = parsed.entries.map { e ->
                val s = e.session
                PreviewRow(
                    line = e.line,
                    sessionId = s.id,
                    localDate = formatDate(s.start, tz),
                    localStart = formatTime(s.start, tz),
                    localEnd = formatTime(s.end, tz),
                    localEndDate = formatDate(s.end, tz),
                    durationMs = e.durationMs,
                    durationText = ShiftTimeMath.formatDurationShort(e.durationMs),
                    breakMs = s.breakMs,
                    breakText = ShiftTimeMath.formatDurationShort(s.breakMs),
                    note = s.note,
                    anomalyKinds = kindsById[s.id].orEmpty().distinct()
                )
            }

            return CsvImportPreview(
                mode = mode,
                modeStatement = plan.summary,
                keepsStatement = when (mode) {
                    ImportMode.REPLACE ->
                        "Only your shift list is replaced by the file's ${sessions.size} entries: your rates, " +
                            "settings, PTO and any running shift are kept, because the file holds none of them."
                    ImportMode.MERGE ->
                        "The file's entries are added to your shifts: your rates, settings, PTO and any " +
                            "running shift are kept."
                },
                rows = rows,
                anomalies = anomalies,
                rowsInFile = parsed.rowsInFile,
                entriesParsed = parsed.entries.size,
                entriesFlagged = rows.count { it.anomalyKinds.isNotEmpty() },
                entriesNeedingAttention = rows.count { r -> r.anomalyKinds.any { !it.informational } },
                sessionsAdded = plan.sessionsAdded,
                sessionsUpdated = plan.sessionsUpdated,
                keepsRunningShift = current.isClockedIn &&
                    plan.resultingState.currentSessionStart == current.currentSessionStart,
                plan = plan
            )
        }

        /**
         * Under MERGE the imported entries sit next to shifts already in the app; one that overlaps
         * an existing shift (other than its own earlier import, same id) would be paid twice.
         * REPLACE removes the existing shifts, so there is nothing to collide with.
         */
        private fun overlapsWithExisting(
            parsed: CsvParseResult.Success,
            current: TimeclockState,
            mode: ImportMode
        ): List<Anomaly> {
            if (mode != ImportMode.MERGE) return emptyList()
            val incomingIds = parsed.entries.mapTo(HashSet()) { it.session.id }
            val existing = current.sessions.filter { it.id !in incomingIds }
            val out = mutableListOf<Anomaly>()
            for (e in parsed.entries) {
                for (s in existing) {
                    if (e.session.start < s.end && s.start < e.session.end) {
                        out += Anomaly(
                            AnomalyKind.OVERLAPPING_ENTRIES,
                            e.line,
                            e.date,
                            listOf(e.session.id, s.id),
                            "${e.date}: ${e.startText}–${e.endText} (line ${e.line}) overlaps a shift already " +
                                "in the app. Both are kept, so the overlap would be counted twice until you fix one."
                        )
                    }
                }
            }
            return out
        }

        private fun formatDate(instantMs: Long, tz: TimeZone): String {
            val c = Calendar.getInstance(tz).apply { timeInMillis = instantMs }
            return String.format(
                Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
            )
        }

        private fun formatTime(instantMs: Long, tz: TimeZone): String {
            val c = Calendar.getInstance(tz).apply { timeInMillis = instantMs }
            return String.format(
                Locale.US, "%02d:%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)
            )
        }
    }
}
