package com.tuttoposto.prayertimes.data.models

/**
 * NORMAL: standard notification on the reminder / prayer-start channels (notification volume).
 * ALARMY: foreground playback with alarm stream — not a separate “content” notification channel.
 */
enum class NotificationStyle {
    NORMAL,
    ALARMY
}

/**
 * Per-prayer notification toggle preferences.
 * Each prayer can be individually enabled/disabled for notifications.
 */
data class PrayerNotificationPreferences(
    val fajr: Boolean = true,
    val dhuhr: Boolean = true,
    val asr: Boolean = true,
    val maghrib: Boolean = true,
    val isha: Boolean = true
) {
    fun isEnabled(prayer: Prayer): Boolean = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }
    
    fun toMap(): Map<String, Boolean> = mapOf(
        Prayer.FAJR.name to fajr,
        Prayer.DHUHR.name to dhuhr,
        Prayer.ASR.name to asr,
        Prayer.MAGHRIB.name to maghrib,
        Prayer.ISHA.name to isha
    )
    
    companion object {
        fun fromMap(map: Map<String, Boolean>): PrayerNotificationPreferences {
            return PrayerNotificationPreferences(
                fajr = map[Prayer.FAJR.name] ?: true,
                dhuhr = map[Prayer.DHUHR.name] ?: true,
                asr = map[Prayer.ASR.name] ?: true,
                maghrib = map[Prayer.MAGHRIB.name] ?: true,
                isha = map[Prayer.ISHA.name] ?: true
            )
        }
    }
}

/**
 * Main app settings data class.
 * Controls all notification-related behavior.
 * 
 * @param globalNotificationsEnabled Master toggle for all notifications
 * @param prayerNotificationPreferences Per-prayer toggles
 * @param reminderOffsetMinutes Minutes before prayer END to send notification (30-60)
 * @param notificationStyleEndReminder Normal or Alarm-like for “before prayer ends” alerts
 * @param notificationStylePrayerStart Normal or Alarm-like for “prayer has begun” alerts
 * @param notifyOnPrayerStart Alert when each enabled prayer's time begins (same per-prayer toggles)
 * @param useEzanForPrayerStart Use bundled adhan in res/raw/ezan.* when true; otherwise default notification sound
 * @param debugModeEnabled Hidden debug mode, activated by long-pressing Settings title
 */
data class AppSettings(
    val globalNotificationsEnabled: Boolean = true,
    val prayerNotificationPreferences: PrayerNotificationPreferences = PrayerNotificationPreferences(),
    val reminderOffsetMinutes: Int = 30, // Constrained between 30 and 60
    val notificationStyleEndReminder: NotificationStyle = NotificationStyle.NORMAL,
    val notificationStylePrayerStart: NotificationStyle = NotificationStyle.NORMAL,
    val notifyOnPrayerStart: Boolean = false,
    val useEzanForPrayerStart: Boolean = true,
    val debugModeEnabled: Boolean = false, // Hidden by default
    val useAmoledTheme: Boolean = false
) {
    init {
        require(reminderOffsetMinutes in 30..60) {
            "Reminder offset must be between 30 and 60 minutes"
        }
    }
}

