package com.randallengineering.jokarztimeclock.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.AuditEntry
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.PtoEntry
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.data.models.UndoAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class TimeclockRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val stateFile: File = File(context.filesDir, "timeclock_state_v2.json")
    private val legacyFile: File = File(context.filesDir, "timeclock_state.json")

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<TimeclockState> = _state.asStateFlow()

    private val undoStack = mutableListOf<UndoAction>()

    private fun loadState(): TimeclockState {
        return try {
            val fileToRead = if (stateFile.exists()) stateFile else if (legacyFile.exists()) legacyFile else null
            if (fileToRead != null && fileToRead.exists()) {
                val json = fileToRead.readText()
                gson.fromJson(json, TimeclockState::class.java) ?: TimeclockState()
            } else {
                TimeclockState()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            TimeclockState()
        }
    }

    private fun persist(newState: TimeclockState) {
        try {
            val json = gson.toJson(newState)
            stateFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateState(transform: (TimeclockState) -> TimeclockState) {
        val current = _state.value
        val next = transform(current)
        _state.value = next
        persist(next)
    }

    fun setMode(mode: PayMode) {
        updateState { it.copy(displayMode = mode) }
    }

    fun setRate(mode: PayMode, rate: Double) {
        val validRate = maxOf(0.0, rate)
        updateState {
            if (mode == PayMode.GROSS) it.copy(grossRate = validRate)
            else it.copy(netRate = validRate)
        }
    }

    fun updateSettings(settings: AppSettings) {
        updateState { it.copy(settings = settings) }
    }

    fun clockIn() {
        if (_state.value.isClockedIn) return
        val now = System.currentTimeMillis()
        updateState {
            val audit = AuditEntry(action = "CLOCK_IN", payloadJson = "{\"start\": $now}")
            it.copy(
                isClockedIn = true,
                currentSessionStart = now,
                isOnBreak = false,
                breakStartTime = null,
                accumulatedBreakMs = 0L,
                auditLog = listOf(audit) + it.auditLog.take(49)
            )
        }
    }

    fun clockOut(note: String = "") {
        val current = _state.value
        if (!current.isClockedIn || current.currentSessionStart == null) return

        var breakMs = current.accumulatedBreakMs
        if (current.isOnBreak && current.breakStartTime != null) {
            breakMs += (System.currentTimeMillis() - current.breakStartTime)
        }

        val newSession = Session(
            start = current.currentSessionStart,
            end = System.currentTimeMillis(),
            breakMs = breakMs,
            note = note
        )

        pushUndo(UndoAction(type = "ADD_SESSION", session = newSession))

        updateState {
            val audit = AuditEntry(action = "CLOCK_OUT", payloadJson = gson.toJson(newSession))
            it.copy(
                isClockedIn = false,
                currentSessionStart = null,
                isOnBreak = false,
                breakStartTime = null,
                accumulatedBreakMs = 0L,
                sessions = it.sessions + newSession,
                auditLog = listOf(audit) + it.auditLog.take(49)
            )
        }
    }

    fun toggleBreak() {
        val current = _state.value
        if (!current.isClockedIn) return

        if (!current.isOnBreak) {
            updateState { it.copy(isOnBreak = true, breakStartTime = System.currentTimeMillis()) }
        } else {
            val additionalBreak = if (current.breakStartTime != null) System.currentTimeMillis() - current.breakStartTime else 0L
            updateState {
                it.copy(
                    isOnBreak = false,
                    breakStartTime = null,
                    accumulatedBreakMs = it.accumulatedBreakMs + additionalBreak
                )
            }
        }
    }

    fun updateActiveStartTime(newStartMs: Long) {
        if (!_state.value.isClockedIn) return
        updateState { it.copy(currentSessionStart = newStartMs) }
    }

    fun addManualSession(startMs: Long, endMs: Long, note: String = "", breakMs: Long = 0L, isPutInSystem: Boolean = false) {
        val newSession = Session(start = startMs, end = endMs, breakMs = breakMs, note = note, isPutInSystem = isPutInSystem)
        pushUndo(UndoAction(type = "ADD_SESSION", session = newSession))
        updateState {
            it.copy(
                sessions = it.sessions + newSession,
                auditLog = listOf(AuditEntry(action = "MANUAL_ADD_SESSION", payloadJson = gson.toJson(newSession))) + it.auditLog.take(49)
            )
        }
    }

    fun updateSession(index: Int, startMs: Long, endMs: Long, note: String = "", breakMs: Long = 0L, isPutInSystem: Boolean? = null) {
        val sessions = _state.value.sessions.toMutableList()
        if (index in sessions.indices) {
            val old = sessions[index]
            val updated = old.copy(
                start = startMs,
                end = endMs,
                note = note,
                breakMs = breakMs,
                isPutInSystem = isPutInSystem ?: old.isPutInSystem
            )
            pushUndo(UndoAction(type = "UPDATE_SESSION", index = index, session = updated, previousSession = old))
            sessions[index] = updated
            updateState {
                it.copy(
                    sessions = sessions,
                    auditLog = listOf(AuditEntry(action = "EDIT_SESSION", payloadJson = gson.toJson(updated))) + it.auditLog.take(49)
                )
            }
        }
    }

    fun setSessionPutInSystem(index: Int, isPutIn: Boolean) {
        val sessions = _state.value.sessions.toMutableList()
        if (index in sessions.indices) {
            val old = sessions[index]
            val updated = old.copy(isPutInSystem = isPutIn)
            sessions[index] = updated
            updateState {
                it.copy(
                    sessions = sessions,
                    auditLog = listOf(AuditEntry(action = "TOGGLE_OT_SYSTEM_INPUT", payloadJson = "{\"index\": $index, \"isPutInSystem\": $isPutIn}")) + it.auditLog.take(49)
                )
            }
        }
    }

    fun markAllOtPutInSystem(indices: List<Int>, isPutIn: Boolean) {
        val sessions = _state.value.sessions.toMutableList()
        var changed = false
        for (idx in indices) {
            if (idx in sessions.indices) {
                sessions[idx] = sessions[idx].copy(isPutInSystem = isPutIn)
                changed = true
            }
        }
        if (changed) {
            updateState {
                it.copy(
                    sessions = sessions,
                    auditLog = listOf(AuditEntry(action = "BATCH_OT_SYSTEM_INPUT", payloadJson = "{\"count\": ${indices.size}, \"isPutInSystem\": $isPutIn}")) + it.auditLog.take(49)
                )
            }
        }
    }

    fun deleteSession(index: Int) {
        val sessions = _state.value.sessions.toMutableList()
        if (index in sessions.indices) {
            val deleted = sessions.removeAt(index)
            pushUndo(UndoAction(type = "DELETE_SESSION", index = index, session = deleted))
            updateState {
                it.copy(
                    sessions = sessions,
                    auditLog = listOf(AuditEntry(action = "DELETE_SESSION", payloadJson = gson.toJson(deleted))) + it.auditLog.take(49)
                )
            }
        }
    }

    fun addPtoEntry(dateMs: Long, hours: Double, type: com.randallengineering.jokarztimeclock.data.models.PtoType, note: String = "") {
        val entry = PtoEntry(date = dateMs, hours = hours, type = type, note = note)
        updateState {
            it.copy(
                ptoEntries = it.ptoEntries + entry,
                auditLog = listOf(AuditEntry(action = "ADD_PTO", payloadJson = gson.toJson(entry))) + it.auditLog.take(49)
            )
        }
    }

    fun deletePtoEntry(id: String) {
        updateState {
            it.copy(ptoEntries = it.ptoEntries.filterNot { p -> p.id == id })
        }
    }

    fun clearAllData() {
        updateState {
            TimeclockState(
                grossRate = it.grossRate,
                netRate = it.netRate,
                displayMode = it.displayMode,
                settings = it.settings
            )
        }
    }

    private fun pushUndo(action: UndoAction) {
        undoStack.add(action)
        if (undoStack.size > 20) undoStack.removeAt(0)
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val lastAction = undoStack.removeAt(undoStack.size - 1)

        when (lastAction.type) {
            "ADD_SESSION" -> {
                lastAction.session?.let { s ->
                    updateState { it.copy(sessions = it.sessions.filterNot { it.id == s.id }) }
                }
            }
            "DELETE_SESSION" -> {
                lastAction.session?.let { s ->
                    val sessions = _state.value.sessions.toMutableList()
                    val idx = if (lastAction.index in 0..sessions.size) lastAction.index else sessions.size
                    sessions.add(idx, s)
                    updateState { it.copy(sessions = sessions) }
                }
            }
            "UPDATE_SESSION" -> {
                lastAction.previousSession?.let { old ->
                    val sessions = _state.value.sessions.toMutableList()
                    if (lastAction.index in sessions.indices) {
                        sessions[lastAction.index] = old
                        updateState { it.copy(sessions = sessions) }
                    }
                }
            }
        }
        return true
    }

    fun exportJson(): String {
        return gson.toJson(_state.value)
    }

    fun importJson(json: String): Boolean {
        return try {
            val imported = gson.fromJson(json, TimeclockState::class.java)
            if (imported != null) {
                _state.value = imported
                persist(imported)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
