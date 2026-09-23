package com.randallengineering.jokarztimeclock.engine

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * What the OS actually did with the live shift notification, in words the owner can act on.
 *
 * [statusBarChipPossible] means "this OS + this app permission could draw a promoted chip";
 * [promoted] means "the system really promoted the notification we posted".
 */
data class ChipVerdict(
    val statusBarChipPossible: Boolean,
    val promoted: Boolean,
    val headline: String,
    val detail: String
)

/**
 * Pure, Android-free rules for turning platform facts into a [ChipVerdict]. Kept separate from
 * [LiveChipStatusReader] so every branch is covered by plain JVM tests.
 */
object LiveChipStatus {

    /** Android 16 (BAKLAVA): first API level with promoted-ongoing / Live Update notifications. */
    const val PROMOTION_API = 36

    fun evaluate(
        apiLevel: Int,
        canPostPromoted: Boolean,
        isOngoing: Boolean,
        promotable: Boolean,
        promotedFlag: Boolean
    ): ChipVerdict = when {
        apiLevel < PROMOTION_API -> ChipVerdict(
            statusBarChipPossible = false,
            promoted = false,
            headline = "Status bar chip not supported on this Android version",
            detail = "Live Updates need Android 16 or newer. The notification still shows the " +
                "system-drawn elapsed timer in the shade."
        )
        !canPostPromoted -> ChipVerdict(
            statusBarChipPossible = false,
            promoted = false,
            headline = "Live Updates are switched off for this app",
            detail = "Turn on \"Live Updates\" (promoted notifications) for Jokarz Timeclock in the " +
                "phone's notification settings — use the button below."
        )
        !isOngoing -> ChipVerdict(
            statusBarChipPossible = true,
            promoted = false,
            headline = "No live shift notification is posted right now",
            detail = "Live Updates are allowed. The chip appears once a shift is running and the " +
                "live notification is enabled."
        )
        promotedFlag -> ChipVerdict(
            statusBarChipPossible = true,
            promoted = true,
            headline = "Status bar chip is live",
            detail = "Android promoted the shift notification to a Live Update; the chip shows " +
                "the system elapsed timer."
        )
        !promotable -> ChipVerdict(
            statusBarChipPossible = true,
            promoted = false,
            headline = "Notification does not qualify for promotion",
            detail = "Android reports the posted notification is missing a Live Update " +
                "requirement. This is an app bug — please report it with this build number."
        )
        else -> ChipVerdict(
            statusBarChipPossible = true,
            promoted = false,
            headline = "The phone is not promoting this app",
            detail = "Everything the app controls is in place and Live Updates are allowed, but " +
                "the system did not promote it. Some phone makers only promote their own apps, " +
                "and Android 16.0 before the QPR1 update never draws the chip. The notification " +
                "stays a normal ongoing one with a working timer."
        )
    }
}

/**
 * Android-side reader: collects the facts [LiveChipStatus.evaluate] needs by reading back the
 * notification that is actually posted, rather than assuming what the builder produced.
 */
object LiveChipStatusReader {

    data class Snapshot(
        val apiLevel: Int,
        val canPostPromoted: Boolean,
        val isOngoing: Boolean,
        val promotable: Boolean,
        val promotedFlag: Boolean,
        val verdict: ChipVerdict
    )

    fun read(context: Context): Snapshot {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val api = Build.VERSION.SDK_INT
        val posted: Notification? = try {
            nm.activeNotifications.firstOrNull { it.id == LiveShiftService.NOTIFICATION_LIVE_ID }?.notification
        } catch (e: Exception) {
            null
        }
        val isOngoing = posted != null && (posted.flags and Notification.FLAG_ONGOING_EVENT) != 0

        var canPostPromoted = false
        var promotable = false
        var promotedFlag = false
        if (api >= LiveChipStatus.PROMOTION_API) {
            canPostPromoted = try { nm.canPostPromotedNotifications() } catch (e: Exception) { false }
            if (posted != null) {
                promotable = posted.hasPromotableCharacteristics()
                promotedFlag = (posted.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
            }
        }
        return Snapshot(
            apiLevel = api,
            canPostPromoted = canPostPromoted,
            isOngoing = isOngoing,
            promotable = promotable,
            promotedFlag = promotedFlag,
            verdict = LiveChipStatus.evaluate(api, canPostPromoted, isOngoing, promotable, promotedFlag)
        )
    }

    /**
     * Opens the per-app "Live Updates" toggle on Android 16+, else this app's notification settings.
     * (`Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` is not in the API 36 or 37 SDK on the build
     * machine, so it is not guessed at here.)
     */
    fun openPromotionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= LiveChipStatus.PROMOTION_API) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            try {
                if (activity.packageManager.resolveActivity(intent, 0) != null) {
                    activity.startActivity(intent)
                    return
                }
            } catch (e: Exception) {
                // Fall through to the generic notification settings.
            }
        }
        PermissionHelper.openNotificationSettings(activity)
    }
}
