package com.randallengineering.jokarztimeclock.engine

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper object to request location permissions required for geofencing.
 * It first requests ACCESS_FINE_LOCATION (foreground) and then ACCESS_BACKGROUND_LOCATION.
 * Call [requestBackgroundLocation] from a UI component (e.g., SettingsFragment).
 */
object PermissionHelper {
    private const val REQUEST_FINE_LOCATION = 1001
    private const val REQUEST_BACKGROUND_LOCATION = 1002

    /**
     * Checks if the required permissions are already granted.
     */
    fun hasLocationPermissions(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val background = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Background permission not needed before Android 10
        }
        return fine && background
    }

    /**
     * Initiates the permission request flow. The caller must provide two [ActivityResultLauncher]s:
     * - one for fine location
     * - one for background location (only needed on Android 10+)
     * The helper will handle showing rationale dialogs when appropriate.
     */
    fun requestBackgroundLocation(
        activity: Activity,
        fineLocationLauncher: ActivityResultLauncher<String>,
        backgroundLocationLauncher: ActivityResultLauncher<String>
    ) {
        // Step 1: request fine location if not granted
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Show rationale if needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                showRationaleDialog(
                    activity,
                    "Location permission is needed for the app to detect when you are at work and automatically clock‑in/out.",
                    onProceed = { fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                )
            } else {
                fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            return
        }

        // Step 2: request background location on Android 10+ if not granted
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            ) {
                showRationaleDialog(
                    activity,
                    "Background location permission is required for geofencing to work even when the app is not in the foreground.",
                    onProceed = { backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
                )
            } else {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            return
        }

        // All permissions already granted – you can now enable the geofence.
        // The caller should react to this state (e.g., call GeofenceManager.updateGeofence).
    }

    private fun showRationaleDialog(context: Context, message: String, onProceed: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Permission required")
            .setMessage(message)
            .setPositiveButton("Proceed") { _, _ -> onProceed() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Opens the app's permission settings screen, useful when the user has permanently denied a permission.
     */
    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
}
