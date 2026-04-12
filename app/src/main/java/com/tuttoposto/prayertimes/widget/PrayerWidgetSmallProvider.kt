package com.tuttoposto.prayertimes.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * 1x1 compact widget showing: prayer name and countdown.
 * Shares the same update alarm chain as the 2x1 widget.
 */
class PrayerWidgetSmallProvider : AppWidgetProvider() {

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
