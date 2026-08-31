package com.randallengineering.jokarztimeclock.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.randallengineering.jokarztimeclock.MainActivity
import com.randallengineering.jokarztimeclock.R
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

class LiveShiftService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateCollectJob: Job? = null
    private var refreshJob: Job? = null
    private lateinit var repository: TimeclockRepository
    private lateinit var notificationManager: NotificationManager

    companion object {
        // Updated channel ID to ensure Android/ColorOS creates it with HIGH importance for Status Bar Chips and Live Alerts
        const val CHANNEL_LIVE_ID = "jokarz_live_shift_chip_v4"
        const val NOTIFICATION_LIVE_ID = 1003

        fun start(context: Context) {
            val intent = Intent(context, LiveShiftService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveShiftService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = TimeclockRepository(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                notificationManager.deleteNotificationChannel("jokarz_live_shift_channel")
                notificationManager.deleteNotificationChannel("jokarz_live_shift_island_v3")
            } catch (e: Exception) {
                // Ignore
            }

            val liveChannel = NotificationChannel(
                CHANNEL_LIVE_ID,
                "Live Shift Status Bar Chip",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live ongoing shift chronometer chip in Status Bar and Dynamic Island"
                setShowBadge(true)
                setSound(null, null) // Completely silent so it acts as an ongoing live activity
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(liveChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = repository.state.value
        if (!state.isClockedIn || state.currentSessionStart == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val initialNotification = buildNotification(state, System.currentTimeMillis())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_LIVE_ID,
                initialNotification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else 0
            )
        } else {
            startForeground(NOTIFICATION_LIVE_ID, initialNotification)
        }

        startStateMonitoring()
        return START_STICKY
    }

    private fun startStateMonitoring() {
        stateCollectJob?.cancel()
        stateCollectJob = serviceScope.launch {
            repository.state.collectLatest { state ->
                if (!state.isClockedIn || state.currentSessionStart == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val notification = buildNotification(state, System.currentTimeMillis())
                    notificationManager.notify(NOTIFICATION_LIVE_ID, notification)
                }
            }
        }

        // Ambient refresh loop: update subtitle every 30 seconds (NOT every 1 second, so the native chronometer chip stays stable)
        refreshJob?.cancel()
        refreshJob = serviceScope.launch {
            while (isActive) {
                delay(30000L)
                val state = repository.state.value
                if (state.isClockedIn && state.currentSessionStart != null) {
                    val notification = buildNotification(state, System.currentTimeMillis())
                    notificationManager.notify(NOTIFICATION_LIVE_ID, notification)
                }
            }
        }
    }

    private fun buildNotification(state: TimeclockState, currentTickMs: Long): Notification {
        val startMs = state.currentSessionStart ?: currentTickMs
        val elapsedMs = max(0L, currentTickMs - startMs)
        val settings = state.settings
        val mealBreakToAdd = if (settings.autoBreakDeduction) settings.unpaidMealDuration else 0.0
        val standardTargetMs = ((settings.standardShiftHours + mealBreakToAdd) * 3600000.0).toLong()
        val cliffTargetMs = (settings.cliffHours * 3600000.0).toLong()

        val cal = Calendar.getInstance().apply { timeInMillis = startMs }
        val isMonThu = cal.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.THURSDAY

        val statusText = if (state.isOnBreak) {
            val breakElapsed = state.accumulatedBreakMs + (currentTickMs - (state.breakStartTime ?: currentTickMs))
            "On Break / Lunch • ${PayrollEngine.formatDuration(breakElapsed)}"
        } else if (isMonThu) {
            val prevBanked = PayrollEngine.getPreviousBankedHoursForCurrentWeek(startMs, state)
            val targetStandardHrs = (settings.standardShiftHours + mealBreakToAdd) - prevBanked
            val standardMs = (targetStandardHrs * 3600000.0).toLong()

            if (elapsedMs < standardMs) {
                val remainingMs = standardMs - elapsedMs
                val bankNote = if (abs(prevBanked) > 0.05) {
                    val sign = if (prevBanked > 0) "+" else ""
                    " ($sign${String.format("%.1f", prevBanked)}h bank)"
                } else ""
                "Remaining: ${PayrollEngine.formatDurationShort(remainingMs)}$bankNote"
            } else if (elapsedMs < cliffTargetMs) {
                val bankingHrs = (elapsedMs - standardMs) / 3600000.0
                "Banking Buffer: +${String.format("%.2f", bankingHrs)}h (Unpaid)"
            } else {
                val otHours = (elapsedMs - standardTargetMs) / 3600000.0
                val rate = if (state.displayMode == PayMode.GROSS) state.grossRate else state.netRate
                val otPay = otHours * rate * settings.otMultiplier
                val moneyStr = if (settings.hideMoneyAmounts) "" else " • ${PayrollEngine.formatMoney(otPay)}"
                "Overtime (${settings.otMultiplier}x): ${String.format("%.2f", otHours)}h$moneyStr"
            }
        } else {
            val elapsedHrs = elapsedMs / 3600000.0
            val payableHours = if (elapsedHrs > 4.0) elapsedHrs - 0.5 else elapsedHrs
            val rate = if (state.displayMode == PayMode.GROSS) state.grossRate else state.netRate
            val pay = maxOf(0.0, payableHours * rate * settings.otMultiplier)
            val moneyStr = if (settings.hideMoneyAmounts) "" else " • ${PayrollEngine.formatMoney(pay)}"
            "Weekend Overtime: ${String.format("%.2f", payableHours)}h$moneyStr"
        }

        // Tap opens app
        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingContentIntent = PendingIntent.getActivity(
            this, 10, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Break toggle
        val breakIntent = Intent(Intent.ACTION_VIEW, Uri.parse("jokarz://timeclock?action=break"), this, MainActivity::class.java)
        val pendingBreakIntent = PendingIntent.getActivity(
            this, 11, breakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Clock Out
        val clockOutIntent = Intent(Intent.ACTION_VIEW, Uri.parse("jokarz://timeclock?action=clock_out"), this, MainActivity::class.java)
        val pendingClockOutIntent = PendingIntent.getActivity(
            this, 12, clockOutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Static, stable title ensures the system UI maintains the status bar chip
        val title = if (state.isOnBreak) "⏸️ Shift Paused (Lunch)" else "⏱️ Shift Active"

        // ColorOS Aqua Dynamics / OnePlus Fluid Cloud & Android 16 Live Activity bundle extras
        val liveExtras = Bundle().apply {
            // Android 16 Rich Ongoing Notification Promoted Status
            putBoolean("android.promotedOngoing", true)
            putBoolean("android.extra.promoted_ongoing", true)
            putString("android.extra.ongoing_activity_type", "stopwatch")
            putBoolean("android.substName", true)

            // ColorOS / OxygenOS Fluid Cloud / Aqua Dynamics (Oppo Find X / OnePlus)
            putBoolean("oplus.isLiveAlert", true)
            putBoolean("oplus.capsule.enable", true)
            putString("oplus.liveAlert.type", "stopwatch")
            putString("oplus_view_type", "capsule")
            putString("capsule_type", "stopwatch")
            putBoolean("com.oplus.notification.isLiveAlert", true)
            putString("com.oplus.notification.capsule_type", "stopwatch")
            putString("oplus.capsule.title", if (state.isOnBreak) "Paused" else "Shift Active")
            putString("oplus.capsule.text", statusText)
        }

        return NotificationCompat.Builder(this, CHANNEL_LIVE_ID)
            .setSmallIcon(R.drawable.ic_stat_stopwatch)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSubText("Jokarz Timeclock")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(!state.isOnBreak)
            .setUsesChronometer(!state.isOnBreak)
            .setChronometerCountDown(false)
            .setWhen(startMs)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .addExtras(liveExtras)
            .setContentIntent(pendingContentIntent)
            .addAction(
                0,
                if (state.isOnBreak) "▶️ Resume Shift" else "⏸️ Lunch / Pause",
                pendingBreakIntent
            )
            .addAction(
                0,
                "⏹️ Clock Out",
                pendingClockOutIntent
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stateCollectJob?.cancel()
        refreshJob?.cancel()
    }
}

