package com.tuttoposto.prayertimes.data.model

/**
 * Notification style determines which notification channel to use.
 * 
 * NORMAL: Standard notification with default importance
 * ALARMY: Higher importance notification with stronger sound/vibration for heads-up behavior
 * 
 * Note: ALARMY does NOT create Clock app alarms - it's still a standard notification
 * but with higher priority/importance level.
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
    /**
     * Convert to a Map for easier comparison and serialization.
     */
    fun toMap(): Map<String, Boolean> = mapOf(
        Prayer.FAJR.displayName to fajr,
        Prayer.DHUHR.displayName to dhuhr,
        Prayer.ASR.displayName to asr,
        Prayer.MAGHRIB.displayName to maghrib,
        Prayer.ISHA.displayName to isha
    )
    
    /**
     * Check if a specific prayer is enabled.
     */
    fun isEnabled(prayer: Prayer): Boolean = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }
}

/**
 * Main app settings persisted via DataStore.
 * 
 * @property globalNotificationsEnabled Master toggle for all prayer notifications
 * @property prayerNotificationPreferences Per-prayer toggle settings
 * @property reminderOffsetMinutes Minutes before prayer END to send notification (30-60)
 * @property notificationStyleEndReminder Style for end reminders (NORMAL or ALARMY)
 * @property notificationStylePrayerStart Style for prayer-start alerts (NORMAL or ALARMY)
 */
data class AppSettings(
    val globalNotificationsEnabled: Boolean = true,
    val prayerNotificationPreferences: PrayerNotificationPreferences = PrayerNotificationPreferences(),
    val reminderOffsetMinutes: Int = 30,
    val notificationStyleEndReminder: NotificationStyle = NotificationStyle.NORMAL,
    val notificationStylePrayerStart: NotificationStyle = NotificationStyle.NORMAL
) {
    companion object {
        const val MIN_OFFSET_MINUTES = 30
        const val MAX_OFFSET_MINUTES = 60
    }
}

