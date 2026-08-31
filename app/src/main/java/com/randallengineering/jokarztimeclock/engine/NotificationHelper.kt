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

        const val CHANNEL_LIVE_ID = "jokarz_live_shift_chip_v4"
        const val CHANNEL_LIVE_NAME = "Live Shift Status Bar Chip"

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
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows live ongoing shift chronometer chip in Status Bar and Dynamic Island"
                setShowBadge(true)
                setSound(null, null)
                enableVibration(false)
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

        try {
            LiveShiftService.start(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearLiveNotification() {
        try {
            LiveShiftService.stop(context)
            notificationManager.cancel(NOTIFICATION_LIVE_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearNotifications() {
        notificationManager.cancel(NOTIFICATION_STANDARD_ID)
        notificationManager.cancel(NOTIFICATION_CLIFF_ID)
        notificationManager.cancel(NOTIFICATION_LIVE_ID)
        notificationManager.cancel(NOTIFICATION_GEOFENCE_ID)
    }
}
