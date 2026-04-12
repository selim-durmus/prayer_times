package com.tuttoposto.prayertimes.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tuttoposto.prayertimes.R
import com.tuttoposto.prayertimes.data.models.NotificationStyle
import com.tuttoposto.prayertimes.data.models.Prayer
import com.tuttoposto.prayertimes.ui.MainActivity

/**
 * Helper class for creating notification channels and displaying notifications.
 *
 * Normal style: standard notifications + channel sounds.
 * Alarm-like: foreground [PrayerAlarmPlaybackService] with [Ringtone] on the alarm stream
 * (Clock-like behavior). A silent channel is used only for the foreground playback notification.
 * Separate “alarm” *content* channels are not created: alarm-like never posts to those channels,
 * so they only added clutter in system notification settings.
 *
 * Legacy / retired channel IDs are deleted on startup so they disappear from settings.
 *
 * Reference: https://developer.android.com/develop/ui/views/notifications/channels
 */
object NotificationHelper {

    const val CHANNEL_PRAYER_REMINDERS = "prayer_reminders"
    const val CHANNEL_PRAYER_START = "prayer_has_begun"
    const val CHANNEL_PRAYER_START_ADHAN = "prayer_has_begun_adhan"
    /** Ongoing notification for [PrayerAlarmPlaybackService]; silent (audio is explicit Ringtone). */
    const val CHANNEL_ALARM_PLAYBACK = "alarm_playback_control"

    /** Base name for raw resource: res/raw/ezan.ogg or ezan.mp3 */
    private const val EZAN_RAW_NAME = "ezan"

    /** @deprecated Use [CHANNEL_PRAYER_REMINDERS]; kept for migration delete only */
    private const val LEGACY_CHANNEL_NORMAL = "prayer_reminder_normal"

    /** @deprecated Use [CHANNEL_PRAYER_REMINDERS]; kept for migration delete only */
    private const val LEGACY_CHANNEL_ALARM = "prayer_reminder_alarm"

    /** Retired: alarm-like end reminders use [PrayerAlarmPlaybackService], not this channel. */
    private const val RETIRED_CHANNEL_REMINDERS_ALARM = "prayer_reminders_alarm"

    /** Retired: alarm-like prayer start uses FGS, not this channel. */
    private const val RETIRED_CHANNEL_START_ALARM = "prayer_has_begun_alarm"

    /** Retired: alarm-like + adhan uses FGS with raw URI, not this channel. */
    private const val RETIRED_CHANNEL_START_ADHAN_ALARM = "prayer_has_begun_adhan_alarm"
    
    // Notification IDs
    // Prayer notification IDs: 1000-1004 (one per prayer)
    // Test notification: 9999
    const val NOTIFICATION_ID_FAJR = 1000
    const val NOTIFICATION_ID_DHUHR = 1001
    const val NOTIFICATION_ID_ASR = 1002
    const val NOTIFICATION_ID_MAGHRIB = 1003
    const val NOTIFICATION_ID_ISHA = 1004
    const val NOTIFICATION_ID_TEST = 9999
    /** Debug: prayer-start test so we do not replace the real Maghrib start slot (2013). */
    const val NOTIFICATION_ID_TEST_PRAYER_START = 9988

    const val NOTIFICATION_ID_START_FAJR = 2010
    const val NOTIFICATION_ID_START_DHUHR = 2011
    const val NOTIFICATION_ID_START_ASR = 2012
    const val NOTIFICATION_ID_START_MAGHRIB = 2013
    const val NOTIFICATION_ID_START_ISHA = 2014

    /**
     * Get notification ID for a prayer by name.
     */
    fun getNotificationIdForPrayer(prayerName: String): Int {
        return when (prayerName.uppercase()) {
            "FAJR" -> NOTIFICATION_ID_FAJR
            "DHUHR" -> NOTIFICATION_ID_DHUHR
            "ASR" -> NOTIFICATION_ID_ASR
            "MAGHRIB" -> NOTIFICATION_ID_MAGHRIB
            "ISHA" -> NOTIFICATION_ID_ISHA
            else -> prayerName.hashCode()
        }
    }

    fun getNotificationIdForPrayerStart(prayerName: String): Int {
        return when (prayerName.uppercase()) {
            "FAJR" -> NOTIFICATION_ID_START_FAJR
            "DHUHR" -> NOTIFICATION_ID_START_DHUHR
            "ASR" -> NOTIFICATION_ID_START_ASR
            "MAGHRIB" -> NOTIFICATION_ID_START_MAGHRIB
            "ISHA" -> NOTIFICATION_ID_START_ISHA
            else -> prayerName.hashCode() + 3000
        }
    }

    fun hasBundledEzan(context: Context): Boolean {
        return context.resources.getIdentifier(EZAN_RAW_NAME, "raw", context.packageName) != 0
    }

    private fun ezanSoundUri(context: Context): Uri? {
        val id = context.resources.getIdentifier(EZAN_RAW_NAME, "raw", context.packageName)
        if (id == 0) return null
        return Uri.parse("android.resource://${context.packageName}/$id")
    }

