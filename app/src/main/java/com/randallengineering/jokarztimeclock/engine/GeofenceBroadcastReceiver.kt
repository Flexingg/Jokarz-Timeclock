package com.randallengineering.jokarztimeclock.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository

private const val TAG = "GeofenceReceiver"

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CLOCK_IN  = "com.randallengineering.jokarztimeclock.ACTION_CLOCK_IN"
        const val ACTION_CLOCK_OUT = "com.randallengineering.jokarztimeclock.ACTION_CLOCK_OUT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val repository = TimeclockRepository(context)
        val notificationHelper = NotificationHelper(context)
        val taskerBridge = TaskerBridge(context)

        when (intent.action) {
            // ── Tasker-triggered clock-in/out ─────────────────────────────────
            ACTION_CLOCK_IN -> {
                val state = repository.state.value
                if (!state.isClockedIn) {
                    repository.clockIn()
                    notificationHelper.showGeofenceNotification(
                        title = "Auto Clocked In 📍",
                        message = "Tasker location trigger detected — shift started."
                    )
                    taskerBridge.sendEvent("Tasker Auto Clock In")
                    Log.i(TAG, "Tasker ACTION_CLOCK_IN received — clocked in.")
                }
            }
            ACTION_CLOCK_OUT -> {
                val state = repository.state.value
                if (state.isClockedIn) {
                    repository.clockOut(note = "Auto Clock Out via Tasker")
                    notificationHelper.showGeofenceNotification(
                        title = "Auto Clocked Out 📍",
                        message = "Tasker location trigger detected — shift ended."
                    )
                    taskerBridge.sendEvent("Tasker Auto Clock Out")
                    Log.i(TAG, "Tasker ACTION_CLOCK_OUT received — clocked out.")
                }
            }
            // ── Native Google Play Services geofence ──────────────────────────
            else -> {
                val geofencingEvent = GeofencingEvent.fromIntent(intent)
                if (geofencingEvent == null || geofencingEvent.hasError()) {
                    Log.e(TAG, "Geofence event error: ${geofencingEvent?.errorCode}")
                    return
                }

                val state = repository.state.value

                when (geofencingEvent.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        if (!state.isClockedIn) {
                            repository.clockIn()
                            notificationHelper.showGeofenceNotification(
                                title = "Auto Clocked In 📍",
                                message = "Welcome to work! Shift started automatically via Geofence."
                            )
                            taskerBridge.sendEvent("Geofence Auto Clock In")
                        }
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        if (state.isClockedIn) {
                            repository.clockOut(note = "Auto Clock Out via Geofence")
                            notificationHelper.showGeofenceNotification(
                                title = "Auto Clocked Out 📍",
                                message = "You have left the work area. Shift completed automatically."
                            )
                            taskerBridge.sendEvent("Geofence Auto Clock Out")
                        }
                    }
                }
            }
        }
    }
}
