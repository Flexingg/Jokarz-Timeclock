package com.randallengineering.jokarztimeclock.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.PeriodTotals
import com.randallengineering.jokarztimeclock.data.models.PtoType
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository
import com.randallengineering.jokarztimeclock.engine.AudioHapticEngine
import com.randallengineering.jokarztimeclock.engine.NotificationHelper
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import com.randallengineering.jokarztimeclock.engine.TaskerBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

class TimeclockViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TimeclockRepository(application)
    val state: StateFlow<TimeclockState> = repository.state

    val audioHaptic = AudioHapticEngine(application)
    private val notificationHelper = NotificationHelper(application)
    private val taskerBridge = TaskerBridge(application)

    // Tick counter for live timer updates
    private val _tick = MutableStateFlow(0L)

    val totals: StateFlow<PeriodTotals> = combine(state, _tick) { s, _ ->
        PayrollEngine.calculatePeriodTotals(s)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PayrollEngine.calculatePeriodTotals(state.value)
    )

    private var standardNotified = false
    private var cliffNotified = false

    init {
        // 1-second live tick loop
        viewModelScope.launch {
            while (isActive) {
                _tick.value = System.currentTimeMillis()
                checkMilestones()
                delay(1000L)
            }
        }
    }

    private fun checkMilestones() {
        val s = state.value
        if (!s.isClockedIn || s.currentSessionStart == null || !s.settings.notificationsEnabled) {
            standardNotified = false
            cliffNotified = false
            return
        }

        val elapsedMs = System.currentTimeMillis() - s.currentSessionStart
        val cal = Calendar.getInstance().apply { timeInMillis = s.currentSessionStart }
        val isMonThu = cal.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.THURSDAY

        if (isMonThu) {
            val prevBanked = PayrollEngine.getPreviousBankedHoursForCurrentWeek(s.currentSessionStart, s)
            val targetStandardHrs = (s.settings.standardShiftHours + s.settings.unpaidMealDuration) - prevBanked
            val standardMs = (targetStandardHrs * 3600000.0).toLong()
            val cliffMs = (s.settings.cliffHours * 3600000.0).toLong()

            if (elapsedMs >= standardMs && !standardNotified) {
                standardNotified = true
                notificationHelper.showStandardShiftCompleteNotification()
                audioHaptic.playMilestoneChime(s.settings.soundEnabled)
            }

            if (elapsedMs >= cliffMs && !cliffNotified) {
                cliffNotified = true
                notificationHelper.showOvertimeCliffNotification()
                audioHaptic.playMilestoneChime(s.settings.soundEnabled)
            }
        }
    }

    fun toggleClock() {
        val s = state.value
        if (s.isClockedIn) {
            audioHaptic.playClockOutSound(s.settings.soundEnabled)
            repository.clockOut()
            taskerBridge.sendEvent("Clocked Out")
        } else {
            audioHaptic.playClockInSound(s.settings.soundEnabled)
            repository.clockIn()
            taskerBridge.sendEvent("Clocked In")
        }
        pushTaskerData()
    }

    fun toggleBreak() {
        audioHaptic.playClickSound(state.value.settings.soundEnabled)
        repository.toggleBreak()
    }

    fun setMode(mode: PayMode) {
        audioHaptic.playClickSound(state.value.settings.soundEnabled)
        repository.setMode(mode)
    }

    fun setRate(mode: PayMode, rate: Double) {
        repository.setRate(mode, rate)
        pushTaskerData()
    }

    fun updateSettings(settings: AppSettings) {
        repository.updateSettings(settings)
    }

    fun updateActiveStartTime(startMs: Long) {
        repository.updateActiveStartTime(startMs)
    }

    fun addManualSession(startMs: Long, endMs: Long, note: String) {
        repository.addManualSession(startMs, endMs, note)
        pushTaskerData()
    }

    fun updateSession(index: Int, startMs: Long, endMs: Long, note: String) {
        repository.updateSession(index, startMs, endMs, note)
        pushTaskerData()
    }

    fun deleteSession(index: Int) {
        repository.deleteSession(index)
        pushTaskerData()
    }

    fun addPto(dateMs: Long, hours: Double, type: PtoType, note: String) {
        repository.addPtoEntry(dateMs, hours, type, note)
    }

    fun deletePto(id: String) {
        repository.deletePtoEntry(id)
    }

    fun undo(): Boolean {
        val success = repository.undo()
        if (success) pushTaskerData()
        return success
    }

    fun clearAllData() {
        repository.clearAllData()
        pushTaskerData()
    }

    private fun pushTaskerData() {
        val currentTotals = totals.value
        taskerBridge.pushData(
            todayStats = currentTotals.todayStats,
            totalTechHrsPeriod = currentTotals.totalClockedHoursPeriod,
            totalActualHrsPeriod = currentTotals.totalPayableHoursPeriod,
            state = state.value
        )
    }
}
