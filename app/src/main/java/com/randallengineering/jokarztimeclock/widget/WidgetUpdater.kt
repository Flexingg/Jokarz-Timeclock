package com.randallengineering.jokarztimeclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.randallengineering.jokarztimeclock.MainActivity
import com.randallengineering.jokarztimeclock.R
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository
import com.randallengineering.jokarztimeclock.engine.PayrollEngine

/**
 * Builds and pushes the home-screen widgets (a clock in/out widget and a pay stats widget) from the
 * shared [TimeclockRepository] state. Plain RemoteViews: no extra dependency, and the live timer is a
 * system-driven Chronometer so the widget never needs a periodic refresh to tick.
 */
object WidgetUpdater {

    const val ACTION_TOGGLE_CLOCK = "com.randallengineering.jokarztimeclock.widget.TOGGLE_CLOCK"
    const val ACTION_TOGGLE_BREAK = "com.randallengineering.jokarztimeclock.widget.TOGGLE_BREAK"

    fun updateAll(context: Context) {
        val app = context.applicationContext
        try {
            val manager = AppWidgetManager.getInstance(app)
            update(app, manager, ClockWidgetProvider::class.java) { buildClock(app) }
            update(app, manager, StatsWidgetProvider::class.java) { buildStats(app) }
        } catch (e: Exception) {
            android.util.Log.w("WidgetUpdater", "Widget refresh failed", e)
        }
    }

    private fun update(
        context: Context,
        manager: AppWidgetManager,
        provider: Class<*>,
        build: () -> RemoteViews
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isNotEmpty()) manager.updateAppWidget(ids, build())
    }

    internal fun buildClock(context: Context): RemoteViews {
        val state = TimeclockRepository.get(context).state.value
        val start = state.currentSessionStart
        val clockedIn = state.isClockedIn && start != null
        val views = RemoteViews(context.packageName, R.layout.widget_clock)

        views.setTextViewText(
            R.id.widget_status,
            when {
                !clockedIn -> "READY FOR SHIFT"
                state.isOnBreak -> "ON BREAK"
                else -> "ACTIVE SHIFT"
            }
        )
        views.setImageViewResource(
            R.id.widget_dot,
            when {
                !clockedIn -> R.drawable.widget_dot_idle
                state.isOnBreak -> R.drawable.widget_dot_break
                else -> R.drawable.widget_dot_active
            }
        )

        if (clockedIn) {
            // Chronometer counts from a SystemClock.elapsedRealtime() base.
            val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - start!!)
            views.setChronometer(R.id.widget_timer, base, null, !state.isOnBreak)
            views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
            views.setViewVisibility(R.id.widget_idle, View.GONE)
            views.setTextViewText(
                R.id.widget_caption,
                "since ${java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(start))}"
            )
            views.setTextViewText(R.id.widget_action, "Clock Out")
            views.setInt(R.id.widget_action_row, "setBackgroundResource", R.drawable.widget_button_stop)
            views.setTextColor(R.id.widget_action, 0xFF690005.toInt())
            // Text only: next to the break button there is no room for an icon on a 2-cell widget.
            views.setViewVisibility(R.id.widget_action_icon, View.GONE)

            // Break toggle: pause while working, play while on break.
            views.setViewVisibility(R.id.widget_break_row, View.VISIBLE)
            views.setImageViewResource(
                R.id.widget_break_icon,
                if (state.isOnBreak) R.drawable.ic_widget_play else R.drawable.ic_widget_pause
            )
            views.setInt(R.id.widget_break_icon, "setColorFilter", 0xFFF7EEFB.toInt())
            views.setOnClickPendingIntent(R.id.widget_break_row, actionPendingIntent(context, ACTION_TOGGLE_BREAK, 3))
        } else {
            views.setViewVisibility(R.id.widget_timer, View.GONE)
            views.setViewVisibility(R.id.widget_idle, View.VISIBLE)
            views.setViewVisibility(R.id.widget_break_row, View.GONE)
            val today = PayrollEngine.calculatePeriodTotals(state).todayStats
            views.setTextViewText(R.id.widget_idle, PayrollEngine.formatDurationShort(today?.clockedMs ?: 0L))
            views.setTextViewText(R.id.widget_caption, "worked today")
            views.setTextViewText(R.id.widget_action, "Clock In")
            views.setInt(R.id.widget_action_row, "setBackgroundResource", R.drawable.widget_button_play)
            views.setTextColor(R.id.widget_action, 0xFF4A0A6B.toInt())
            views.setViewVisibility(R.id.widget_action_icon, View.VISIBLE)
            views.setImageViewResource(R.id.widget_action_icon, R.drawable.ic_widget_play)
            views.setInt(R.id.widget_action_icon, "setColorFilter", 0xFF4A0A6B.toInt())
        }

        views.setOnClickPendingIntent(R.id.widget_action_row, actionPendingIntent(context, ACTION_TOGGLE_CLOCK, 1))
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        return views
    }

    internal fun buildStats(context: Context): RemoteViews {
        val state = TimeclockRepository.get(context).state.value
        val totals = PayrollEngine.calculatePeriodTotals(state)
        val hide = state.settings.hideMoneyAmounts
        val views = RemoteViews(context.packageName, R.layout.widget_stats)

        val fmt = java.text.SimpleDateFormat("M/d", java.util.Locale.US)
        views.setTextViewText(
            R.id.widget_period_label,
            "${fmt.format(java.util.Date(totals.startOfPeriod))} - ${fmt.format(java.util.Date(totals.endOfPeriod))}"
        )
        views.setTextViewText(R.id.widget_today_money, PayrollEngine.formatMoney(totals.todayEarnings, hide))
        views.setTextViewText(
            R.id.widget_today_hours,
            "${PayrollEngine.formatDurationShort(totals.todayStats?.clockedMs ?: 0L)} clocked"
        )
        views.setTextViewText(R.id.widget_period_money, PayrollEngine.formatMoney(totals.periodEarnings, hide))
        views.setTextViewText(
            R.id.widget_period_hours,
            "${PayrollEngine.formatDurationShort(totals.totalClockedMsPeriod)} clocked"
        )

        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        return views
    }

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
