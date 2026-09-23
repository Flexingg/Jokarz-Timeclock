package com.randallengineering.jokarztimeclock.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.randallengineering.jokarztimeclock.MainActivity
import com.randallengineering.jokarztimeclock.R
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Foreground service that owns the single, ongoing **status bar chronometer** for the running shift.
 *
 * ## How the timer moves
 *
 * The elapsed time you see is drawn by the Android SystemUI itself:
 * [NotificationCompat.Builder.setUsesChronometer]`(true)` + `setWhen(clockInInstant)`. SystemUI
 * re-reads `when` and animates the counter on its own; the app is not involved at all.
 *
 * There is deliberately **no periodic refresh loop** in this class. The notification is (re)posted
 * for exactly two reasons:
 *  1. the service starts (cold start, or restart by the system after a process kill), and
 *  2. [TimeclockRepository.state] emits a *different* state — clock in, clock out, break toggle,
 *     edit of the start instant, or the live-notification preference changing.
 *
 * Both are event driven. Nothing in this file sleeps, polls or ticks, so no wakeup is required to
 * keep the clock moving — the process can be frozen, backgrounded or swiped away and the counter
 * still runs. The subtitle text is a segment label ("Started 6:02 AM • 10.5h target"), not a
 * countdown, precisely so that it can stay correct without any app-side updating.
 *
 * The start instant is always read back from storage ([TimeclockRepository] persists to a JSON file
 * in `filesDir`), never from a field in memory, so a killed-and-restarted process reconstructs the
 * notification with the original `when` and therefore the correct elapsed time.
 */
