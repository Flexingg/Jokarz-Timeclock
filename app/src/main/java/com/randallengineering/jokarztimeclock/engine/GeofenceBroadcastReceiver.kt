package com.randallengineering.jokarztimeclock.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            println("Geofence error: ${geofencingEvent.errorCode}")
            return
        }

        val repository = TimeclockRepository(context)
        val notificationHelper = NotificationHelper(context)
        val taskerBridge = TaskerBridge(context)

        val geofenceTransition = geofencingEvent.geofenceTransition
        val state = repository.state.value

        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                if (!state.isClockedIn) {
                    repository.clockIn()
                    notificationHelper.showGeofenceNotification(
                        title = "Auto Clocked In 📍",
                        message = "Welcome to work! Your shift has started automatically via Google Maps Geofence."
                    )
                    taskerBridge.sendEvent("Geofence Auto Clock In")
                }
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                if (state.isClockedIn) {
                    repository.clockOut(note = "Auto Clock Out via Geofence")
                    notificationHelper.showGeofenceNotification(
                        title = "Auto Clocked Out 📍",
                        message = "You have left the work geofence. Your shift has been completed."
                    )
                    taskerBridge.sendEvent("Geofence Auto Clock Out")
                }
            }
        }
    }
}
