package com.tuttoposto.prayertimes.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** 1x1 widget: prayer name and countdown. Shares the minute tick with the 2x1 widget. */
class PrayerWidgetSmallProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdateHelper.refreshAsync(this, context)
    }

    override fun onDisabled(context: Context) {
        if (!WidgetUpdateHelper.hasActiveWidgets(context)) WidgetUpdateHelper.cancelScheduledUpdates(context)
    }
}
