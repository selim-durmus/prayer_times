package com.tuttoposto.prayertimes.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tuttoposto.prayertimes.data.models.Prayer
import com.tuttoposto.prayertimes.data.repository.SettingsRepository
import com.tuttoposto.prayertimes.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A separate, one-shot alarm lets users lock the phone before testing. Never touches prayer slots. */
object FajrPreviewAlarm {
    const val DELAY_MS = 10_000L
    internal const val REQUEST_CODE = 9976
    private const val TOKEN = "token"
    private const val EXPIRES = "expires"
    private fun prefs(context: Context) = context.getSharedPreferences("fajr_screen_preview", Context.MODE_PRIVATE)
    private fun intent(context: Context) = Intent(context, FajrPreviewReceiver::class.java)

    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        check(manager.canScheduleExactAlarms()) { "Allow exact alarms before testing the lock-screen alarm." }
        val token = java.util.UUID.randomUUID().toString()
        val at = System.currentTimeMillis() + DELAY_MS
        check(prefs(context).edit().putString(TOKEN, token).putLong(EXPIRES, at + 30_000).commit())
        val alarm = PendingIntent.getBroadcast(context, REQUEST_CODE, intent(context).putExtra(TOKEN, token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context, REQUEST_CODE, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try { manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, open), alarm) }
        catch (e: Exception) { cancel(context); throw e }
    }

    fun cancel(context: Context) {
        prefs(context).edit().clear().commit()
        PendingIntent.getBroadcast(context, REQUEST_CODE, intent(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    /** Run on the main thread, after any suspending settings reads, immediately before playback. */
    internal fun consume(context: Context, intent: Intent): Boolean {
        val saved = prefs(context)
        val valid = matches(saved.getString(TOKEN, null), intent.getStringExtra(TOKEN),
            saved.getLong(EXPIRES, 0), System.currentTimeMillis())
        if (valid) saved.edit().clear().commit()
        return valid
    }

    internal fun matches(saved: String?, received: String?, expires: Long, now: Long): Boolean =
        saved != null && saved == received && now <= expires
}

class FajrPreviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val settings = SettingsRepository(context).getSettings()
                if (FajrPreviewAlarm.consume(context, intent)) {
                    val alert = settings.reminderAlertFor(Prayer.FAJR)
                    NotificationHelper.showPrayerNotification(context, Prayer.FAJR.name,
                        settings.reminderOffsetFor(Prayer.FAJR), alert.style, alert.sound,
                        settings.fajrWakeUp.enabled, isPreview = true)
                }
            } catch (e: Exception) { Log.w("FajrPreview", "Unable to deliver preview", e) }
            finally { pending.finish() }
        }
    }
}