class LiveShiftService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateCollectJob: Job? = null
    private var lastPostedSignature: String? = null
    private var lastClockedIn = false
    private lateinit var repository: TimeclockRepository
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val TAG = "LiveShiftService"

        // Updated channel ID so Android/ColorOS creates it with HIGH importance for status bar chips
        // and Live Alerts. IMPORTANCE_HIGH + no sound = a silent, ongoing "live activity" chip.
        const val CHANNEL_LIVE_ID = "jokarz_live_shift_chip_v4"
        const val NOTIFICATION_LIVE_ID = 1003

        const val ACTION_CLOCK_OUT = "com.randallengineering.jokarztimeclock.action.CLOCK_OUT"
        const val ACTION_TOGGLE_BREAK = "com.randallengineering.jokarztimeclock.action.TOGGLE_BREAK"

        fun start(context: Context) {
            val intent = Intent(context, LiveShiftService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Android 12+ can refuse a background foreground-service start. The app stays
                // usable; the live chip simply does not start until the app is opened again.
                Log.w(TAG, "Could not start live shift service", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, LiveShiftService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop live shift service", e)
            }
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
                description = "Ongoing system chronometer for the running shift (status bar chip)"
                setShowBadge(true)
                setSound(null, null) // Completely silent so it acts as an ongoing live activity
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(liveChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOCK_OUT -> {
                repository.clockOut()
                TaskerBridge(applicationContext).sendEvent("Clocked Out")
                // The state collector below tears the service down on the same emission.
            }
            ACTION_TOGGLE_BREAK -> {
                repository.toggleBreak()
            }
        }

        val state = repository.state.value
        if (!state.isClockedIn || state.currentSessionStart == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val initialNotification = buildNotification(state)
        try {
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
            lastPostedSignature = signatureOf(state)
        } catch (e: Exception) {
            // e.g. POST_NOTIFICATIONS revoked: degrade instead of crashing. The app keeps working.
            Log.w(TAG, "startForeground failed; continuing without the live chip", e)
        }

        startStateMonitoring()
        // START_STICKY: if the process is killed (ColorOS does this), the system restarts the
        // service; onStartCommand then rebuilds the chip from the persisted start instant.
        return START_STICKY
    }

    /**
     * Event-driven only: this collector re-posts the notification when the stored state changes.
     * If a state emission arrives that produces identical notification content, nothing is posted.
     */
    private fun startStateMonitoring() {
        stateCollectJob?.cancel()
        stateCollectJob = serviceScope.launch {
            repository.state.collectLatest { state ->
                if (!state.isClockedIn || state.currentSessionStart == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    notificationManager.cancel(NOTIFICATION_LIVE_ID)
                    stopSelf()
                    return@collectLatest
                }
                if (!state.settings.liveNotificationEnabled) {
                    // Preference turned off in Settings: remove the chip, keep tracking.
                    if (lastClockedIn) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        notificationManager.cancel(NOTIFICATION_LIVE_ID)
                    }
                    lastClockedIn = true
                    return@collectLatest
                }
                lastClockedIn = true

                val signature = signatureOf(state)
                if (signature == lastPostedSignature) return@collectLatest
                lastPostedSignature = signature
                safeNotify(NOTIFICATION_LIVE_ID, buildNotification(state))
            }
        }
    }

    /**
     * A cheap fingerprint of everything the chip shows. Two equal fingerprints must produce an
     * identical notification, which is what lets us skip redundant posts.
     */
    private fun signatureOf(state: TimeclockState): String {
        val start = state.currentSessionStart ?: 0L
        return "$start|${state.isOnBreak}|${state.accumulatedBreakMs}|${state.settings.liveNotificationEnabled}" +
            "|${state.settings.standardShiftHours}|${state.settings.cliffHours}|${state.settings.otMultiplier}" +
            "|${state.displayMode}|${state.settings.hideMoneyAmounts}"
    }

    /** Guarded because the service can start with PERMISSION_DENIED on POST_NOTIFICATIONS. */
    private fun safeNotify(id: Int, notification: Notification) {
        try {
            notificationManager.notify(id, notification)
        } catch (e: Exception) {
            Log.w(TAG, "notify failed", e)
        }
    }

    /**
     * Builds the ongoing chronometer chip.
     *
     * `setWhen(startMs)` + `setUsesChronometer(true)` + `setOngoing(true)` +
     * `setOnlyAlertOnce(true)` is the whole live-timer mechanism: SystemUI animates the elapsed
     * value itself. There is no app-side ticking anywhere.
     */
    private fun buildNotification(state: TimeclockState): Notification {
        val startMs = state.currentSessionStart ?: System.currentTimeMillis()
        val nowMs = System.currentTimeMillis()
        val settings = state.settings
        val mealBreakToAdd = if (settings.autoBreakDeduction) settings.unpaidMealDuration else 0.0

        val cal = Calendar.getInstance().apply { timeInMillis = startMs }
        val isMonThu = cal.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.THURSDAY
        val startedAt = SimpleDateFormat("h:mm a", Locale.US).format(Date(startMs))
        val targetHours = settings.standardShiftHours + mealBreakToAdd

        // Segment label, NOT a countdown: stable for as long as the segment lasts, so the chip never
        // needs a periodic refresh to stay truthful. The live elapsed value is the OS chronometer.
        val statusText = when {
            state.isOnBreak -> {
                val breakStart = state.breakStartTime
                val since = if (breakStart != null) SimpleDateFormat("h:mm a", Locale.US).format(Date(breakStart)) else null
                if (since != null) "On break since $since" else "On break"
            }
            isMonThu -> {
                val elapsedHrs = (nowMs - startMs) / 3600000.0
                when {
                    elapsedHrs < targetHours -> "Started $startedAt • ${trimHours(targetHours)}h target"
                    elapsedHrs < settings.cliffHours -> "Banking buffer (unpaid) • started $startedAt"
                    else -> "Overtime accruing (${trimHours(settings.otMultiplier)}x) • started $startedAt"
                }
            }
            else -> "Weekend overtime (100%) • started $startedAt"
        }

        // Tap opens the app on the main screen (single instance, cleared stack).
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingContentIntent = PendingIntent.getActivity(
            this, 10, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: break toggle — handled inside the service, so it works without launching the UI.
        val breakIntent = Intent(this, LiveShiftService::class.java).setAction(ACTION_TOGGLE_BREAK)
        val pendingBreakIntent = PendingIntent.getService(
            this, 11, breakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: clock out — also handled inside the service.
        val clockOutIntent = Intent(this, LiveShiftService::class.java).setAction(ACTION_CLOCK_OUT)
        val pendingClockOutIntent = PendingIntent.getService(
            this, 12, clockOutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Static, stable title keeps the status bar chip / capsule alive on ColorOS.
        val title = if (state.isOnBreak) "Shift Paused" else "Shift Active"

        // ColorOS Aqua Dynamics / Fluid Cloud & Android 16 promoted-ongoing bundle extras.
        val liveExtras = Bundle().apply {
            putBoolean("android.promotedOngoing", true)
            putBoolean("android.extra.promoted_ongoing", true)
            putString("android.extra.ongoing_activity_type", "stopwatch")
            putBoolean("android.substName", true)

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
            .setShowWhen(true)
            // The system chronometer: SystemUI draws and animates the elapsed time from `when`.
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setWhen(startMs)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .addExtras(liveExtras)
            .setContentIntent(pendingContentIntent)
            .addAction(
                0,
                if (state.isOnBreak) "Resume Shift" else "Lunch / Pause",
                pendingBreakIntent
            )
            .addAction(
                0,
                "Clock Out",
                pendingClockOutIntent
            )
            .build()
    }

    private fun trimHours(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

    /**
     * Swiping the app away must not stop the shift: the service is not declared with
     * `android:stopWithTask`, so the foreground service (and therefore the chip) keeps running.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "Task removed; live shift service keeps running (shift still open)")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stateCollectJob?.cancel()
    }
}
