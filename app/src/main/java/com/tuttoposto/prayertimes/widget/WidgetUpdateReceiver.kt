package com.tuttoposto.prayertimes.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Minute tick: redraws every widget and arms the next tick (see [WidgetUpdateHelper]). */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        WidgetUpdateHelper.refreshAsync(this, context)
    }
}
