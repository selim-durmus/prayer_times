package com.tuttoposto.prayertimes.data.models

/**
 * NORMAL: standard notification on the reminder / prayer-start channels (notification volume).
 * ALARMY: foreground playback with alarm stream — not a separate “content” notification channel.
 */
enum class NotificationStyle {
    NORMAL,
    ALARMY
}

enum class ReminderSound { EZAN, ALARM, NOTIFICATION }

data class FajrWakeUpSettings(
    val enabled: Boolean = false,
    val minutesBeforeSunrise: Int = 15,
    val sound: ReminderSound = ReminderSound.ALARM,
    val style: NotificationStyle = NotificationStyle.ALARMY,
    val notifyAtStart: Boolean = false
) {
    init { require(minutesBeforeSunrise in FajrReminderTiming.storageRange) }
}

data class ReminderAlert(val style: NotificationStyle, val sound: ReminderSound)

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
 * Per-prayer Ezan (adhan) preferences for the "prayer has begun" alert.
 * When true, that prayer's start alert plays the bundled adhan; when false it uses the
 * default notification sound. Applied per-notification at fire time.
 */
data class PrayerEzanPreferences(
    val fajr: Boolean = true,
    val dhuhr: Boolean = true,
    val asr: Boolean = true,
    val maghrib: Boolean = true,
    val isha: Boolean = true
) {
    fun isEzan(prayer: Prayer): Boolean = when (prayer) {
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
        fun fromMap(map: Map<String, Boolean>): PrayerEzanPreferences {
            return PrayerEzanPreferences(
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
 * @param prayerEzanPreferences Per-prayer choice of bundled adhan (res/raw/ezan.*) vs. default notification sound for the prayer-start alert
 * @param debugModeEnabled Hidden debug mode, activated by long-pressing Settings title
 */
data class AppSettings(
    val globalNotificationsEnabled: Boolean = true,
    val prayerNotificationPreferences: PrayerNotificationPreferences = PrayerNotificationPreferences(),
    val reminderOffsetMinutes: Int = 30, // Constrained between 30 and 60
    val notificationStyleEndReminder: NotificationStyle = NotificationStyle.NORMAL,
    val notificationStylePrayerStart: NotificationStyle = NotificationStyle.NORMAL,
    val notifyOnPrayerStart: Boolean = false,
    val prayerEzanPreferences: PrayerEzanPreferences = PrayerEzanPreferences(),
    val debugModeEnabled: Boolean = false, // Hidden by default
    val useAmoledTheme: Boolean = false,
    val fajrWakeUp: FajrWakeUpSettings = FajrWakeUpSettings(),
    val fajrWakeSkip: FajrWakeSkip? = null
) {
    fun reminderOffsetFor(prayer: Prayer): Int =
        if (prayer == Prayer.FAJR && fajrWakeUp.enabled) fajrWakeUp.minutesBeforeSunrise else reminderOffsetMinutes

    fun reminderAlertFor(prayer: Prayer): ReminderAlert =
        if (prayer == Prayer.FAJR && fajrWakeUp.enabled) ReminderAlert(fajrWakeUp.style, fajrWakeUp.sound)
        else ReminderAlert(notificationStyleEndReminder,
            if (notificationStyleEndReminder == NotificationStyle.ALARMY) ReminderSound.ALARM else ReminderSound.NOTIFICATION)

    fun shouldNotifyAtStart(prayer: Prayer): Boolean =
        notifyOnPrayerStart && (prayer != Prayer.FAJR || !fajrWakeUp.enabled || fajrWakeUp.notifyAtStart)

    init {
        require(reminderOffsetMinutes in 30..60) {
            "Reminder offset must be between 30 and 60 minutes"
        }
    }
}

