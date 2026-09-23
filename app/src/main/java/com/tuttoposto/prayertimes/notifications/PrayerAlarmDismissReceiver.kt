package com.tuttoposto.prayertimes.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles notification swipe-dismiss: [PendingIntent] delete intents are not always delivered
 * cleanly to a running [Service]; a short broadcast reliably stops [PrayerAlarmPlaybackService].
 */
class PrayerAlarmDismissReceiver : BroadcastReceiver() {
    companion object { const val EXTRA_PREVIEW = "dismiss_preview" }

    override fun onReceive(context: Context, intent: Intent?) {
        val token = intent?.getStringExtra(com.tuttoposto.prayertimes.ui.FajrAlarmActivity.EXTRA_TOKEN)
        if (token != null) PrayerAlarmPlaybackService.stopSession(context.applicationContext, token)
        else if (intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true)
            PrayerAlarmPlaybackService.stopPreview(context.applicationContext)
        else PrayerAlarmPlaybackService.requestStop(context.applicationContext)
    }
}
