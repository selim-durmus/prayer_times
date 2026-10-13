package com.tuttoposto.prayertimes.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.tuttoposto.prayertimes.ui.MainActivity

/**
 * How prayer alarms are handed to Android.
 *
 * - [Mode.EXACT]: the app has unrestricted battery. Plain exact alarms are then exempt from
 *   App Standby limits, so they are as reliable as clock alarms but don't show the alarm icon or
 *   replace the user's own "next alarm" on the lock screen.
 * - [Mode.CLOCK_ALARM]: battery-optimized. Exact alarms would be capped by App Standby (as little
 *   as one per hour, or one per day for apps not opened in a while), so prayer alarms stay
 *   clock alarms, which are always exempt.
 * - [Mode.INEXACT]: exact-alarm access is off; Android may delay delivery (shown in readiness).
 *
 * Re-scheduling with the same PendingIntent replaces the previous alarm, so a mode change takes
 * effect on the next reschedule (app open, settings change, midnight, boot).
 */
object PrayerAlarmDelivery {
    enum class Mode { EXACT, CLOCK_ALARM, INEXACT }

    fun mode(context: Context): Mode {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return when {
            !alarmManager.canScheduleExactAlarms() -> Mode.INEXACT
            context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName) -> Mode.EXACT
            else -> Mode.CLOCK_ALARM
        }
    }

    /** @param showRequestCode request code for the "open app" intent a clock alarm requires. */
    fun schedule(context: Context, atMillis: Long, operation: PendingIntent, showRequestCode: Int): Mode {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val mode = mode(context)
        try {
            when (mode) {
                Mode.EXACT -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
                Mode.CLOCK_ALARM -> {
                    val show = PendingIntent.getActivity(
                        context, showRequestCode, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), operation)
                }
                Mode.INEXACT -> alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
            }
            return mode
        } catch (e: SecurityException) {
            // Exact-alarm access was revoked between the check and the call.
            Log.w("PrayerAlarmDelivery", "Exact alarm refused; scheduling inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
            return Mode.INEXACT
        }
    }
}
