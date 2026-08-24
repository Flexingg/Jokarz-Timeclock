package com.randallengineering.jokarztimeclock.engine

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.randallengineering.jokarztimeclock.data.models.AppSettings

class GeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    companion object {
        const val GEOFENCE_ID = "WORK_LOCATION_GEOFENCE"
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun updateGeofence(settings: AppSettings) {
        if (!settings.geofenceEnabled || (settings.workLatitude == 0.0 && settings.workLongitude == 0.0)) {
            removeGeofence()
            return
        }

        try {
            val geofence = Geofence.Builder()
                .setRequestId(GEOFENCE_ID)
                .setCircularRegion(
                    settings.workLatitude,
                    settings.workLongitude,
                    settings.geofenceRadiusMeters
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()

            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()

            geofencingClient.addGeofences(request, geofencePendingIntent).addOnSuccessListener {
                println("Jokarz Timeclock: Geofence successfully registered at (${settings.workLatitude}, ${settings.workLongitude})")
            }.addOnFailureListener { e ->
                println("Jokarz Timeclock: Failed to register geofence: ${e.message}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeGeofence() {
        try {
            geofencingClient.removeGeofences(geofencePendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
