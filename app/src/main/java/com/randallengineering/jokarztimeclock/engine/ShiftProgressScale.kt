package com.randallengineering.jokarztimeclock.engine

/**
 * The fixed scale behind the live notification's `ProgressStyle` bar. Pure Kotlin, no Android, so
 * the numbers are unit-testable.
 *
 * The bar is three segments: paid shift (0 → target), unpaid bank buffer (target → cliff) and
 * overtime (cliff → [MAX_MINUTES]). `ProgressStyle`'s max is the sum of its segment lengths, so the
 * segments always add up to exactly [MAX_MINUTES]; the max never depends on "now" or on settings,
 * which keeps two posts of the same state identical.
 */
object ShiftProgressScale {

    /**
     * 12h45m = 765 minutes: a quarter hour past the default 12.5h overtime cliff, so the cliff mark
     * sits near the end of the bar with a visible overtime segment after it.
     */
    const val MAX_MINUTES = 765

    data class Plan(
        /** Elapsed minutes at post time, clamped to [0, MAX_MINUTES]. */
        val progress: Int,
        /** Bar positions (minutes) of the standard-shift boundary and the overtime cliff. */
        val targetMark: Int,
        val cliffMark: Int,
        /** Segment lengths; always sum to [MAX_MINUTES]. */
        val segmentLengths: List<Int>,
        /** The single "you are here" progress point; see [pointMark]. */
        val pointMark: Int,
        /** The value handed to `setProgress()`: [MAX_MINUTES] once the target is reached, else [progress]. */
        val barFill: Int
    )

    fun plan(elapsedMs: Long, targetHours: Double, cliffHours: Double): Plan {
        val progress = (elapsedMs / 60_000L).coerceIn(0L, MAX_MINUTES.toLong()).toInt()
        // Marks sit strictly inside the bar so a mis-set target/cliff can never produce an
        // empty or overflowing segment.
        val target = (targetHours * 60).toInt().coerceIn(1, MAX_MINUTES - 2)
        val cliff = (cliffHours * 60).toInt().coerceIn(target + 1, MAX_MINUTES - 1)
        return Plan(
            progress = progress,
            targetMark = target,
            cliffMark = cliff,
            segmentLengths = listOf(target, cliff - target, MAX_MINUTES - cliff),
            pointMark = pointMark(elapsedMs, targetHours),
            // Full line in overtime, so the fill agrees with the dot pinned at the far right.
            barFill = if (reachedTarget(elapsedMs, targetHours)) MAX_MINUTES else progress
        )
    }

    /**
     * Where the single progress point sits on the bar, on the same minute scale as the segments.
     *
     * - Before the shift starts (`elapsedMs <= 0`): 0, the left end.
     * - Mid-shift (`0 < elapsed < target`): the elapsed minutes, so the dot moves left → right.
     * - At or past the clock-out target: [MAX_MINUTES], pinned to the right end.
     *
     * The bar is a *shift* bar (start → clock-out target); the buffer and overtime segments after
     * the target only colour those zones. Once the target is reached the shift is complete, so the
     * dot is pinned at the far right and the line is full, while the live overtime time and money
     * keep updating in the content text and the system chronometer keeps running. Every branch is
     * non-decreasing in `elapsedMs` and the pinned value is the maximum, so the point never moves
     * backwards.
     */
    fun pointMark(elapsedMs: Long, targetHours: Double): Int = when {
        elapsedMs <= 0L -> 0
        reachedTarget(elapsedMs, targetHours) -> MAX_MINUTES
        // A target longer than the bar leaves late mid-shift minutes past the end; clamp them.
        else -> (elapsedMs / 60_000L).coerceAtMost(MAX_MINUTES.toLong()).toInt()
    }

    /** Compared in milliseconds against the raw target, so a fractional-minute target is not rounded early. */
    private fun reachedTarget(elapsedMs: Long, targetHours: Double): Boolean =
        elapsedMs > 0L && elapsedMs >= (targetHours * 3_600_000L).toLong()
}
