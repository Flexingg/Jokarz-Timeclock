package com.randallengineering.jokarztimeclock.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.randallengineering.jokarztimeclock.BuildConfig
import com.randallengineering.jokarztimeclock.data.backup.BackupCodec
import com.randallengineering.jokarztimeclock.data.backup.BackupImportPlanner
import com.randallengineering.jokarztimeclock.data.backup.ImportMode
import com.randallengineering.jokarztimeclock.data.backup.ImportPlan
import com.randallengineering.jokarztimeclock.data.csv.CsvImportPreview
import com.randallengineering.jokarztimeclock.data.csv.CsvParseResult
import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.PeriodTotals
import com.randallengineering.jokarztimeclock.data.models.PtoType
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository
import com.randallengineering.jokarztimeclock.engine.AudioHapticEngine
import com.randallengineering.jokarztimeclock.engine.GeofenceManager
import com.randallengineering.jokarztimeclock.engine.NotificationHelper
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import com.randallengineering.jokarztimeclock.engine.TaskerBridge
import com.randallengineering.jokarztimeclock.engine.TaskerContract
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

    private val repository = TimeclockRepository.get(application)
    val state: StateFlow<TimeclockState> = repository.state

    val audioHaptic = AudioHapticEngine(application)
    val notificationHelper = NotificationHelper(application)
    val taskerBridge = TaskerBridge(application)
    val geofenceManager = GeofenceManager(application)

    // Tick counter for live timer updates
    private val _tick = MutableStateFlow(System.currentTimeMillis())
    val tick: StateFlow<Long> = _tick.asStateFlow()

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
        // Register initial geofence if configured
        geofenceManager.updateGeofence(state.value.settings)

        // If app opened while already clocked in, ensure live service is running
        if (state.value.isClockedIn && state.value.settings.liveNotificationEnabled) {
            notificationHelper.showOrUpdateLiveShiftNotification(state.value, System.currentTimeMillis())
        }

        // 1-second UI tick loop (only updates UI tick StateFlow and milestone check, NO notification spam)
        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                _tick.value = now
                val s = state.value
                checkMilestones(s, now)
                delay(1000L)
            }
        }
    }

    private fun checkMilestones(s: TimeclockState, now: Long) {
        if (!s.isClockedIn || s.currentSessionStart == null || !s.settings.notificationsEnabled) {
            standardNotified = false
            cliffNotified = false
            return
        }

        val elapsedMs = now - s.currentSessionStart
        val cal = Calendar.getInstance().apply { timeInMillis = s.currentSessionStart }
        val isMonThu = cal.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.THURSDAY

        if (isMonThu) {
            val prevBanked = PayrollEngine.getPreviousBankedHoursForCurrentWeek(s.currentSessionStart, s)
            val mealBreakToAdd = if (s.settings.autoBreakDeduction) s.settings.unpaidMealDuration else 0.0
            val targetStandardHrs = (s.settings.standardShiftHours + mealBreakToAdd) - prevBanked
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
            notificationHelper.clearLiveNotification()
            taskerBridge.sendEvent(TaskerContract.EVENT_CLOCK_OUT, TaskerContract.SOURCE_APP, s.settings)
        } else {
            audioHaptic.playClockInSound(s.settings.soundEnabled)
            repository.clockIn()
            if (s.settings.liveNotificationEnabled) {
                notificationHelper.showOrUpdateLiveShiftNotification(state.value, System.currentTimeMillis())
            }
            taskerBridge.sendEvent(TaskerContract.EVENT_CLOCK_IN, TaskerContract.SOURCE_APP, s.settings)
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
        geofenceManager.updateGeofence(settings)
        if (!settings.liveNotificationEnabled) {
            notificationHelper.clearLiveNotification()
        } else if (state.value.isClockedIn) {
            notificationHelper.showOrUpdateLiveShiftNotification(state.value, System.currentTimeMillis())
        }
    }

    fun updateActiveStartTime(startMs: Long) {
        repository.updateActiveStartTime(startMs)
    }

    fun addManualSession(startMs: Long, endMs: Long, note: String, isPutInSystem: Boolean = false) {
        repository.addManualSession(startMs, endMs, note, isPutInSystem = isPutInSystem)
        pushTaskerData()
    }

    fun updateSession(index: Int, startMs: Long, endMs: Long, note: String, isPutInSystem: Boolean? = null) {
        repository.updateSession(index, startMs, endMs, note, isPutInSystem = isPutInSystem)
        pushTaskerData()
    }

    fun setSessionPutInSystem(index: Int, isPutIn: Boolean) {
        audioHaptic.playClickSound(state.value.settings.soundEnabled)
        repository.setSessionPutInSystem(index, isPutIn)
        pushTaskerData()
    }

    fun markAllOtPutInSystem(indices: List<Int>, isPutIn: Boolean) {
        audioHaptic.playClickSound(state.value.settings.soundEnabled)
        repository.markAllOtPutInSystem(indices, isPutIn)
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
        notificationHelper.clearLiveNotification()
        pushTaskerData()
    }

    fun exportBackup(): String =
        BackupCodec.encode(state.value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, System.currentTimeMillis())

    fun planImport(incoming: TimeclockState, mode: ImportMode): ImportPlan =
        BackupImportPlanner.plan(state.value, incoming, mode, System.currentTimeMillis())

    /**
     * Re-plans against the state as it is *now* (a geofence clock-in may have happened while the
     * confirmation dialog was open) and applies it. Returns the applied plan, or null if nothing changed.
     */
    fun importBackup(incoming: TimeclockState, mode: ImportMode): ImportPlan? {
        val plan = planImport(incoming, mode)
        if (!repository.importBackup(plan.resultingState)) return null
        afterImport()
        return plan
    }

    /**
     * What importing [parsed] under [mode] would do to the state as it is now. Goes through
     * [CsvImportPreview.build], which builds the CSV's incoming state (a REPLACE keeps rates,
     * settings, PTO and the running shift) and plans it with the same [BackupImportPlanner].
     */
    fun planCsvImport(parsed: CsvParseResult.Success, mode: ImportMode): CsvImportPreview =
        CsvImportPreview.build(parsed, state.value, mode, System.currentTimeMillis())

    /**
     * Same shape as [importBackup]: re-plans against the state as it is *now*, applies it atomically
     * through the repository, and returns null (nothing changed) if the write failed.
     */
    fun importCsv(parsed: CsvParseResult.Success, mode: ImportMode): ImportPlan? {
        val plan = planCsvImport(parsed, mode).plan
        if (!repository.importBackup(plan.resultingState)) return null
        afterImport()
        return plan
    }

    /** The geofence, live notification and Tasker all read the state an import just replaced. */
    private fun afterImport() {
        val s = state.value
        geofenceManager.updateGeofence(s.settings)
        if (s.isClockedIn && s.settings.liveNotificationEnabled) {
            notificationHelper.showOrUpdateLiveShiftNotification(s, System.currentTimeMillis())
        } else {
            notificationHelper.clearLiveNotification()
        }
        pushTaskerData()
    }

    private fun pushTaskerData() {
        val currentTotals = totals.value
        taskerBridge.pushData(
            todayStats = currentTotals.todayStats,
            totalActualHrsPeriod = currentTotals.totalPayableHoursPeriod,
            state = state.value
        )
    }
}
