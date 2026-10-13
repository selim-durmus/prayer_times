package com.tuttoposto.prayertimes.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.tuttoposto.prayertimes.R
import com.tuttoposto.prayertimes.data.models.PrayerCountdown
import com.tuttoposto.prayertimes.data.models.PrayerTimeline
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import com.tuttoposto.prayertimes.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders both widgets and keeps their countdown in step with the clock.
 *
 * The countdown has minute resolution, so the widgets are redrawn on every wall-clock minute
 * boundary with an exact, non-wakeup alarm. Non-wakeup means no battery cost while the screen
 * is off: Android holds the alarm and delivers it the moment the screen turns on, so the widget
 * is current when it is looked at. (The previous inexact 60 s alarm could be deferred by up to
 * ten minutes on Android 12+, which is why the widget lagged until the app was opened.)
 *
 * Every refresh re-arms the chain, so any app activity (opening the app, a sync, a reminder,
 * reboot, clock or time-zone change) also repairs a broken chain.
 */
object WidgetUpdateHelper {

    private const val TAG = "PrayerWidget"
    private const val ACTION_TICK = "com.tuttoposto.prayertimes.WIDGET_UPDATE"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    /** For receivers and providers: refresh off the main thread within the broadcast's lifetime. */
    fun refreshAsync(receiver: BroadcastReceiver, context: Context) {
        val pending = receiver.goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                refresh(app)
            } catch (e: Exception) {
                Log.e(TAG, "Widget refresh failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** Redraw every placed widget from saved prayer times, then arm the next minute tick. */
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        if (!hasActiveWidgets(app)) {
            cancelScheduledUpdates(app)
            return
        }
        try {
            val days = PrayerTimesRepository(app).getSchedulingDays()
            val now = System.currentTimeMillis()
            render(app, PrayerTimeline.countdown(days, now), now)
        } finally {
            // Even if reading or drawing failed, keep ticking so the widget recovers on its own.
            scheduleNextUpdate(app)
        }
    }

    private fun render(context: Context, countdown: PrayerCountdown?, now: Long) {
        val manager = AppWidgetManager.getInstance(context)
        val zone = ZoneId.systemDefault()
        val label = countdown?.let {
            (if (it.isFallback) "≈ " else "") + it.prayerName +
                if (it.phase == PrayerCountdown.Phase.ENDS) " ends" else ""
        }
        val time = countdown?.let {
            Instant.ofEpochMilli(it.targetMillis).atZone(zone).format(timeFormat)
        }
        val remaining = countdown?.let { PrayerTimeline.formatMinutes(it.remainingMillis(now)) }
        val description = countdown?.let {
            if (it.phase == PrayerCountdown.Phase.ENDS) "${it.prayerName} ends in $remaining, at $time"
            else "${it.prayerName} in $remaining, at $time"
        } ?: "Prayer times not available. Open the app."

        for (id in manager.getAppWidgetIds(ComponentName(context, PrayerWidgetProvider::class.java))) {
            val views = RemoteViews(context.packageName, R.layout.widget_prayer_times)
            views.setTextViewText(R.id.widget_prayer_info, label ?: "Prayer times")
            views.setTextViewText(R.id.widget_prayer_time, time ?: "—")
            views.setTextViewText(R.id.widget_countdown, remaining ?: "Open app")
            views.setProgressBar(R.id.widget_progress, 1000,
                countdown?.let { (it.progress(now) * 1000).toInt() } ?: 0, false)
            views.setContentDescription(R.id.widget_root, description)
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, 0))
            manager.updateAppWidget(id, views)
        }
        for (id in manager.getAppWidgetIds(ComponentName(context, PrayerWidgetSmallProvider::class.java))) {
            val views = RemoteViews(context.packageName, R.layout.widget_prayer_times_small)
            views.setTextViewText(R.id.widget_small_prayer_name, label ?: "—")
            views.setTextViewText(R.id.widget_small_countdown, remaining ?: "Open app")
            views.setContentDescription(R.id.widget_small_root, description)
            views.setOnClickPendingIntent(R.id.widget_small_root, openAppIntent(context, 1))
            manager.updateAppWidget(id, views)
        }
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

    // --- Minute tick ---

    private fun scheduleNextUpdate(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = tickIntent(context)
        val at = PrayerTimeline.nextMinuteBoundary(System.currentTimeMillis())
        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExact(AlarmManager.RTC, at, pendingIntent)
                return
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm access was revoked; widget ticks may be delayed", e)
        }
        // Without exact-alarm access Android may batch this; Alarm readiness flags that state.
        alarmManager.setWindow(AlarmManager.RTC, at, 60_000L, pendingIntent)
    }

    fun cancelScheduledUpdates(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))
    }

    fun hasActiveWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.getAppWidgetIds(ComponentName(context, PrayerWidgetProvider::class.java)).isNotEmpty() ||
            manager.getAppWidgetIds(ComponentName(context, PrayerWidgetSmallProvider::class.java)).isNotEmpty()
    }

    private fun tickIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, WidgetUpdateReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
