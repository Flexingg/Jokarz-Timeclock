package com.randallengineering.jokarztimeclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.randallengineering.jokarztimeclock.MainActivity
import com.randallengineering.jokarztimeclock.R
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Builds and pushes the home-screen widgets (a clock in/out widget and a pay stats widget) from the
 * shared [TimeclockRepository] state. Plain RemoteViews: no extra dependency, and the live timer is a
 * system-driven Chronometer so the widget never needs a periodic refresh to tick.
 *
 * Two things are decided per widget instance, every time it is drawn:
 *  - its colours come from the app's current UI theme ([WidgetPalette]); every shape is a white
 *    drawable tinted at runtime, so one set of layouts serves every theme;
 *  - its layout comes from its current size ([WidgetLayoutPolicy]), so a resized widget shows more
 *    (or less) rather than stretching one design.
 */
object WidgetUpdater {

    const val ACTION_TOGGLE_CLOCK = "com.randallengineering.jokarztimeclock.widget.TOGGLE_CLOCK"
    const val ACTION_TOGGLE_BREAK = "com.randallengineering.jokarztimeclock.widget.TOGGLE_BREAK"

    /** Refreshes every placed widget (state, theme or system dark mode changed). */
    fun updateAll(context: Context) {
        val app = context.applicationContext
        try {
            val manager = AppWidgetManager.getInstance(app)
            for (id in manager.getAppWidgetIds(ComponentName(app, ClockWidgetProvider::class.java))) updateClock(app, manager, id)
            for (id in manager.getAppWidgetIds(ComponentName(app, StatsWidgetProvider::class.java))) updateStats(app, manager, id)
        } catch (e: Exception) {
            android.util.Log.w("WidgetUpdater", "Widget refresh failed", e)
        }
    }

    internal fun updateClock(context: Context, manager: AppWidgetManager, id: Int) =
        manager.updateAppWidget(id, buildClock(context, sizeOf(context, manager.getAppWidgetOptions(id), DEFAULT_CLOCK)))

    internal fun updateStats(context: Context, manager: AppWidgetManager, id: Int) =
        manager.updateAppWidget(id, buildStats(context, sizeOf(context, manager.getAppWidgetOptions(id), DEFAULT_STATS)))

    private val DEFAULT_CLOCK = WidgetSize(150, 150)
    private val DEFAULT_STATS = WidgetSize(300, 130)

    /** The widget's current size: the launcher reports a min/max box, which end is which depends on orientation. */
    internal fun sizeOf(context: Context, options: Bundle?, fallback: WidgetSize): WidgetSize {
        if (options == null) return fallback
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val w = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val h = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        return if (w > 0 && h > 0) WidgetSize(w, h) else fallback
    }

    // ---- Clock widget ----------------------------------------------------------------------

    internal fun buildClock(context: Context, size: WidgetSize): RemoteViews {
        val state = TimeclockRepository.get(context).state.value
        val p = WidgetPalette.of(context, state.settings.theme)
        val start = state.currentSessionStart
        val clockedIn = state.isClockedIn && start != null
        val layout = WidgetLayoutPolicy.clock(size)
        val views = RemoteViews(
            context.packageName,
            when (layout) {
                WidgetLayoutPolicy.Clock.TINY -> R.layout.widget_clock_tiny
                WidgetLayoutPolicy.Clock.WIDE -> R.layout.widget_clock_wide
                WidgetLayoutPolicy.Clock.MEDIUM -> R.layout.widget_clock
                WidgetLayoutPolicy.Clock.LARGE -> R.layout.widget_clock_large
            }
        )
        if (layout != WidgetLayoutPolicy.Clock.TINY) applyCard(views, p)

        // The running time: a system Chronometer, based on the shift's real start.
        if (clockedIn) {
            val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - start!!)
            views.setChronometer(R.id.widget_timer, base, null, !state.isOnBreak)
            views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
            views.setViewVisibility(R.id.widget_idle, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_timer, View.GONE)
            views.setViewVisibility(R.id.widget_idle, View.VISIBLE)
        }

