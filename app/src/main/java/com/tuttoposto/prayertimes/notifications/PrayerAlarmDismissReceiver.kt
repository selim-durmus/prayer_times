package com.tuttoposto.prayertimes.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles notification swipe-dismiss: [PendingIntent] delete intents are not always delivered
 * cleanly to a running [Service]; a short broadcast reliably stops [PrayerAlarmPlaybackService].
 */
class PrayerAlarmDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        PrayerAlarmPlaybackService.requestStop(context.applicationContext)
    }
}
