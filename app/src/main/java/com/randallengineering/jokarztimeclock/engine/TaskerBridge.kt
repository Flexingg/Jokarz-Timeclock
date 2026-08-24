package com.randallengineering.jokarztimeclock.engine

import android.content.Context
import android.content.Intent
import com.randallengineering.jokarztimeclock.data.models.DayStats
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import java.util.Locale

class TaskerBridge(private val context: Context) {

    fun sendEvent(eventText: String) {
        try {
            val intent = Intent("net.dinglisch.android.tasker.ACTION_EVENT").apply {
                putExtra("event_name", "Work Tracker Event")
                putExtra("event_message", eventText)
                putExtra("event_timestamp", System.currentTimeMillis())
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pushData(todayStats: DayStats?, totalTechHrsPeriod: Double, totalActualHrsPeriod: Double, state: TimeclockState) {
        if (todayStats == null) return
        try {
            val intent = Intent("net.dinglisch.android.tasker.ACTION_VARIABLE_SET").apply {
                putExtra("%WorkTechHrsToday", String.format(Locale.US, "%.2f", todayStats.clockedHours))
                putExtra("%WorkActualHrsToday", String.format(Locale.US, "%.2f", todayStats.payableHours))
                putExtra("%WorkActualGrossToday", String.format(Locale.US, "%.2f", todayStats.payableHours * state.grossRate))
                putExtra("%WorkActualNetToday", String.format(Locale.US, "%.2f", todayStats.payableHours * state.netRate))

                putExtra("%WorkActualHrsPeriod", String.format(Locale.US, "%.2f", totalActualHrsPeriod))
                putExtra("%WorkActualGrossPeriod", String.format(Locale.US, "%.2f", totalActualHrsPeriod * state.grossRate))
                putExtra("%WorkActualNetPeriod", String.format(Locale.US, "%.2f", totalActualHrsPeriod * state.netRate))
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
