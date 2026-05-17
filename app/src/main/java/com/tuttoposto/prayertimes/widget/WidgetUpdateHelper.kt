package com.tuttoposto.prayertimes.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.tuttoposto.prayertimes.R
import com.tuttoposto.prayertimes.data.models.PrayerTime
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import com.tuttoposto.prayertimes.ui.MainActivity
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object WidgetUpdateHelper {

    private const val UPDATE_INTERVAL_MS = 60_000L

    data class NextPrayerInfo(
        val prayerName: String,
        val prayerTimeFormatted: String,
        val countdownText: String,
        val progressPercent: Int,
        val isTomorrow: Boolean
    )

    fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val info = getNextPrayerInfo(appContext)

        val ids2x1 = manager.getAppWidgetIds(
            ComponentName(appContext, PrayerWidgetProvider::class.java)
        )
        for (id in ids2x1) {
            updateWidget2x1(appContext, manager, id, info)
        }

        val ids1x1 = manager.getAppWidgetIds(
            ComponentName(appContext, PrayerWidgetSmallProvider::class.java)
        )
        for (id in ids1x1) {
            updateWidget1x1(appContext, manager, id, info)
        }
    }

    private fun updateWidget2x1(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        info: NextPrayerInfo?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_prayer_times)

        if (info != null) {
            val name = if (info.isTomorrow) "${info.prayerName} ᵗᵐʳʷ" else info.prayerName
            views.setTextViewText(R.id.widget_prayer_info, name)
            views.setTextViewText(R.id.widget_prayer_time, info.prayerTimeFormatted)
            views.setTextViewText(R.id.widget_countdown, info.countdownText)
            views.setProgressBar(R.id.widget_progress, 100, info.progressPercent, false)
        } else {
            views.setTextViewText(R.id.widget_prayer_info, "Prayer")
            views.setTextViewText(R.id.widget_prayer_time, "—")
            views.setTextViewText(R.id.widget_countdown, "Open app")
            views.setProgressBar(R.id.widget_progress, 100, 0, false)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        manager.updateAppWidget(widgetId, views)
    }

    private fun updateWidget1x1(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        info: NextPrayerInfo?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_prayer_times_small)

        if (info != null) {
            val name = if (info.isTomorrow) "${info.prayerName} ᵗᵐʳʷ" else info.prayerName
            views.setTextViewText(R.id.widget_small_prayer_name, name)
            views.setTextViewText(R.id.widget_small_countdown, info.countdownText)
        } else {
            views.setTextViewText(R.id.widget_small_prayer_name, "—")
            views.setTextViewText(R.id.widget_small_countdown, "Open app")
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_small_root, pendingIntent)

        manager.updateAppWidget(widgetId, views)
    }

    fun getNextPrayerInfo(context: Context): NextPrayerInfo? {
        val repository = PrayerTimesRepository(context.applicationContext)
        val cache = runBlocking { repository.getCachedPrayerTimes() } ?: return null

        val now = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val prayers = cache.prayers
        if (prayers.isEmpty()) return null

        // If cache is from a different day, shift times to approximate today
        val daysDiff = ChronoUnit.DAYS.between(cache.date, today)
        val effectivePrayers = if (daysDiff != 0L) {
            val shiftMs = daysDiff * 24 * 3600 * 1000L
            prayers.map {
                PrayerTime(it.name, it.startTimeMillis + shiftMs, it.endTimeMillis + shiftMs)
            }
        } else {
            prayers
        }

        // If we're inside a prayer window, count down to that prayer's end (e.g. Fajr → sunrise),
        // not to the next prayer's start. Otherwise during Fajr we'd incorrectly show Dhuhr's start.
        for (prayer in effectivePrayers) {
            if (now >= prayer.startTimeMillis && now < prayer.endTimeMillis) {
                val timeUntilMs = prayer.endTimeMillis - now
                val windowMs = prayer.endTimeMillis - prayer.startTimeMillis
                val progress = if (windowMs > 0) {
                    ((now - prayer.startTimeMillis).toFloat() / windowMs * 100).toInt().coerceIn(0, 100)
                } else {
                    100
                }
                return NextPrayerInfo(
                    prayerName = prayer.name,
                    prayerTimeFormatted = formatTime(prayer.endTimeMillis, zoneId),
                    countdownText = formatCountdown(timeUntilMs),
                    progressPercent = progress,
                    isTomorrow = false
                )
            }
        }

        // Find the next prayer whose start time is still in the future
        for (i in effectivePrayers.indices) {
            if (effectivePrayers[i].startTimeMillis > now) {
                val next = effectivePrayers[i]
                val windowStart = if (i > 0) {
                    effectivePrayers[i - 1].startTimeMillis
                } else {
                    today.atStartOfDay(zoneId).toInstant().toEpochMilli()
                }

                val timeUntilMs = next.startTimeMillis - now
                val windowMs = next.startTimeMillis - windowStart
                val progress = if (windowMs > 0) {
                    ((now - windowStart).toFloat() / windowMs * 100).toInt().coerceIn(0, 100)
                } else 0

                return NextPrayerInfo(
                    prayerName = next.name,
                    prayerTimeFormatted = formatTime(next.startTimeMillis, zoneId),
                    countdownText = formatCountdown(timeUntilMs),
                    progressPercent = progress,
                    isTomorrow = false
                )
            }
        }

        // All prayers are past — show approximate next Fajr
        val fajr = effectivePrayers.first()
        val nextFajrApprox = fajr.startTimeMillis + 24 * 3600 * 1000L
        val timeUntilMs = nextFajrApprox - now
        if (timeUntilMs <= 0) return null

        val lastEnd = effectivePrayers.last().endTimeMillis
        val windowMs = nextFajrApprox - lastEnd
        val progress = if (windowMs > 0) {
            ((now - lastEnd).toFloat() / windowMs * 100).toInt().coerceIn(0, 100)
        } else 0

        val fajrDay = Instant.ofEpochMilli(nextFajrApprox).atZone(zoneId).toLocalDate()

        return NextPrayerInfo(
            prayerName = fajr.name,
            prayerTimeFormatted = formatTime(nextFajrApprox, zoneId),
            countdownText = formatCountdown(timeUntilMs),
            progressPercent = progress,
            isTomorrow = fajrDay.isAfter(today)
        )
    }

    private fun formatTime(millis: Long, zoneId: ZoneId): String {
        return Instant.ofEpochMilli(millis)
            .atZone(zoneId)
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    private fun formatCountdown(millis: Long): String {
        if (millis <= 0) return "now"
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    // --- Periodic update scheduling ---

    fun scheduleNextUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = getUpdatePendingIntent(context)
        val triggerAt = SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS
        alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, pendingIntent)
    }

    fun cancelScheduledUpdates(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getUpdatePendingIntent(context))
    }

    fun hasActiveWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val ids2x1 = manager.getAppWidgetIds(
            ComponentName(context, PrayerWidgetProvider::class.java)
        )
        val ids1x1 = manager.getAppWidgetIds(
            ComponentName(context, PrayerWidgetSmallProvider::class.java)
        )
        return ids2x1.isNotEmpty() || ids1x1.isNotEmpty()
    }

    private fun getUpdatePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = "com.tuttoposto.prayertimes.WIDGET_UPDATE"
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
