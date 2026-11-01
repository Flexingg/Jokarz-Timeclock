package com.randallengineering.jokarztimeclock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Home-screen widget: today's and the current pay period's earnings and clocked hours. Resizable: its layout follows its size. */
class StatsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetUpdater.updateStats(context, appWidgetManager, it) }
    }

    /** The user dragged a resize handle: pick the layout that fits the new size. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        WidgetUpdater.updateStats(context, appWidgetManager, appWidgetId)
    }
}
