package com.randallengineering.jokarztimeclock.data.backup

import com.randallengineering.jokarztimeclock.data.models.AuditEntry
import com.randallengineering.jokarztimeclock.data.models.TimeclockState

enum class ImportMode { REPLACE, MERGE }

data class ImportPlan(
    val mode: ImportMode,
    val resultingState: TimeclockState,
    val sessionsAdded: Int, val sessionsUpdated: Int,
    val ptoAdded: Int, val ptoUpdated: Int,
    /** True only when there is a running shift and it survives the import. */
    val keepsRunningShift: Boolean,
    val summary: String            // one sentence, shown to the user BEFORE applying
)

/**
 * Works out what an import would do, without doing it, so the user sees the consequences (especially
 * for a running shift) before anything is written. Pure: the only clock is [plan]'s `nowMs`.
 */
object BackupImportPlanner {

    private const val AUDIT_CAP = 50

    fun plan(current: TimeclockState, incoming: TimeclockState, mode: ImportMode, nowMs: Long): ImportPlan {
        val sessions = mergeById(current.sessions, incoming.sessions) { it.id }
        val pto = mergeById(current.ptoEntries, incoming.ptoEntries) { it.id }

        return when (mode) {
            ImportMode.REPLACE -> {
                val runningShiftClause = when {
                    current.isClockedIn && incoming.isClockedIn ->
                        ", and your running shift will be replaced by the one saved in the backup."
                    current.isClockedIn -> ", and the running shift will be ended."
                    incoming.isClockedIn -> ", and the running shift saved in the backup will be resumed."
                    else -> "."
                }
                val summary = "REPLACE everything: ${shifts(current.sessions.size)} become " +
                    "${incoming.sessions.size}, ${ptoEntries(current.ptoEntries.size)} become " +
                    "${incoming.ptoEntries.size}, settings and rates come from the backup$runningShiftClause"
                ImportPlan(
                    mode = mode,
                    resultingState = incoming.copy(
                        auditLog = withImportAudit(incoming.auditLog, mode, nowMs)
                    ),
                    sessionsAdded = sessions.added,
                    sessionsUpdated = sessions.updated,
                    ptoAdded = pto.added,
                    ptoUpdated = pto.updated,
                    // The running shift is part of the state being replaced, so it never survives REPLACE.
                    keepsRunningShift = false,
                    summary = summary
                )
            }

            ImportMode.MERGE -> {
                val summary = "MERGE by id: ${sessions.added} new ${plural(sessions.added, "shift")} will be added, " +
                    "${sessions.updated} existing ${plural(sessions.updated, "shift")} will be updated, " +
                    "${pto.added} new and ${pto.updated} changed PTO ${plural(pto.added + pto.updated, "entry", "entries")}, " +
                    "your settings stay" +
                    if (current.isClockedIn) ", and your running shift is kept." else "."
                ImportPlan(
                    mode = mode,
                    // Only history is merged: settings, rates and the running shift all stay as they are.
                    resultingState = current.copy(
                        sessions = sessions.items,
                        ptoEntries = pto.items,
                        auditLog = withImportAudit(incoming.auditLog + current.auditLog, mode, nowMs)
                    ),
                    sessionsAdded = sessions.added,
                    sessionsUpdated = sessions.updated,
                    ptoAdded = pto.added,
                    ptoUpdated = pto.updated,
                    keepsRunningShift = current.isClockedIn,
                    summary = summary
                )
            }
        }
    }

    private class Merged<T>(val items: List<T>, val added: Int, val updated: Int)

    /**
     * Current order first, then new ids in incoming order. An id already present is replaced in place,
     * and only counts as "updated" when its content actually differs — re-importing the same backup
     * should report zero changes, not "40 updated".
     */
    private fun <T> mergeById(current: List<T>, incoming: List<T>, id: (T) -> String): Merged<T> {
        val result = current.toMutableList()
        val indexById = HashMap<String, Int>()
        current.forEachIndexed { i, item -> indexById.putIfAbsent(id(item), i) }
        val updatedIndices = HashSet<Int>()
        var added = 0
        for (item in incoming) {
            val i = indexById[id(item)]
            if (i == null) {
                indexById[id(item)] = result.size
                result += item
                added++
            } else if (result[i] != item) {
                result[i] = item
                if (i < current.size) updatedIndices += i
            }
        }
        return Merged(result, added, updatedIndices.size)
    }

    // Every other mutation in TimeclockRepository leaves an audit entry; an import must too.
    private fun withImportAudit(log: List<AuditEntry>, mode: ImportMode, nowMs: Long): List<AuditEntry> =
        listOf(AuditEntry(action = "IMPORT_BACKUP_$mode", timestamp = nowMs)) + log.take(AUDIT_CAP - 1)

    private fun shifts(n: Int) = "$n ${plural(n, "shift")}"
    private fun ptoEntries(n: Int) = "$n PTO ${plural(n, "entry", "entries")}"
    private fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) one else many
}