        // The primary button: accent while clocked out, error while clocked in.
        val onAction = if (clockedIn) p.onError else p.onPrimary
        views.setInt(R.id.widget_action_bg, "setColorFilter", if (clockedIn) p.error else p.primary)
        views.setTextColor(R.id.widget_action, onAction)
        views.setInt(R.id.widget_action_icon, "setColorFilter", onAction)
        views.setImageViewResource(R.id.widget_action_icon, if (clockedIn) R.drawable.ic_widget_stop else R.drawable.ic_widget_play)
        views.setOnClickPendingIntent(R.id.widget_action_row, actionPendingIntent(context, ACTION_TOGGLE_CLOCK, 1))

        if (layout == WidgetLayoutPolicy.Clock.TINY) {
            views.setTextColor(R.id.widget_timer, onAction)
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
            return views
        }

        val w = size.widthDp
        // The break button only exists during a shift, and a narrow short widget drops it.
        val showBreak = clockedIn && !(layout == WidgetLayoutPolicy.Clock.WIDE && w < WidgetLayoutPolicy.WIDE_BREAK_MIN_WIDTH_DP)
        val hpad = if (layout == WidgetLayoutPolicy.Clock.LARGE) 20 else if (layout == WidgetLayoutPolicy.Clock.WIDE) 16 else 18
        val breakDp = when (layout) {
            WidgetLayoutPolicy.Clock.LARGE -> WidgetLayoutPolicy.BREAK_LARGE_DP
            WidgetLayoutPolicy.Clock.WIDE -> WidgetLayoutPolicy.BREAK_WIDE_DP
            else -> WidgetLayoutPolicy.BREAK_MEDIUM_DP
        }
        val timeReserve = if (layout == WidgetLayoutPolicy.Clock.WIDE) WidgetLayoutPolicy.WIDE_TIMER_RESERVE_DP else 0
        val label = WidgetLayoutPolicy.actionLabel(clockedIn, w, hpad, (if (showBreak) breakDp else 0) + timeReserve)
        views.setViewVisibility(R.id.widget_action, if (label != null) View.VISIBLE else View.GONE)
        if (label != null) views.setTextViewText(R.id.widget_action, label)

        // Time text sized to the room: a wide card gives it the leftover after the buttons.
        val timerSp = when (layout) {
            WidgetLayoutPolicy.Clock.WIDE ->
                WidgetLayoutPolicy.timerSp(w, hpad, (if (showBreak) breakDp else 0) + (if (label != null) 150 else 52), 18, 30)
            WidgetLayoutPolicy.Clock.LARGE -> WidgetLayoutPolicy.timerSp(
                w, hpad, 0, 24,
                minOf(52, WidgetLayoutPolicy.timerCapByHeight(size.heightDp, WidgetLayoutPolicy.CLOCK_LARGE_FIXED_DP)).coerceAtLeast(24)
            )
            else -> {
                val caption = if (size.heightDp >= WidgetLayoutPolicy.MEDIUM_CAPTION_MIN_HEIGHT_DP) WidgetLayoutPolicy.CAPTION_LINE_DP else 0
                WidgetLayoutPolicy.timerSp(
                    w, hpad, 0, 22,
                    minOf(34, WidgetLayoutPolicy.timerCapByHeight(size.heightDp, WidgetLayoutPolicy.CLOCK_MEDIUM_FIXED_DP + caption)).coerceAtLeast(22)
                )
            }
        }
        views.setTextViewTextSize(R.id.widget_timer, TypedValue.COMPLEX_UNIT_SP, timerSp.toFloat())
        views.setTextViewTextSize(R.id.widget_idle, TypedValue.COMPLEX_UNIT_SP, timerSp.toFloat())

        val statusColor = when {
            !clockedIn -> p.onSurfaceVariant
            state.isOnBreak -> p.secondary
            else -> p.tertiary
        }
        views.setTextViewText(
            R.id.widget_status,
            when {
                !clockedIn -> "READY FOR SHIFT"
                state.isOnBreak -> "ON BREAK"
                else -> "ACTIVE SHIFT"
            }
        )
        views.setTextColor(R.id.widget_status, p.onSurfaceVariant)
        views.setInt(R.id.widget_dot, "setColorFilter", statusColor)
        views.setInt(R.id.widget_dot_halo, "setColorFilter", statusColor)
        views.setTextColor(R.id.widget_timer, p.onSurface)
        views.setTextColor(R.id.widget_idle, p.onSurface)
        views.setTextColor(R.id.widget_caption, p.onSurfaceVariant)

