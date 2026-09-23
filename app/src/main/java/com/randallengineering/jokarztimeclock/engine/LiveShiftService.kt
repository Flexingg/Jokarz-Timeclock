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
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.randallengineering.jokarztimeclock.AppVersion
import com.randallengineering.jokarztimeclock.MainActivity
import com.randallengineering.jokarztimeclock.R
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the single, ongoing **status bar chronometer** for the running shift.
 *
 * ## How the timer moves
 *
 * The timer you see is drawn by the Android SystemUI itself:
 * [NotificationCompat.Builder.setUsesChronometer]`(true)` + `setWhen(instant)`. SystemUI re-reads
 * `when` and animates the counter on its own; the app is not involved at all. While the clock-out
 * target ([ShiftClockOutTarget]) is still ahead, the chronometer counts **down** to it
 * (`setChronometerCountDown(true)` + `setWhen(clockOutAt)`); once it has passed (or cannot be
 * computed) it falls back to counting up from the session start. [LiveChipText] decides which.
 *
 * ## When the notification is (re)posted
 *
 *  1. the service starts (cold start, or restart by the system after a process kill),
 *  2. [TimeclockRepository.state] emits a *different* state — clock in, clock out, break toggle,
 *     edit of the start instant, or a settings change, and
 *  3. a coarse **once-per-wall-clock-minute** refresh, and one wakeup at the clock-out instant.
 *
 * Why (3) exists: only *time* can be animated by the system. The overtime money and the elapsed
 * text in the content line are plain strings — SystemUI cannot make them rise — so the app has to
 * re-post them. That refresh is limited to [LiveRefreshCadence] (the next minute boundary, never
 * sub-minute), re-posts only when the rendered [LiveChipText] actually changed, never touches the
 * chronometer (SystemUI owns that), and stops with the service. Per-second re-posting is what broke
 * the chip and drained the battery in 2.6.x; `NoPeriodicNotificationUpdateTest` fails the build if
 * a faster delay or any other timer mechanism appears in this file.
 *
 * ## Android 16 Live Update
 *
 * The notification also meets every requirement for a promoted-ongoing ("Live Update") notification:
 * ongoing, `setRequestPromotedOngoing(true)`, a content title, [NotificationCompat.ProgressStyle],
 * no custom views, not colorized, not a group summary, on a non-MIN channel, plus the
 * `POST_PROMOTED_NOTIFICATIONS` manifest permission. Whether the OS actually promotes it is read back
 * by [LiveChipStatusReader], not assumed.
 *
 * The start instant is always read back from storage ([TimeclockRepository] persists to a JSON file
 * in `filesDir`), never from a field in memory, so a killed-and-restarted process reconstructs the
 * notification with the original `when` and therefore the correct elapsed time.
 */
