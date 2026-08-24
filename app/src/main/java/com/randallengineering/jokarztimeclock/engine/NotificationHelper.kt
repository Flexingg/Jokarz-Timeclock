package com.randallengineering.jokarztimeclock.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.randallengineering.jokarztimeclock.MainActivity
import com.randallengineering.jokarztimeclock.R

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "jokarz_milestones_channel"
        const val CHANNEL_NAME = "Shift Milestones & Overtime"
        const val NOTIFICATION_STANDARD_ID = 1001
        const val NOTIFICATION_CLIFF_ID = 1002
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when standard shift is reached or overtime cliff is crossed"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showStandardShiftCompleteNotification() {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Standard Shift Complete! ✅")
            .setContentText("You have reached today's standard 10.0h requirement. Now entering unpaid banking buffer.")
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Overtime Unlocked! 🔥")
            .setContentText("You crossed the 12.5h mark! Overtime pay is now actively accruing back to standard shift.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_CLIFF_ID, notification)
    }

    fun clearNotifications() {
        notificationManager.cancel(NOTIFICATION_STANDARD_ID)
        notificationManager.cancel(NOTIFICATION_CLIFF_ID)
    }
}
