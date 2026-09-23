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
        val segmentLengths: List<Int>
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
            segmentLengths = listOf(target, cliff - target, MAX_MINUTES - cliff)
        )
    }
}
