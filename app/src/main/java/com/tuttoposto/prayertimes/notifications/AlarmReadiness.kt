package com.tuttoposto.prayertimes.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.PowerManager
import com.tuttoposto.prayertimes.data.models.AppSettings
import com.tuttoposto.prayertimes.data.models.NotificationStyle
import com.tuttoposto.prayertimes.data.models.Prayer
import com.tuttoposto.prayertimes.data.models.ReminderSound

enum class ReadinessAction { NOTIFICATIONS, EXACT_ALARMS, FULL_SCREEN, CHANNEL, SOUND, MODES, APP_SETTINGS }

data class ReadinessCheck(
    val label: String,
    val detail: String,
    val needsAttention: Boolean = false,
    val action: ReadinessAction? = null,
    val channelId: String? = null
)

data class AlertChannel(val id: String, val usesAlarmVolume: Boolean)

object AlarmReadiness {
    /** Only inspect channels actually used by enabled prayers, never retired/unused ones. */
    fun activeChannels(settings: AppSettings, hasEzan: Boolean): Set<AlertChannel> {
        if (!settings.globalNotificationsEnabled) return emptySet()
        return buildSet {
            for (prayer in Prayer.entries.filter { settings.prayerNotificationPreferences.isEnabled(it) }) {
                val alert = settings.reminderAlertFor(prayer)
                add(AlertChannel(NotificationHelper.channelIdForReminder(
                    prayer == Prayer.FAJR && settings.fajrWakeUp.enabled, alert.sound, alert.style
                ), alert.style == NotificationStyle.ALARMY))
                if (settings.shouldNotifyAtStart(prayer)) {
                    val alarm = settings.notificationStylePrayerStart == NotificationStyle.ALARMY
                    add(AlertChannel(when {
                        alarm -> NotificationHelper.CHANNEL_ALARM_PLAYBACK
                        hasEzan && settings.prayerEzanPreferences.isEzan(prayer) -> NotificationHelper.CHANNEL_PRAYER_START_ADHAN
                        else -> NotificationHelper.CHANNEL_PRAYER_START
                    }, alarm))
                }
            }
        }
    }

    // The playback-control channel is intentionally silent: its service plays the alarm audio.
    fun channelNeedsAttention(channel: AlertChannel, importance: Int?, hasSound: Boolean, groupBlocked: Boolean,
                              fullScreen: Boolean = false): Boolean =
        importance == null || importance == 0 || groupBlocked ||
            (fullScreen && importance < 4) ||
            (!channel.usesAlarmVolume && (importance < 3 || !hasSound))

