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
 * In-app Tasker setup.
 *
 * Two things live here:
 *
 *  1. [exportProfileFile] — writes a **valid** Tasker profile file (`.prf.xml`) to Downloads and
 *     walks the owner through importing it. This replaces the pre-2.8.1 "one-click import", which
 *     fired an undocumented `tasker://import?file=<asset name>` URI that nothing handles and shipped
 *     a hand-written XML asset Tasker rejected with "Error details: Missing event type".
 *  2. [launchTaskerSetup] — the typed recipe for the other directions (Tasker ▸ app, the app's
 *     hour totals, the opt-in "run a Tasker task"), built from [TaskerContract.setupSteps] so it
 *     cannot drift from the constants the code uses.
 */
object TaskerHelper {

    private fun recipe(): String = TaskerContract.setupSteps().joinToString("\n")

    /**
     * Writes [TaskerProfileExport.profileXml] to Downloads, then shows the import steps. The XML is
     * also copied to the clipboard, which is a usable fallback: recent Tasker versions import
     * profile XML straight from the clipboard.
     */
    fun exportProfileFile(context: Context) {
        val xml = TaskerProfileExport.profileXml()
        copyToClipboard(context, "Jokarz Timeclock Tasker profile", xml)
        val steps = TaskerProfileExport.importSteps().joinToString("\n")

        when (val result = TaskerProfileWriter.write(context, xml)) {
            is TaskerExportResult.Written -> AlertDialog.Builder(context)
                .setTitle("Tasker profile exported")
                .setMessage(
                    "Written to ${result.location} (${result.bytes} bytes). " +
                        "The XML is on the clipboard too.\n\nimport into Tasker:\n\n$steps"
                )
                .setPositiveButton("Open Tasker") { _, _ -> openTasker(context) }
                .setNeutralButton("Copy XML") { _, _ ->
                    copyToClipboard(context, "Jokarz Timeclock Tasker profile", xml)
                    Toast.makeText(context, "Tasker XML copied", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Close", null)
                .show()

            is TaskerExportResult.Failed -> AlertDialog.Builder(context)
                .setTitle("Could not write the profile file")
                .setMessage(
                    "Android refused to write ${TaskerProfileExport.FILE_NAME}: ${result.reason}\n\n" +
                        "The XML was copied to the clipboard instead — in Tasker, open the PROFILES " +
                        "tab and import from the clipboard.\n\n$steps"
                )
                .setPositiveButton("Open Tasker") { _, _ -> openTasker(context) }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    /** Copies the typed recipe to the clipboard, then shows it with Open Tasker / Share buttons. */
    fun launchTaskerSetup(context: Context) {
        val text = recipe()
        copyToClipboard(context, "Jokarz Timeclock Tasker setup", text)

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

    private fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
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
