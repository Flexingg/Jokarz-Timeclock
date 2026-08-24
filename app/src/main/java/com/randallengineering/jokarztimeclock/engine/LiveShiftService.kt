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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

class LiveShiftService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null
    private lateinit var repository: TimeclockRepository
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val CHANNEL_LIVE_ID = "jokarz_live_shift_channel"
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
            val liveChannel = NotificationChannel(
                CHANNEL_LIVE_ID,
                "Live Shift Stopwatch & Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live ongoing shift chronometer and status in Android status bar"
                setShowBadge(false)
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

        startUpdateLoop()
        return START_STICKY
    }

    private fun startUpdateLoop() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (isActive) {
                val state = repository.state.value
                if (!state.isClockedIn || state.currentSessionStart == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                val notification = buildNotification(state, System.currentTimeMillis())
                notificationManager.notify(NOTIFICATION_LIVE_ID, notification)
                delay(1000L)
            }
        }
    }

    private fun buildNotification(state: TimeclockState, currentTickMs: Long): Notification {
        val startMs = state.currentSessionStart ?: currentTickMs
        val elapsedMs = max(0L, currentTickMs - startMs)
        val settings = state.settings
        val standardTargetMs = ((settings.standardShiftHours + settings.unpaidMealDuration) * 3600000.0).toLong()
        val cliffTargetMs = (settings.cliffHours * 3600000.0).toLong()

        val cal = Calendar.getInstance().apply { timeInMillis = startMs }
        val isMonThu = cal.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.THURSDAY

        val statusText = if (state.isOnBreak) {
            val breakElapsed = state.accumulatedBreakMs + (currentTickMs - (state.breakStartTime ?: currentTickMs))
            "On Break / Lunch • ${PayrollEngine.formatDuration(breakElapsed)}"
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

        val title = if (state.isOnBreak) "⏸️ On Lunch / Break" else "⏱️ Shift Active: ${PayrollEngine.formatDuration(elapsedMs)}"

        return NotificationCompat.Builder(this, CHANNEL_LIVE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSubText("Live Shift Tracker")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(!state.isOnBreak)
            .setUsesChronometer(!state.isOnBreak)
            .setWhen(startMs)
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
        updateJob?.cancel()
    }
}
