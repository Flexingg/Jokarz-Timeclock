package com.randallengineering.jokarztimeclock.data.backup

import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Sanity checks for a state read from a backup file, before it is allowed anywhere near the live data.
 * Returns human-readable problems; an empty list means the state is safe to import.
 */
object BackupValidator {

    private const val MAX_MESSAGES = 5
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 2000-01-01T00:00Z — nothing older can be a real shift from this app. */
    private const val EARLIEST_MS = 946_684_800_000L

    fun validate(
        state: TimeclockState,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<String> {
        // Gson fills Kotlin's non-null fields with null when the JSON says `null` (or, for classes
        // without a no-arg constructor, when the key is missing). Kotlin cannot see that, so every
        // check below goes through gsonNull(), whose Any? parameter stops the compiler from folding
        // the null test away.
        val structural = buildList {
            if (gsonNull(state.sessions)) add("The backup has no shift list (\"sessions\" is null).")
            if (gsonNull(state.ptoEntries)) add("The backup has no PTO list (\"ptoEntries\" is null).")
            if (gsonNull(state.settings)) add("The backup has no settings (\"settings\" is null).")
            if (gsonNull(state.auditLog)) add("The backup has no audit log (\"auditLog\" is null).")
            if (gsonNull(state.displayMode)) add("The backup has an unknown pay display mode.")
        }
        if (structural.isNotEmpty()) return structural

        val problems = mutableListOf<String>()
        val latestMs = nowMs + DAY_MS
        fun fmt(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()
            .truncatedTo(ChronoUnit.MINUTES).toString()

        state.sessions.forEachIndexed { i, s ->
            val n = i + 1
            if (gsonNull(s)) {
                problems += "Session #$n is empty."
                return@forEachIndexed
            }
            val label = "Session #$n (start ${fmt(s.start)}, end ${fmt(s.end)})"
            when {
                gsonNull(s.id) || s.id.isBlank() -> problems += "Session #$n has no id."
                gsonNull(s.note) || gsonNull(s.jobCode) -> problems += "Session #$n is missing its note/job code fields."
                s.end <= s.start -> problems += "$label: the stop is at or before the start."
                s.breakMs < 0 -> problems += "$label: the break is negative."
                s.breakMs >= s.end - s.start -> problems += "$label: the break is as long as the shift."
                s.start < EARLIEST_MS || s.end < EARLIEST_MS -> problems += "$label: dated before 2000."
                s.start >= latestMs || s.end >= latestMs -> problems += "$label: dated in the future."
            }
        }

        state.ptoEntries.forEachIndexed { i, p ->
            val n = i + 1
            when {
                gsonNull(p) -> problems += "PTO entry #$n is empty."
                gsonNull(p.id) || p.id.isBlank() -> problems += "PTO entry #$n has no id."
                gsonNull(p.type) -> problems += "PTO entry #$n (${fmt(p.date)}) has an unknown type."
                p.hours !in 0.0..24.0 -> problems += "PTO entry #$n (${fmt(p.date)}): ${p.hours} hours is outside 0–24."
            }
        }

        val st = state.settings
        if (gsonNull(st.theme) || gsonNull(st.paySchedule)) {
            problems += "Settings: unknown theme or pay schedule."
        }
        if (st.standardShiftHours !in 1.0..24.0) {
            problems += "Settings: standard shift of ${st.standardShiftHours} h is outside 1–24."
        } else if (st.cliffHours !in st.standardShiftHours..24.0) {
            problems += "Settings: OT cliff of ${st.cliffHours} h must be between the standard shift and 24."
        }
        if (st.otMultiplier !in 0.5..3.0) problems += "Settings: OT multiplier ${st.otMultiplier} is outside 0.5–3.0."
        if (st.unpaidMealDuration !in 0.0..4.0) problems += "Settings: meal duration ${st.unpaidMealDuration} h is outside 0–4."

        // Written as !(x >= 0) so NaN is rejected too.
        if (!(state.grossRate >= 0.0)) problems += "Gross rate ${state.grossRate} must be zero or more."
        if (!(state.netRate >= 0.0)) problems += "Net rate ${state.netRate} must be zero or more."

        return if (problems.size <= MAX_MESSAGES) problems
        else problems.take(MAX_MESSAGES) + "…and ${problems.size - MAX_MESSAGES} more problem(s)."
    }

    private fun gsonNull(value: Any?): Boolean = value == null
}
