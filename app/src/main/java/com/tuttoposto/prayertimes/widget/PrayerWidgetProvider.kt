package com.tuttoposto.prayertimes.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * 2x1 widget showing: prayer name, start time, countdown, and a progress bar.
 * Updates every minute via a chained AlarmManager alarm.
 */
class PrayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetUpdateHelper.updateAllWidgets(context)
        WidgetUpdateHelper.scheduleNextUpdate(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateHelper.scheduleNextUpdate(context)
    }

    override fun onDisabled(context: Context) {
        if (!WidgetUpdateHelper.hasActiveWidgets(context)) {
            WidgetUpdateHelper.cancelScheduledUpdates(context)
        }
    }
}
