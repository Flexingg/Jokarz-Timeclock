package com.randallengineering.jokarztimeclock.engine

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

object TaskerHelper {
    private const val TASKER_PACKAGE = "net.dinglisch.android.taskerm"
    private const val IMPORT_URI = "tasker://import?file=jokarz_timeclock_tasker_profile.txt"

    /**
     * Attempts to launch Tasker to import the geofence profile.
     * If Tasker is not installed, copies the profile text to the clipboard and shows a dialog.
     */
    fun launchTaskerImport(context: Context) {
        val pm = context.packageManager
        val isTaskerInstalled = try {
            pm.getPackageInfo(TASKER_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }

        if (isTaskerInstalled) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(IMPORT_URI)
                `package` = TASKER_PACKAGE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            // Fallback: copy the profile text to clipboard and inform the user.
            val profileText = readAssetProfile(context)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Tasker Profile", profileText)
            clipboard.setPrimaryClip(clip)

            AlertDialog.Builder(context)
                .setTitle("Tasker not installed")
                .setMessage("Tasker is not installed on this device. The geofence profile has been copied to the clipboard. Paste it into Tasker manually or install Tasker to use the one‑click import.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun readAssetProfile(context: Context): String {
        return try {
            context.assets.open("jokarz_timeclock_tasker_profile.txt").use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }
        } catch (e: Exception) {
            "" // Return empty string if something goes wrong.
        }
    }
}
