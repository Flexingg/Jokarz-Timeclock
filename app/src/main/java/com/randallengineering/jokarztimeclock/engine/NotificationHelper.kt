package com.randallengineering.jokarztimeclock.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.randallengineering.jokarztimeclock.MainActivity
import com.randallengineering.jokarztimeclock.R
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_MILESTONES_ID = "jokarz_milestones_channel"
        const val CHANNEL_MILESTONES_NAME = "Shift Milestones & Alerts"

        const val CHANNEL_LIVE_ID = "jokarz_live_shift_channel"
        const val CHANNEL_LIVE_NAME = "Live Shift Tracker"

        const val NOTIFICATION_STANDARD_ID = 1001
        const val NOTIFICATION_CLIFF_ID = 1002
        const val NOTIFICATION_LIVE_ID = 1003
        const val NOTIFICATION_GEOFENCE_ID = 1004
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val milestoneChannel = NotificationChannel(
                CHANNEL_MILESTONES_ID,
                CHANNEL_MILESTONES_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when standard shift is reached or overtime cliff is crossed"
                enableVibration(true)
            }

            val liveChannel = NotificationChannel(
                CHANNEL_LIVE_ID,
                CHANNEL_LIVE_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live ongoing shift tracker and remaining time"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(milestoneChannel)
            notificationManager.createNotificationChannel(liveChannel)
        }
    }

    fun showStandardShiftCompleteNotification() {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MILESTONES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Standard Shift Complete! ✅")
            .setContentText("You reached today's 10.0h requirement. Now entering unpaid banking buffer.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_STANDARD_ID, notification)
    }

    fun showOvertimeCliffNotification() {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MILESTONES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Overtime Unlocked! 🔥")
            .setContentText("You crossed the 12.5h mark! Overtime pay is now actively accruing back to 10.5h.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_CLIFF_ID, notification)
    }

    fun showGeofenceNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MILESTONES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_GEOFENCE_ID, notification)
    }

    fun showOrUpdateLiveShiftNotification(state: TimeclockState, currentTickMs: Long) {
        if (!state.settings.liveNotificationEnabled || !state.isClockedIn || state.currentSessionStart == null) {
            clearLiveNotification()
            return
        }

        val startMs = state.currentSessionStart
        val elapsedMs = max(0L, currentTickMs - startMs)
        val settings = state.settings
        val standardTargetMs = ((settings.standardShiftHours + settings.unpaidMealDuration) * 3600000.0).toLong()
        val cliffTargetMs = (settings.cliffHours * 3600000.0).toLong()

        val cal = Calendar.getInstance().apply { timeInMillis = startMs }
        val isMonThu = cal.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.THURSDAY

        val statusText = if (state.isOnBreak) {
            val breakElapsed = state.accumulatedBreakMs + (currentTickMs - (state.breakStartTime ?: currentTickMs))
            "On Lunch / Break: ${PayrollEngine.formatDuration(breakElapsed)}"
        } else if (isMonThu) {
            val prevBanked = PayrollEngine.getPreviousBankedHoursForCurrentWeek(startMs, state)
            val targetStandardHrs = (settings.standardShiftHours + settings.unpaidMealDuration) - prevBanked
            val standardMs = (targetStandardHrs * 3600000.0).toLong()

            if (elapsedMs < standardMs) {
                val remainingMs = standardMs - elapsedMs
                val bankNote = if (abs(prevBanked) > 0.05) {
                    val sign = if (prevBanked > 0) "+" else ""
                    " ($sign${String.format("%.1f", prevBanked)}h bank)"
                } else ""
                "Remaining: ${PayrollEngine.formatDuration(remainingMs)}$bankNote"
            } else if (elapsedMs < cliffTargetMs) {
                val bankingHrs = (elapsedMs - standardMs) / 3600000.0
                "Banking Buffer: +${String.format("%.2f", bankingHrs)}h (Unpaid)"
            } else {
                val otHours = (elapsedMs - standardTargetMs) / 3600000.0
                val rate = if (state.displayMode == com.randallengineering.jokarztimeclock.data.models.PayMode.GROSS) state.grossRate else state.netRate
                val otPay = otHours * rate * settings.otMultiplier
                "Overtime Active: ${String.format("%.2f", otHours)}h • ${PayrollEngine.formatMoney(otPay)}"
            }
        } else {
            val elapsedHrs = elapsedMs / 3600000.0
            val payableHours = if (elapsedHrs > 4.0) elapsedHrs - 0.5 else elapsedHrs
            val rate = if (state.displayMode == com.randallengineering.jokarztimeclock.data.models.PayMode.GROSS) state.grossRate else state.netRate
            val pay = maxOf(0.0, payableHours * rate * settings.otMultiplier)
            "Weekend Overtime: ${String.format("%.2f", payableHours)}h • ${PayrollEngine.formatMoney(pay)}"
        }

        // Tap opens app
        val contentIntent = Intent(context, MainActivity::class.java)
        val pendingContentIntent = PendingIntent.getActivity(
            context, 10, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Break toggle
        val breakIntent = Intent(Intent.ACTION_VIEW, Uri.parse("jokarz://timeclock?action=break"), context, MainActivity::class.java)
        val pendingBreakIntent = PendingIntent.getActivity(
            context, 11, breakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Clock Out
        val clockOutIntent = Intent(Intent.ACTION_VIEW, Uri.parse("jokarz://timeclock?action=clock_out"), context, MainActivity::class.java)
        val pendingClockOutIntent = PendingIntent.getActivity(
            context, 12, clockOutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (state.isOnBreak) "⏸️ Jokarz Timeclock (On Break)" else "⏱️ Shift Active: ${PayrollEngine.formatDuration(elapsedMs)}"

        val notification = NotificationCompat.Builder(context, CHANNEL_LIVE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingContentIntent)
            .addAction(
                0,
                if (state.isOnBreak) "▶️ Resume" else "⏸️ Lunch / Pause",
                pendingBreakIntent
            )
            .addAction(
                0,
                "⏹️ Clock Out",
                pendingClockOutIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_LIVE_ID, notification)
    }

    fun clearLiveNotification() {
        notificationManager.cancel(NOTIFICATION_LIVE_ID)
    }

    fun clearNotifications() {
        notificationManager.cancel(NOTIFICATION_STANDARD_ID)
        notificationManager.cancel(NOTIFICATION_CLIFF_ID)
        notificationManager.cancel(NOTIFICATION_LIVE_ID)
        notificationManager.cancel(NOTIFICATION_GEOFENCE_ID)
    }
}
