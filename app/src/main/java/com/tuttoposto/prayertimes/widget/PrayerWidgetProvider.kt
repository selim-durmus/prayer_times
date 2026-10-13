package com.tuttoposto.prayertimes.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** 2x1 widget: countdown, prayer name (or "ends") with its time, and a progress bar. */
class PrayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdateHelper.refreshAsync(this, context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        WidgetUpdateHelper.refreshAsync(this, context)
    }

    override fun onDisabled(context: Context) {
        if (!WidgetUpdateHelper.hasActiveWidgets(context)) WidgetUpdateHelper.cancelScheduledUpdates(context)
    }
}
