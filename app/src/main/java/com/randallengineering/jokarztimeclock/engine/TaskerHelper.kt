package com.randallengineering.jokarztimeclock.engine

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * In-app Tasker setup: shows and copies the exact recipe from [TaskerContract.setupSteps].
 *
 * Replaces the pre-2.8.0 "one-click import", which fired an undocumented `tasker://import?file=`
 * URI that nothing handles and bundled a hand-written Tasker XML asset that could not import cleanly
 * (its task also ran `am broadcast` from Run Shell, which a normal app uid may not do). Both were
 * removed; typed-in steps built from the same constants the code uses cannot drift.
 */
object TaskerHelper {

    private fun recipe(): String = TaskerContract.setupSteps().joinToString("\n")

    /** Copies the recipe to the clipboard, then shows it with Open Tasker / Share buttons. */
    fun launchTaskerSetup(context: Context) {
        val text = recipe()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Jokarz Timeclock Tasker setup", text))

        AlertDialog.Builder(context)
            .setTitle("Tasker setup (copied to clipboard)")
            .setMessage(text)
            .setPositiveButton("Open Tasker") { _, _ -> openTasker(context) }
            .setNeutralButton("Share") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Jokarz Timeclock Tasker setup")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, "Share Tasker setup"))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openTasker(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(TaskerContract.TASKER_PACKAGE)
        if (launch != null) {
            context.startActivity(launch)
            return
        }
        Toast.makeText(context, "Tasker is not installed — opening its Play Store page.", Toast.LENGTH_LONG).show()
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${TaskerContract.TASKER_PACKAGE}"))
        try {
            context.startActivity(market)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${TaskerContract.TASKER_PACKAGE}"))
            )
        }
    }
}