    private fun notificationSoundAttrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Create notification channels.
     * Must be called early in app lifecycle (e.g., in Application.onCreate).
     * 
     * Per Android docs, calling this multiple times is safe - it will not
     * recreate an existing channel or change user-modified settings.
     */
    fun createNotificationChannels(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_NORMAL)
            notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ALARM)
            notificationManager.deleteNotificationChannel(RETIRED_CHANNEL_REMINDERS_ALARM)
            notificationManager.deleteNotificationChannel(RETIRED_CHANNEL_START_ALARM)
            notificationManager.deleteNotificationChannel(RETIRED_CHANNEL_START_ADHAN_ALARM)
        }

        val playbackControl = NotificationChannel(
            CHANNEL_ALARM_PLAYBACK,
            context.getString(R.string.notification_channel_alarm_playback),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_alarm_playback_desc)
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(playbackControl)

        val notifTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val reminders = NotificationChannel(
            CHANNEL_PRAYER_REMINDERS,
            context.getString(R.string.notification_channel_prayer_reminders),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_prayer_reminders_desc)
            enableVibration(true)
            setSound(notifTone, notificationSoundAttrs())
        }
        notificationManager.createNotificationChannel(reminders)

        val startDefault = NotificationChannel(
            CHANNEL_PRAYER_START,
            context.getString(R.string.notification_channel_prayer_start),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_prayer_start_desc)
            enableVibration(true)
            setSound(notifTone, notificationSoundAttrs())
        }
        notificationManager.createNotificationChannel(startDefault)

        val adhanUri = ezanSoundUri(context) ?: notifTone
        val startAdhan = NotificationChannel(
            CHANNEL_PRAYER_START_ADHAN,
            context.getString(R.string.notification_channel_prayer_start_adhan),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_prayer_start_adhan_desc)
            enableVibration(true)
            setSound(adhanUri, notificationSoundAttrs())
        }
        notificationManager.createNotificationChannel(startAdhan)
    }

    /** Alarm-like end reminders do not use a notification channel (foreground playback). */
    fun channelIdForEndReminder(): String = CHANNEL_PRAYER_REMINDERS

    /**
     * Channel for prayer-started [NotificationCompat] (normal style only).
     * Alarm-like uses [PrayerAlarmPlaybackService], not a content channel.
     */
    fun channelIdForPrayerStartNotify(useEzan: Boolean, context: Context): String {
        return if (useEzan && hasBundledEzan(context)) {
            CHANNEL_PRAYER_START_ADHAN
        } else {
            CHANNEL_PRAYER_START
        }
    }
    
    /**
     * Show a prayer reminder notification.
     * 
     * @param context Application context
     * @param prayerName Name of the prayer (e.g., "Fajr")
     * @param minutesRemaining Minutes until prayer ends
     * @param style Notification style (NORMAL or ALARMY)
     */
    fun showPrayerNotification(
        context: Context,
        prayerName: String,
        minutesRemaining: Int,
        style: NotificationStyle
    ) {
        if (!hasNotificationPermission(context)) {
            return
        }

        val displayName = Prayer.fromName(prayerName)?.displayName ?: prayerName

        if (style == NotificationStyle.ALARMY) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            PrayerAlarmPlaybackService.startPlayback(
                context,
                uri,
                displayName,
                context.getString(R.string.notification_reminder_alarm_playback, minutesRemaining)
            )
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = channelIdForEndReminder()

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(displayName)
            .setContentText("$minutesRemaining minutes remaining")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = getNotificationIdForPrayer(prayerName)
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
    
    /**
     * Show a test notification (for debugging).
     */
    fun showTestNotification(context: Context, offsetMinutes: Int, style: NotificationStyle) {
        if (!hasNotificationPermission(context)) {
            return
        }

        if (style == NotificationStyle.ALARMY) {
            PrayerAlarmPlaybackService.startPlayback(
                context,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                "Test Prayer Reminder",
                context.getString(R.string.notification_reminder_test_alarm_playback, offsetMinutes)
            )
            return
        }

        val channelId = channelIdForEndReminder()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Test Prayer Reminder")
            .setContentText("This is a test notification. Current offset: $offsetMinutes minutes before prayer end.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TEST, notification)
    }

    fun showPrayerStartedNotification(
        context: Context,
        prayerName: String,
        style: NotificationStyle,
        useEzan: Boolean,
        notificationIdOverride: Int? = null
    ) {
        if (!hasNotificationPermission(context)) {
            return
        }

        val displayName = Prayer.fromName(prayerName)?.displayName ?: prayerName

        if (style == NotificationStyle.ALARMY) {
            val uri = when {
                useEzan && hasBundledEzan(context) ->
                    ezanSoundUri(context) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
            PrayerAlarmPlaybackService.startPlayback(
                context,
                uri,
                context.getString(R.string.notification_prayer_started_title, displayName),
                ""
            )
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = channelIdForPrayerStartNotify(useEzan, context)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_prayer_started_title, displayName))
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = notificationIdOverride ?: getNotificationIdForPrayerStart(prayerName)
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Cancel a specific notification.
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
    
    /**
     * Cancel all prayer notifications.
     */
    fun cancelAllPrayerNotifications(context: Context) {
        PrayerAlarmPlaybackService.requestStop(context)
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(PrayerAlarmPlaybackService.PLAYBACK_NOTIFICATION_ID)
        manager.cancel(NOTIFICATION_ID_FAJR)
        manager.cancel(NOTIFICATION_ID_DHUHR)
        manager.cancel(NOTIFICATION_ID_ASR)
        manager.cancel(NOTIFICATION_ID_MAGHRIB)
        manager.cancel(NOTIFICATION_ID_ISHA)
        manager.cancel(NOTIFICATION_ID_START_FAJR)
        manager.cancel(NOTIFICATION_ID_START_DHUHR)
        manager.cancel(NOTIFICATION_ID_START_ASR)
        manager.cancel(NOTIFICATION_ID_START_MAGHRIB)
        manager.cancel(NOTIFICATION_ID_START_ISHA)
    }
    
    /**
     * Check if notification permission is granted.
     * On Android 13+, POST_NOTIFICATIONS permission is required.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}

