package com.randallengineering.jokarztimeclock.engine

/**
 * How often the app itself may refresh the live notification. Pure JVM.
 *
 * The timers in the chip are drawn by SystemUI (chronometer) and need no app refresh at all. The
 * overtime money and the elapsed text are plain strings, which SystemUI cannot animate, so
 * [LiveShiftService] re-posts them — but no faster than once per wall-clock minute. Per-second
 * re-posting is what broke the status bar chip and drained the battery in 2.6.x;
 * `NoPeriodicNotificationUpdateTest` fails the build if a faster delay appears.
 */
object LiveRefreshCadence {

    /** The shortest refresh interval allowed for app-side notification updates. */
    const val MIN_REFRESH_INTERVAL_MS = 60_000L

    /**
     * A wakeup that lands closer than this to a minute boundary is treated as having hit it, so an
     * early or late timer wakeup cannot produce two refreshes inside the same minute.
     */
    private const val BOUNDARY_SLACK_MS = 1_000L

    fun isAllowedRefreshInterval(intervalMs: Long): Boolean = intervalMs >= MIN_REFRESH_INTERVAL_MS

    /**
     * The first whole minute strictly after [nowMs]. Epoch minutes are local wall-clock minutes in
     * every zone with a whole-minute UTC offset (all current ones), so the figure changes on the
     * minute like a clock.
     */
    fun nextMinuteBoundaryMs(nowMs: Long): Long =
        (Math.floorDiv(nowMs, MIN_REFRESH_INTERVAL_MS) + 1) * MIN_REFRESH_INTERVAL_MS

    /**
     * Sleep length to the next minute boundary. The first sleep after start is shorter than a
     * minute (to get onto the boundary); after that, wakeups are one wall-clock minute apart. Never
     * less than [BOUNDARY_SLACK_MS] — a wakeup just before a boundary skips to the following one.
     */
    fun delayUntilNextMinuteBoundary(nowMs: Long): Long {
        val delay = nextMinuteBoundaryMs(nowMs) - nowMs
        return if (delay < BOUNDARY_SLACK_MS) delay + MIN_REFRESH_INTERVAL_MS else delay
    }

    /**
     * Next wakeup: the next minute boundary, or the clock-out instant if that comes first. Waking
     * at the target lets the chip switch from the countdown back to the elapsed timer on time
     * instead of showing a negative countdown for up to a minute. That adds one wakeup per shift,
     * not a faster cadence: once the target has passed only minute boundaries remain.
     */
    fun nextWakeDelayMs(nowMs: Long, clockOutAtMs: Long?): Long {
        val toMinute = delayUntilNextMinuteBoundary(nowMs)
        if (clockOutAtMs == null || clockOutAtMs <= nowMs) return toMinute
        return minOf(toMinute, clockOutAtMs - nowMs)
    }
}