class LiveShiftService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateCollectJob: Job? = null
    private var minuteRefreshJob: Job? = null
    private var lastPostedSignature: String? = null
    private var lastPostedChip: LiveChipText? = null
    private var lastClockedIn = false
    private lateinit var repository: TimeclockRepository
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val TAG = "LiveShiftService"

        // Bumped to v5 so existing installs get a fresh channel with these settings (channel
        // settings are immutable once created). IMPORTANCE_HIGH + no sound = silent ongoing chip;
        // importance must not be MIN or Android refuses to promote it.
        const val CHANNEL_LIVE_ID = "jokarz_live_shift_chip_v5"
        const val CHANNEL_LIVE_NAME = "Live Shift Status Bar Chip"
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
        repository = TimeclockRepository.get(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                notificationManager.deleteNotificationChannel("jokarz_live_shift_channel")
                notificationManager.deleteNotificationChannel("jokarz_live_shift_island_v3")
                notificationManager.deleteNotificationChannel("jokarz_live_shift_chip_v4")
            } catch (e: Exception) {
                // Ignore
            }

            val liveChannel = NotificationChannel(
                CHANNEL_LIVE_ID,
                CHANNEL_LIVE_NAME,
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
                TaskerBridge(applicationContext).sendEvent(
                    TaskerContract.EVENT_CLOCK_OUT, TaskerContract.SOURCE_NOTIFICATION, repository.state.value.settings
                )
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

        val nowMs = System.currentTimeMillis()
        val initialChip = LiveChipText.build(state, nowMs)
        val initialNotification = buildNotification(state, nowMs, initialChip)
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
            lastPostedChip = initialChip
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
     * Event-driven: this collector re-posts the notification when the stored state changes.
     * If a state emission arrives that produces identical notification content, nothing is posted.
     * It also starts / cancels the minute refresh, which only runs while clocked in with the live
     * notification enabled.
     */
    private fun startStateMonitoring() {
        stateCollectJob?.cancel()
        stateCollectJob = serviceScope.launch {
            repository.state.collectLatest { state ->
                if (!state.isClockedIn || state.currentSessionStart == null) {
                    minuteRefreshJob?.cancel()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    notificationManager.cancel(NOTIFICATION_LIVE_ID)
                    stopSelf()
                    return@collectLatest
                }
                if (!state.settings.liveNotificationEnabled) {
                    // Preference turned off in Settings: remove the chip, keep tracking.
                    minuteRefreshJob?.cancel()
                    if (lastClockedIn) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        notificationManager.cancel(NOTIFICATION_LIVE_ID)
                    }
                    lastClockedIn = true
                    return@collectLatest
                }
                lastClockedIn = true

                postIfChanged(state, System.currentTimeMillis())
                startMinuteRefresh()
            }
        }
    }

    /**
     * The coarse app-side refresh for the text SystemUI cannot animate (overtime money, elapsed
     * minutes). One coroutine, woken at the next wall-clock minute boundary — or at the clock-out
     * instant if sooner, so the chip flips from countdown to elapsed on time — never faster; see
     * [LiveRefreshCadence]. It ends itself when the shift ends or the chip is turned off, and is
     * cancelled in [onDestroy].
     */
    private fun startMinuteRefresh() {
        if (minuteRefreshJob?.isActive == true) return
        minuteRefreshJob = serviceScope.launch {
            while (isActive) {
                val nowMs = System.currentTimeMillis()
                val clockOutAt = ShiftClockOutTarget.clockOutAtMs(repository.state.value, nowMs)
                delay(LiveRefreshCadence.nextWakeDelayMs(nowMs, clockOutAt))
                val state = repository.state.value
                if (!state.isClockedIn || state.currentSessionStart == null ||
                    !state.settings.liveNotificationEnabled
                ) break
                postIfChanged(state, System.currentTimeMillis())
            }
        }
    }

    /**
     * Posts only when something visible changed: the state fingerprint ([signatureOf]) or the
     * rendered [LiveChipText]. A minute in which the text is unchanged costs no post at all.
     */
    private fun postIfChanged(state: TimeclockState, nowMs: Long) {
        val chip = LiveChipText.build(state, nowMs)
        val signature = signatureOf(state)
        if (signature == lastPostedSignature && chip == lastPostedChip) return
        lastPostedSignature = signature
        lastPostedChip = chip
        safeNotify(NOTIFICATION_LIVE_ID, buildNotification(state, nowMs, chip))
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
     * Builds the ongoing chronometer chip. This is the only place the live notification is built,
     * and it only assembles Android objects: the wording and the timer choice come from [chip]
     * ([LiveChipText.build], pure and unit-tested).
     *
     * `setWhen(chip.chronometerWhenMs)` + `setUsesChronometer(true)` (+ `setChronometerCountDown(true)`
     * while a clock-out target is ahead) + `setOngoing(true)` + `setOnlyAlertOnce(true)` is the whole
     * live-timer mechanism: SystemUI animates the value itself — in the promoted status bar chip on
     * Android 16+, and in the shade on older versions. There is no app-side ticking of the timer.
     *
     * `setShortCriticalText()` is deliberately NOT used: when set, it replaces the chip's content,
     * so the chip would show a frozen string instead of the system timer.
     */
    private fun buildNotification(state: TimeclockState, nowMs: Long, chip: LiveChipText): Notification {
        val startMs = state.currentSessionStart ?: nowMs
        val settings = state.settings
        val mealBreakToAdd = if (settings.autoBreakDeduction) settings.unpaidMealDuration else 0.0
        val targetHours = settings.standardShiftHours + mealBreakToAdd

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

        // The old hand-made oplus.* / android.extra.* "capsule" extras were removed: nothing in AOSP
        // or the ColorOS SDK reads those keys. Promotion is requested the documented way below.

        // Sampled at post time, so the bar moves only when the notification is re-posted: a state
        // change or the minute refresh (which posts only when the text changed). The live number
        // is the system chronometer.
        val plan = ShiftProgressScale.plan(nowMs - startMs, targetHours, settings.cliffHours)
        val progressStyle = NotificationCompat.ProgressStyle()
            // Segment lengths sum to ShiftProgressScale.MAX_MINUTES, which is the bar's max.
            .setProgressSegments(plan.segmentLengths.map { NotificationCompat.ProgressStyle.Segment(it) })
            .addProgressPoint(NotificationCompat.ProgressStyle.Point(plan.targetMark))
            .addProgressPoint(NotificationCompat.ProgressStyle.Point(plan.cliffMark))
            .setProgress(plan.progress)
            // No tracker icon: there is no drawable for it, and the default track is enough.
            .setStyledByProgress(false)

        return NotificationCompat.Builder(this, CHANNEL_LIVE_ID)
            .setSmallIcon(R.drawable.ic_stat_stopwatch)
            .setContentTitle(chip.title)
            .setContentText(chip.contentText)
            // Carries the build, so a screenshot of the notification proves what is installed.
            .setSubText(AppVersion.short)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            // Android 16 Live Update request (writes EXTRA_REQUEST_PROMOTED_ONGOING; no-op on older OS).
            .setRequestPromotedOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            // The system chronometer: SystemUI draws and animates the time from `when` — the
            // countdown to clock-out while it is ahead, else elapsed since the session start.
            .setUsesChronometer(true)
            .setWhen(chip.chronometerWhenMs)
            // Builder default is count-up, which is the fallback when the target is past or null.
            .apply { if (chip.countsDown) setChronometerCountDown(true) }
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(progressStyle)
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
        minuteRefreshJob?.cancel()
    }
}
