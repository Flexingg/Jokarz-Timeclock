package com.randallengineering.jokarztimeclock.widget

/** A widget's size in dp, as the launcher reports it. */
internal data class WidgetSize(val widthDp: Int, val heightDp: Int)

/**
 * Which layout a widget shows at a given size. Pure, so the thresholds are unit tested; the builder
 * only maps the result to a layout. As a widget grows it keeps everything it had and adds more.
 */
internal object WidgetLayoutPolicy {

    enum class Clock {
        /** About one cell: just the round clock in / out button (with the running time). */
        TINY,
        /** Short and wide: status + time on the left, break and clock in / out on the right. */
        WIDE,
        /** The standard 2x2 card. */
        MEDIUM,
        /** Tall: a bigger timer and today / pay-period earnings underneath. */
        LARGE
    }

    enum class Stats {
        /** Narrow: today's earnings, with the pay period as one line below. */
        COMPACT,
        /** Today and pay period side by side. */
        NORMAL,
        /** Tall: adds this week's hours, overtime input and net banked. */
        LARGE
    }

    const val TINY_MAX_WIDTH_DP = 100
    const val WIDE_MAX_HEIGHT_DP = 120
    const val CLOCK_LARGE_MIN_HEIGHT_DP = 200
    const val CLOCK_LARGE_MIN_WIDTH_DP = 140
    /** Below this width the wide layout drops the break button (the time and Clock Out matter most). */
    const val WIDE_BREAK_MIN_WIDTH_DP = 200
    /** Below this height the standard card hides its caption line so the button row still fits. */
    const val MEDIUM_CAPTION_MIN_HEIGHT_DP = 140

    const val STATS_COMPACT_MAX_WIDTH_DP = 190
    const val STATS_LARGE_MIN_HEIGHT_DP = 170

    fun clock(size: WidgetSize): Clock = when {
        size.widthDp < TINY_MAX_WIDTH_DP -> Clock.TINY
        size.heightDp < WIDE_MAX_HEIGHT_DP -> Clock.WIDE
        size.heightDp >= CLOCK_LARGE_MIN_HEIGHT_DP && size.widthDp >= CLOCK_LARGE_MIN_WIDTH_DP -> Clock.LARGE
        else -> Clock.MEDIUM
    }

    fun stats(size: WidgetSize): Stats = when {
        size.widthDp < STATS_COMPACT_MAX_WIDTH_DP -> Stats.COMPACT
        size.heightDp >= STATS_LARGE_MIN_HEIGHT_DP -> Stats.LARGE
        else -> Stats.NORMAL
    }

    /**
     * The timer's text size for a card [widthDp] wide with [horizontalPaddingDp] either side and
     * [reservedDp] taken by other things in its row. Seven characters ("1:23:45", "10h 33m") must fit,
     * so the size is a quarter of the room, between [minSp] and [maxSp].
     */
    fun timerSp(widthDp: Int, horizontalPaddingDp: Int, reservedDp: Int, minSp: Int, maxSp: Int): Int =
        ((widthDp - 2 * horizontalPaddingDp - reservedDp) / 4).coerceIn(minSp, maxSp)

    /**
     * The words on the Clock In / Clock Out pill for the room left beside the icon: the full label,
     * a short one ("Out" / "In"), or none (icon only).
     */
    fun actionLabel(clockedIn: Boolean, widthDp: Int, horizontalPaddingDp: Int, reservedDp: Int): String? {
        val room = widthDp - 2 * horizontalPaddingDp - reservedDp - PILL_PADDING_DP - ICON_AND_GAP_DP
        return when {
            room >= (if (clockedIn) FULL_OUT_DP else FULL_IN_DP) -> if (clockedIn) "Clock Out" else "Clock In"
            room >= SHORT_LABEL_DP -> if (clockedIn) "Out" else "In"
            else -> null
        }
    }

    private const val PILL_PADDING_DP = 22
    private const val ICON_AND_GAP_DP = 26
    private const val FULL_IN_DP = 58
    private const val FULL_OUT_DP = 70
    private const val SHORT_LABEL_DP = 26

    /** The room the wide layout keeps for the time itself when deciding whether the button gets a word. */
    const val WIDE_TIMER_RESERVE_DP = 64

    /** Fixed height of the large card's content apart from the timer line, and the line-height ratio. */
    const val CLOCK_LARGE_FIXED_DP = 161
    const val CLOCK_MEDIUM_FIXED_DP = 84
    const val CAPTION_LINE_DP = 14

    /** The biggest timer text that still leaves the rest of a card of [heightDp] on screen. */
    fun timerCapByHeight(heightDp: Int, fixedDp: Int): Int = ((heightDp - fixedDp) / 1.2f).toInt()

    /** Space the break button (plus its gap) takes in a row, by card layout. */
    const val BREAK_MEDIUM_DP = 48
    const val BREAK_LARGE_DP = 56
    const val BREAK_WIDE_DP = 50
}
