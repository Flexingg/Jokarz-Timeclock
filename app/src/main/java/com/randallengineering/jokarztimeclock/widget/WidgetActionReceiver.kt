package com.randallengineering.jokarztimeclock.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository
import com.randallengineering.jokarztimeclock.engine.LiveShiftService
import com.randallengineering.jokarztimeclock.engine.TaskerBridge
import com.randallengineering.jokarztimeclock.engine.TaskerContract

/** Handles the widget's Clock In / Clock Out tap with the same side effects as the in-app button. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetUpdater.ACTION_TOGGLE_CLOCK) return
        val repository = TimeclockRepository.get(context)
        val settings = repository.state.value.settings
        val taskerBridge = TaskerBridge(context)

        if (repository.state.value.isClockedIn) {
            repository.clockOut()
            LiveShiftService.stop(context)
            taskerBridge.sendEvent(TaskerContract.EVENT_CLOCK_OUT, TaskerContract.SOURCE_APP, settings)
        } else {
            repository.clockIn()
            if (settings.liveNotificationEnabled) LiveShiftService.start(context)
            taskerBridge.sendEvent(TaskerContract.EVENT_CLOCK_IN, TaskerContract.SOURCE_APP, settings)
        }
        WidgetUpdater.updateAll(context)
    }
}
