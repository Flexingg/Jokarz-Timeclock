package com.randallengineering.jokarztimeclock.engine

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.randallengineering.jokarztimeclock.data.models.AppSettings

private const val TAG = "GeofenceManager"
private const val MAX_RETRIES = 3

class GeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private var retryCount = 0

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

    /**
     * Checks whether Google Play Services are available and up-to-date.
     */
    fun isGeofenceAvailable(): Boolean {
        val result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        return result == ConnectionResult.SUCCESS
    }

    @SuppressLint("MissingPermission")
    fun updateGeofence(settings: AppSettings) {
        // If geofence is disabled or no coordinates set, remove existing fence.
        if (!settings.geofenceEnabled || (settings.workLatitude == 0.0 && settings.workLongitude == 0.0)) {
            removeGeofence()
            return
        }

        // If Tasker fallback is enabled, skip native geofencing entirely.
        if (settings.useTaskerFallback) {
            Log.i(TAG, "Tasker fallback enabled – skipping native geofence registration.")
            removeGeofence()
            return
        }

        // Require location permissions.
        if (!PermissionHelper.hasLocationPermissions(context)) {
            Log.w(TAG, "Location permissions not granted – geofence not registered.")
            return
        }

        // Require Google Play Services.
        if (!isGeofenceAvailable()) {
            Log.w(TAG, "Google Play Services unavailable – geofence not registered.")
            return
        }

        registerGeofence(settings)
    }

    @SuppressLint("MissingPermission")
    private fun registerGeofence(settings: AppSettings) {
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

            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    retryCount = 0
                    Log.i(TAG, "Geofence registered at (${settings.workLatitude}, ${settings.workLongitude}) r=${settings.geofenceRadiusMeters}m")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to register geofence: ${e.message}")
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        val delayMs = (1000L * (1 shl retryCount)) // exponential backoff: 2s, 4s, 8s
                        Log.i(TAG, "Retrying geofence registration in ${delayMs}ms (attempt $retryCount)")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            registerGeofence(settings)
                        }, delayMs)
                    } else {
                        Log.e(TAG, "Max retries reached. Geofence not registered.")
                        retryCount = 0
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception registering geofence", e)
        }
    }

    fun removeGeofence() {
        try {
            geofencingClient.removeGeofences(geofencePendingIntent)
                .addOnSuccessListener { Log.i(TAG, "Geofence removed.") }
                .addOnFailureListener { e -> Log.w(TAG, "Geofence removal failed: ${e.message}") }
        } catch (e: Exception) {
            Log.e(TAG, "Exception removing geofence", e)
        }
    }
}
