package com.tuttoposto.prayertimes.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the periodic alarm to refresh widget content every minute.
 * Updates all active widget instances and chains the next alarm.
 */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        WidgetUpdateHelper.updateAllWidgets(context)
        if (WidgetUpdateHelper.hasActiveWidgets(context)) {
            WidgetUpdateHelper.scheduleNextUpdate(context)
        }
    }
}