    fun read(context: Context, settings: AppSettings, hasPrayerData: Boolean): List<ReadinessCheck> {
        val manager = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        val channels = activeChannels(settings, NotificationHelper.hasBundledEzan(context))
        val enabled = settings.globalNotificationsEnabled && channels.isNotEmpty()
        val fajrScreen = FajrAlarmPresentation.usesAlarmScreen(Prayer.FAJR, settings.fajrWakeUp.enabled, settings.fajrWakeUp.style)
        return buildList {
            add(ReadinessCheck("Prayer alerts", if (enabled) "Enabled" else "Paused in app settings", !enabled))
            val notifications = NotificationHelper.hasNotificationPermission(context) && manager.areNotificationsEnabled()
            add(ReadinessCheck("Notifications", if (notifications) "Allowed" else "Blocked by Android", !notifications,
                ReadinessAction.NOTIFICATIONS))
            val exact = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            add(ReadinessCheck("Exact alarms", if (exact) "Allowed" else "Allow precise reminder times", !exact,
                ReadinessAction.EXACT_ALARMS))
            if (fajrScreen) {
                val allowed = manager.canUseFullScreenIntent()
                add(ReadinessCheck("Fajr full-screen alarms",
                    if (allowed) "Allowed on the lock screen" else "Allow full-screen access; audio still works without it",
                    !allowed, ReadinessAction.FULL_SCREEN))
            }
            add(ReadinessCheck("Saved prayer times", if (hasPrayerData) "Available" else "Open Prayer Times and refresh", !hasPrayerData))
            for (route in channels) {
                val channel = manager.getNotificationChannel(route.id)
                val blockedGroup = channel?.group?.let { manager.getNotificationChannelGroup(it)?.isBlocked } == true
                val sound = channel?.sound
                val hasSound = sound != null && (!RingtoneManager.isDefault(sound) ||
                    RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.getDefaultType(sound)) != null)
                val fullScreen = fajrScreen && route.id == NotificationHelper.CHANNEL_ALARM_PLAYBACK
                val needsAttention = channelNeedsAttention(route, channel?.importance, hasSound, blockedGroup, fullScreen)
                add(ReadinessCheck(channel?.name?.toString() ?: "Notification channel",
                    when {
                        channel == null -> "Unavailable — reopen the app"
                        channel.importance == NotificationManager.IMPORTANCE_NONE || blockedGroup -> "Blocked"
                        fullScreen && channel.importance < NotificationManager.IMPORTANCE_HIGH -> "Allow pop-up alerts for the Fajr alarm screen"
                        needsAttention -> "Silent — review channel sound settings"
                        route.usesAlarmVolume -> "Playback controls allowed; sound uses alarm volume"
                        else -> "Sound enabled"
                    }, needsAttention, ReadinessAction.CHANNEL, route.id))
            }
            // Alarm-style playback uses default ringtone URIs, which may themselves be set to None.
            val defaultSounds = buildSet {
                if (enabled) for (prayer in Prayer.entries.filter { settings.prayerNotificationPreferences.isEnabled(it) }) {
                    val reminder = settings.reminderAlertFor(prayer)
                    if (reminder.style == NotificationStyle.ALARMY) add(reminder.sound)
                    if (settings.shouldNotifyAtStart(prayer) && settings.notificationStylePrayerStart == NotificationStyle.ALARMY) {
                        add(if (settings.prayerEzanPreferences.isEzan(prayer)) ReminderSound.EZAN else ReminderSound.ALARM)
                    }
                }
            }
            for (sound in defaultSounds) {
                if (sound == ReminderSound.EZAN && NotificationHelper.hasBundledEzan(context)) continue
                val type = if (sound == ReminderSound.NOTIFICATION) RingtoneManager.TYPE_NOTIFICATION else RingtoneManager.TYPE_ALARM
                if (RingtoneManager.getActualDefaultRingtoneUri(context, type) == null) {
                    add(ReadinessCheck(if (type == RingtoneManager.TYPE_ALARM) "Default alarm sound" else "Default notification sound",
                        "Set to None — choose a sound", true, ReadinessAction.SOUND))
                }
            }
            if (channels.any { it.usesAlarmVolume }) {
                val volume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
                add(ReadinessCheck("Alarm volume", "${volume * 100 / max}%", volume == 0, ReadinessAction.SOUND))
            }
            if (channels.any { !it.usesAlarmVolume }) {
                val volume = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION).coerceAtLeast(1)
                val muted = volume == 0 || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
                add(ReadinessCheck("Notification volume", if (muted) "Silent / vibrate" else "${volume * 100 / max}%",
                    muted, ReadinessAction.SOUND))
            }
            val filter = manager.currentInterruptionFilter
            val modes = filter != NotificationManager.INTERRUPTION_FILTER_ALL
            add(ReadinessCheck("Do Not Disturb / Modes", when (filter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> "Not restricting alerts now"
                NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> "Unable to check — review exceptions"
                else -> "Active — check which alarms and notifications are allowed"
            }, enabled && modes, ReadinessAction.MODES))
            val unrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
            add(ReadinessCheck("Battery", if (unrestricted) "Optimization exemption enabled" else "Optimized — review if background refresh is delayed",
                action = ReadinessAction.APP_SETTINGS))
        }
    }
}