        val todayStats = PayrollEngine.calculatePeriodTotals(state).todayStats
        views.setTextViewText(R.id.widget_idle, PayrollEngine.formatDurationShort(todayStats?.clockedMs ?: 0L))
        views.setTextViewText(
            R.id.widget_caption,
            if (clockedIn) "since ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(start!!))}" else "worked today"
        )

        // Break toggle: pause while working, play while on break.
        views.setViewVisibility(R.id.widget_break_row, if (showBreak) View.VISIBLE else View.GONE)
        views.setInt(R.id.widget_break_bg, "setColorFilter", p.onSurface)
        views.setInt(R.id.widget_break_edge, "setColorFilter", p.onSurface)
        views.setImageViewResource(R.id.widget_break_icon, if (state.isOnBreak) R.drawable.ic_widget_play else R.drawable.ic_widget_pause)
        views.setInt(R.id.widget_break_icon, "setColorFilter", p.onSurface)
        views.setOnClickPendingIntent(R.id.widget_break_row, actionPendingIntent(context, ACTION_TOGGLE_BREAK, 3))

        when (layout) {
            WidgetLayoutPolicy.Clock.WIDE -> {
                // Short and wide: the status line only when there is room beside the buttons.
                val statusRoom = if (w >= 300) View.VISIBLE else View.GONE
                views.setViewVisibility(R.id.widget_status, statusRoom)
                views.setViewVisibility(R.id.widget_dot, statusRoom)
                views.setViewVisibility(R.id.widget_dot_halo, statusRoom)
            }
            WidgetLayoutPolicy.Clock.MEDIUM -> {
                views.setViewVisibility(
                    R.id.widget_caption,
                    if (size.heightDp >= WidgetLayoutPolicy.MEDIUM_CAPTION_MIN_HEIGHT_DP) View.VISIBLE else View.GONE
                )
            }
            WidgetLayoutPolicy.Clock.LARGE -> {
                val totals = PayrollEngine.calculatePeriodTotals(state)
                val hide = state.settings.hideMoneyAmounts
                views.setInt(R.id.widget_divider, "setColorFilter", p.onSurface)
                views.setImageAlpha(R.id.widget_divider, 70)
                views.setTextColor(R.id.widget_today_label, p.onSurfaceVariant)
                views.setTextColor(R.id.widget_period_label, p.onSurfaceVariant)
                views.setTextViewText(R.id.widget_period_label, if (w >= 200) "PAY PERIOD" else "PERIOD")
                views.setTextViewText(R.id.widget_today_money, PayrollEngine.formatMoney(totals.todayEarnings, hide))
                views.setTextViewText(R.id.widget_period_money, PayrollEngine.formatMoney(totals.periodEarnings, hide))
                views.setTextColor(R.id.widget_today_money, p.onSurface)
                views.setTextColor(R.id.widget_period_money, p.onSurface)
            }
            WidgetLayoutPolicy.Clock.TINY -> Unit
        }
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        return views
    }

    // ---- Stats widget ----------------------------------------------------------------------

    internal fun buildStats(context: Context, size: WidgetSize): RemoteViews {
        val state = TimeclockRepository.get(context).state.value
        val p = WidgetPalette.of(context, state.settings.theme)
        val totals = PayrollEngine.calculatePeriodTotals(state)
        val hide = state.settings.hideMoneyAmounts
        val layout = WidgetLayoutPolicy.stats(size)
        val views = RemoteViews(
            context.packageName,
            when (layout) {
                WidgetLayoutPolicy.Stats.COMPACT -> R.layout.widget_stats_compact
                WidgetLayoutPolicy.Stats.NORMAL -> R.layout.widget_stats
                WidgetLayoutPolicy.Stats.LARGE -> R.layout.widget_stats_large
            }
        )
        applyCard(views, p)

        val fmt = SimpleDateFormat("M/d", Locale.US)
        val periodLabel = "${fmt.format(Date(totals.startOfPeriod))} - ${fmt.format(Date(totals.endOfPeriod))}"
        val todayHours = "${PayrollEngine.formatDurationShort(totals.todayStats?.clockedMs ?: 0L)} clocked"
        val periodHours = "${PayrollEngine.formatDurationShort(totals.totalClockedMsPeriod)} clocked"
        val todayMoney = PayrollEngine.formatMoney(totals.todayEarnings, hide)
        val periodMoney = PayrollEngine.formatMoney(totals.periodEarnings, hide)

        chip(views, R.id.widget_today_label, R.id.widget_today_label_bg, "TODAY", p)
        views.setTextViewText(R.id.widget_today_money, todayMoney)
        views.setTextViewText(R.id.widget_today_hours, todayHours)
        views.setTextColor(R.id.widget_today_money, p.onSurface)
        views.setTextColor(R.id.widget_today_hours, p.onSurfaceVariant)

        if (layout == WidgetLayoutPolicy.Stats.COMPACT) {
            views.setTextViewText(R.id.widget_period_line, "Period $periodMoney")
            views.setTextColor(R.id.widget_period_line, p.onSurfaceVariant)
        } else {
            chip(views, R.id.widget_period_label, R.id.widget_period_label_bg, periodLabel, p)
            views.setTextViewText(R.id.widget_period_money, periodMoney)
            views.setTextViewText(R.id.widget_period_hours, periodHours)
            views.setTextColor(R.id.widget_period_money, p.onSurface)
            views.setTextColor(R.id.widget_period_hours, p.onSurfaceVariant)
            views.setInt(R.id.widget_vdivider, "setColorFilter", p.onSurface)
            views.setImageAlpha(R.id.widget_vdivider, 70)
        }

        if (layout == WidgetLayoutPolicy.Stats.LARGE) {
            val week = currentWeekSummary(state)
            views.setInt(R.id.widget_divider, "setColorFilter", p.onSurface)
            views.setImageAlpha(R.id.widget_divider, 70)
            val bank = week.bankedHours
            val bankText = (if (bank >= 0) "+" else "") + String.format(Locale.US, "%.1fh", bank)
            views.setTextViewText(R.id.widget_wk_clocked_value, PayrollEngine.formatDurationShort(week.clockedMs))
            views.setTextViewText(R.id.widget_wk_ot_value, String.format(Locale.US, "%.1fh", week.systemInputHours))
            views.setTextViewText(R.id.widget_wk_bank_value, bankText)
            for (label in intArrayOf(R.id.widget_wk_clocked_label, R.id.widget_wk_ot_label, R.id.widget_wk_bank_label)) {
                views.setTextColor(label, p.onSurfaceVariant)
            }
            views.setTextColor(R.id.widget_wk_clocked_value, p.onSurface)
            views.setTextColor(R.id.widget_wk_ot_value, if (week.systemInputHours > 0) (if (p.dark) p.onPrimaryContainer else p.primary) else p.onSurface)
            // Banked time follows the app: secondary when ahead, error when behind.
            views.setTextColor(R.id.widget_wk_bank_value, if (abs(bank) <= 0.05) p.onSurface else if (bank > 0) p.secondary else p.error)
        }

        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        return views
    }

    // ---- Shared pieces ---------------------------------------------------------------------

    /** Tints the card's four layers (base, top sheen, accent glow, contour) from the theme. */
    private fun applyCard(views: RemoteViews, p: WidgetPalette) {
        views.setInt(R.id.widget_bg_base, "setColorFilter", p.card)
        views.setImageAlpha(R.id.widget_bg_base, p.cardAlpha)
        views.setInt(R.id.widget_bg_sheen, "setColorFilter", p.sheen)
        views.setInt(R.id.widget_bg_glow, "setColorFilter", p.primary)
        views.setImageAlpha(R.id.widget_bg_glow, p.glowAlpha)
        views.setInt(R.id.widget_bg_edge, "setColorFilter", p.edge)
    }

    /** A small label pill: the container colour with its on-colour text, as the app's chips. */
    private fun chip(views: RemoteViews, textId: Int, bgId: Int, text: String, p: WidgetPalette) {
        views.setTextViewText(textId, text)
        views.setTextColor(textId, p.onPrimaryContainer)
        views.setInt(bgId, "setColorFilter", p.primaryContainer)
    }

    private fun RemoteViews.setImageAlpha(viewId: Int, alpha: Int) = setInt(viewId, "setImageAlpha", alpha)

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, WidgetActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openAppPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
