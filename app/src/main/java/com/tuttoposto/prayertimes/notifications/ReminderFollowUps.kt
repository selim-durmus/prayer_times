package com.tuttoposto.prayertimes.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.squareup.moshi.Moshi
import com.tuttoposto.prayertimes.data.models.*
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import com.tuttoposto.prayertimes.data.repository.SettingsRepository
import com.tuttoposto.prayertimes.ui.MainActivity
import kotlinx.coroutines.*
import java.util.UUID

/** All read/modify/schedule operations share a lock, including coordinator and receiver calls. */
object ReminderFollowUps {
    private val lock = Any()
    private val adapter = Moshi.Builder().build().adapter(ReminderFollowUp::class.java)
    internal const val SNOOZE = "com.tuttoposto.prayertimes.FOLLOW_UP_SNOOZE"
    internal const val FIRE = "com.tuttoposto.prayertimes.FOLLOW_UP_FIRE"
    internal const val TOKEN = "follow_up_token"
    internal const val PLAYBACK_TOKEN = "follow_up_playback_token"
    internal const val PRAYER = "follow_up_prayer"

    private fun prefs(context: Context) = context.getSharedPreferences("reminder_follow_ups", Context.MODE_PRIVATE)
    private fun read(context: Context, prayer: Prayer): ReminderFollowUp? = runCatching {
        prefs(context).getString(prayer.name, null)?.let(adapter::fromJson)?.takeIf { it.prayer == prayer }
    }.getOrNull()

    private fun save(context: Context, item: ReminderFollowUp) {
        check(prefs(context).edit().putString(item.prayer.name, adapter.toJson(item)).commit())
    }

    private fun alarmIntent(context: Context, prayer: Prayer, token: String = ""): PendingIntent =
        PendingIntent.getBroadcast(context, 30_000 + prayer.ordinal,
            Intent(context, ReminderFollowUpReceiver::class.java).apply {
                action = FIRE
                putExtra(PRAYER, prayer.name)
                putExtra(TOKEN, token)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun cancel(context: Context, prayer: Prayer) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, prayer))
        check(prefs(context).edit().remove(prayer.name).commit())
    }

    private fun schedule(context: Context, item: ReminderFollowUp, now: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val at = maxOf(requireNotNull(item.atMillis), now + 500)
        val operation = alarmIntent(context, item.prayer, item.token)
        if (alarm.canScheduleExactAlarms()) {
            val open = PendingIntent.getActivity(context, 30_100 + item.prayer.ordinal,
                Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarm.setAlarmClock(AlarmManager.AlarmClockInfo(at, open), operation)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    /** Issued only for real, shared before-end reminders; each displayed alert invalidates old actions. */
    fun offer(context: Context, prayer: Prayer, date: String, zone: String, end: Long,
              style: NotificationStyle, sound: ReminderSound): ReminderFollowUp = synchronized(lock) {
        cancel(context, prayer)
        ReminderFollowUp(UUID.randomUUID().toString(), prayer, date, zone, end, style, sound)
            .also { save(context, it) }
    }

    fun action(context: Context, item: ReminderFollowUp, playbackToken: String? = null): NotificationCompat.Action? {
        val minutes = FollowUpTiming.delayMinutes(System.currentTimeMillis(), item.endMillis) ?: return null
        val intent = Intent(context, ReminderFollowUpReceiver::class.java).apply {
            action = SNOOZE
            data = Uri.parse("prayertimes://follow-up/${item.token}")
            putExtra(PRAYER, item.prayer.name)
            putExtra(TOKEN, item.token)
            putExtra(PLAYBACK_TOKEN, playbackToken)
        }
        return NotificationCompat.Action(0, "Remind in $minutes min", PendingIntent.getBroadcast(context,
            31_000 + item.prayer.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    fun encode(item: ReminderFollowUp): String = adapter.toJson(item)
    fun decode(json: String?): ReminderFollowUp? = runCatching { json?.let(adapter::fromJson) }.getOrNull()

    /** Reboot/refresh/settings changes restore pending reminders without touching regular alarm slots. */
    fun reconcile(context: Context, settings: AppSettings, days: List<PrayerTimesCache>) = synchronized(lock) {
        val now = System.currentTimeMillis()
        for (prayer in Prayer.entries) {
            val saved = read(context, prayer) ?: continue
            val item = saved.withCachedDeadline(days)
            if (!item.allowed(settings) || item.endMillis <= now + 500 ||
                (saved.atMillis != null && item.atMillis == null)) {
                cancel(context, prayer)
            } else {
                if (item != saved) save(context, item)
                if (item.atMillis != null) schedule(context, item, now)
            }
        }
    }

    fun handle(context: Context, intent: Intent, settings: AppSettings, days: List<PrayerTimesCache>): String? = synchronized(lock) {
        val prayer = intent.getStringExtra(PRAYER)?.let(Prayer::fromName) ?: return@synchronized null
        val token = intent.getStringExtra(TOKEN) ?: return@synchronized null
        val saved = read(context, prayer) ?: return@synchronized null
        if (saved.token != token) return@synchronized null
        val item = saved.withCachedDeadline(days)
        val now = System.currentTimeMillis()
        when (intent.action) {
            SNOOZE -> {
                // Repeated taps cannot enqueue another alarm or dismiss a newer alert.
                if (saved.atMillis != null) return@synchronized null
                val pending = item.snoozed(token, now, settings)
                    ?: return@synchronized "No follow-up set: this prayer is ending or its reminders are off."
                save(context, pending)
                try { schedule(context, pending, now) }
                catch (e: Exception) { save(context, saved); throw e }
                if (item.style == NotificationStyle.ALARMY) {
                    // Scheduling already succeeded; do not report failure if Android refuses a
                    // stop request for a service that has since ended. Never stop a newer session.
                    runCatching {
                        intent.getStringExtra(PLAYBACK_TOKEN)?.let { PrayerAlarmPlaybackService.stopSession(context, it) }
                    }.onFailure { Log.w("ReminderFollowUp", "Could not stop previous playback", it) }
                } else {
                    NotificationHelper.cancelNotification(context, NotificationHelper.getNotificationIdForPrayer(prayer.name))
                }
                "Reminder set for ${(pending.atMillis!! - now) / FollowUpTiming.MINUTE} minutes from now."
            }
            FIRE -> {
                if (!item.canDeliver(token, now, settings)) {
                    if (!item.allowed(settings) || now >= item.endMillis || item.atMillis == null) cancel(context, prayer)
                    return@synchronized null
                }
                // Consume even if notification permission was revoked or optional persistence fails.
                cancel(context, prayer)
                // showPrayerNotification replaces the token before posting the follow-up. The next
                // action may repeat the process, but duplicate delivery of this token is ignored.
                NotificationHelper.showPrayerNotification(context, prayer.name,
                    FollowUpTiming.minutesRemaining(now, item.endMillis), item.style, item.sound,
                    prayerEndTimeMillis = item.endMillis, prayerDate = item.prayerDate, prayerZone = item.timezoneId)
                null
            }
            else -> null
        }
    }
}

class ReminderFollowUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderFollowUps.SNOOZE && intent.action != ReminderFollowUps.FIRE) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(context).getSettings()
                val days = PrayerTimesRepository(context).getSchedulingDays()
                val message = ReminderFollowUps.handle(context, intent, settings, days)
                if (message != null) withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("ReminderFollowUp", "Follow-up failed", e)
                if (intent.action == ReminderFollowUps.SNOOZE) withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Could not set the follow-up reminder. Please try again.", Toast.LENGTH_LONG).show()
                }
            } finally { pending.finish() }
        }
    }
}
