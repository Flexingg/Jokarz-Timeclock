package com.randallengineering.jokarztimeclock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Home-screen widget: live shift timer plus a one-tap Clock In / Clock Out button. Resizable: its layout follows its size. */
class ClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetUpdater.updateClock(context, appWidgetManager, it) }
    }

    /** The user dragged a resize handle: pick the layout that fits the new size. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        WidgetUpdater.updateClock(context, appWidgetManager, appWidgetId)
    }
}
