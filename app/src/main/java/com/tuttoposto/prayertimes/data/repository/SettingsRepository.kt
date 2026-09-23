package com.tuttoposto.prayertimes.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tuttoposto.prayertimes.data.models.AppSettings
import com.tuttoposto.prayertimes.data.models.FajrWakeUpSettings
import com.tuttoposto.prayertimes.data.models.FajrWakeSkip
import com.tuttoposto.prayertimes.data.models.ReminderSound
import com.tuttoposto.prayertimes.data.models.NotificationStyle
import com.tuttoposto.prayertimes.data.models.PrayerEzanPreferences
import com.tuttoposto.prayertimes.data.models.PrayerNotificationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings"
)

/**
 * Repository for managing app settings.
 * Uses DataStore for persistence.
 */
class SettingsRepository internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.settingsDataStore)

    private fun parseNotificationStyle(raw: String): NotificationStyle? {
        return try {
            NotificationStyle.valueOf(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    
    private object Keys {
        val GLOBAL_NOTIFICATIONS_ENABLED = booleanPreferencesKey("global_notifications_enabled")
        val FAJR_WAKE_ENABLED = booleanPreferencesKey("fajr_wake_enabled")
        val FAJR_WAKE_MINUTES = intPreferencesKey("fajr_wake_minutes")
        val FAJR_WAKE_SOUND = stringPreferencesKey("fajr_wake_sound")
        val FAJR_WAKE_STYLE = stringPreferencesKey("fajr_wake_style")
        val FAJR_WAKE_START = booleanPreferencesKey("fajr_wake_start")
        val FAJR_SKIP_DATE = stringPreferencesKey("fajr_skip_date")
        val FAJR_SKIP_ZONE = stringPreferencesKey("fajr_skip_zone")
        val REMINDER_OFFSET_MINUTES = intPreferencesKey("reminder_offset_minutes")
        /** Legacy single style; used when [NOTIFICATION_STYLE_END] / [NOTIFICATION_STYLE_START] are absent. */
        val NOTIFICATION_STYLE = stringPreferencesKey("notification_style")
        val NOTIFICATION_STYLE_END = stringPreferencesKey("notification_style_end")
        val NOTIFICATION_STYLE_START = stringPreferencesKey("notification_style_start")
        val DEBUG_MODE_ENABLED = booleanPreferencesKey("debug_mode_enabled")
        val USE_AMOLED_THEME = booleanPreferencesKey("use_amoled_theme")
        val NOTIFY_ON_PRAYER_START = booleanPreferencesKey("notify_on_prayer_start")
        /** Legacy global Ezan toggle; used to seed per-prayer defaults when [FAJR_EZAN] etc. are absent. */
        val USE_EZAN_FOR_PRAYER_START = booleanPreferencesKey("use_ezan_for_prayer_start")

        // Per-prayer toggles
        val FAJR_ENABLED = booleanPreferencesKey("fajr_enabled")
        val DHUHR_ENABLED = booleanPreferencesKey("dhuhr_enabled")
        val ASR_ENABLED = booleanPreferencesKey("asr_enabled")
        val MAGHRIB_ENABLED = booleanPreferencesKey("maghrib_enabled")
        val ISHA_ENABLED = booleanPreferencesKey("isha_enabled")

        // Per-prayer Ezan (adhan) toggles for the prayer-start alert
        val FAJR_EZAN = booleanPreferencesKey("fajr_ezan")
        val DHUHR_EZAN = booleanPreferencesKey("dhuhr_ezan")
        val ASR_EZAN = booleanPreferencesKey("asr_ezan")
        val MAGHRIB_EZAN = booleanPreferencesKey("maghrib_ezan")
        val ISHA_EZAN = booleanPreferencesKey("isha_ezan")
    }
    
    /**
     * Flow of current app settings.
     * Emits default values for any missing preferences.
     */
    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        val legacyStyle = prefs[Keys.NOTIFICATION_STYLE]?.let { parseNotificationStyle(it) }
        val endStyle = prefs[Keys.NOTIFICATION_STYLE_END]?.let { parseNotificationStyle(it) }
            ?: legacyStyle
            ?: NotificationStyle.NORMAL
        val startStyle = prefs[Keys.NOTIFICATION_STYLE_START]?.let { parseNotificationStyle(it) }
            ?: legacyStyle
            ?: NotificationStyle.NORMAL
        // Seed per-prayer Ezan defaults from the legacy global toggle so existing users keep their choice.
        val legacyEzan = prefs[Keys.USE_EZAN_FOR_PRAYER_START] ?: true
        AppSettings(
            fajrWakeSkip = FajrWakeSkip.fromStored(prefs[Keys.FAJR_SKIP_DATE], prefs[Keys.FAJR_SKIP_ZONE]),
            fajrWakeUp = FajrWakeUpSettings(
                enabled = prefs[Keys.FAJR_WAKE_ENABLED] ?: false,
                minutesBeforeSunrise = (prefs[Keys.FAJR_WAKE_MINUTES] ?: 15).coerceIn(com.tuttoposto.prayertimes.data.models.FajrReminderTiming.storageRange),
                sound = prefs[Keys.FAJR_WAKE_SOUND]?.let { raw -> ReminderSound.entries.find { it.name == raw } }
                    ?: ReminderSound.ALARM,
                style = prefs[Keys.FAJR_WAKE_STYLE]?.let(::parseNotificationStyle) ?: NotificationStyle.ALARMY,
                notifyAtStart = prefs[Keys.FAJR_WAKE_START] ?: false
            ),
            globalNotificationsEnabled = prefs[Keys.GLOBAL_NOTIFICATIONS_ENABLED] ?: true,
            prayerNotificationPreferences = PrayerNotificationPreferences(
                fajr = prefs[Keys.FAJR_ENABLED] ?: true,
                dhuhr = prefs[Keys.DHUHR_ENABLED] ?: true,
                asr = prefs[Keys.ASR_ENABLED] ?: true,
                maghrib = prefs[Keys.MAGHRIB_ENABLED] ?: true,
                isha = prefs[Keys.ISHA_ENABLED] ?: true
            ),
            reminderOffsetMinutes = (prefs[Keys.REMINDER_OFFSET_MINUTES] ?: 30).coerceIn(30, 60),
            notificationStyleEndReminder = endStyle,
            notificationStylePrayerStart = startStyle,
            debugModeEnabled = prefs[Keys.DEBUG_MODE_ENABLED] ?: false,
            useAmoledTheme = prefs[Keys.USE_AMOLED_THEME] ?: false,
            notifyOnPrayerStart = prefs[Keys.NOTIFY_ON_PRAYER_START] ?: false,
            prayerEzanPreferences = PrayerEzanPreferences(
                fajr = prefs[Keys.FAJR_EZAN] ?: legacyEzan,
                dhuhr = prefs[Keys.DHUHR_EZAN] ?: legacyEzan,
                asr = prefs[Keys.ASR_EZAN] ?: legacyEzan,
                maghrib = prefs[Keys.MAGHRIB_EZAN] ?: legacyEzan,
                isha = prefs[Keys.ISHA_EZAN] ?: legacyEzan
            )
        )
    }
    
    /**
     * Get current settings (non-flow version).
     */
    suspend fun getSettings(): AppSettings {
        return settingsFlow.first()
    }

    suspend fun setFajrWakeEnabled(enabled: Boolean) = dataStore.edit { it[Keys.FAJR_WAKE_ENABLED] = enabled }
    suspend fun setFajrWakeMinutes(minutes: Int) {
        require(minutes in com.tuttoposto.prayertimes.data.models.FajrReminderTiming.storageRange)
        dataStore.edit { it[Keys.FAJR_WAKE_MINUTES] = minutes }
    }
    suspend fun setFajrWakeSound(sound: ReminderSound) = dataStore.edit { it[Keys.FAJR_WAKE_SOUND] = sound.name }
    suspend fun setFajrWakeStyle(style: NotificationStyle) = dataStore.edit { it[Keys.FAJR_WAKE_STYLE] = style.name }
    suspend fun setFajrWakeStart(enabled: Boolean) = dataStore.edit { it[Keys.FAJR_WAKE_START] = enabled }

    suspend fun setFajrWakeSkip(skip: FajrWakeSkip?) = dataStore.edit {
        if (skip == null) {
            it.remove(Keys.FAJR_SKIP_DATE)
            it.remove(Keys.FAJR_SKIP_ZONE)
        } else {
            it[Keys.FAJR_SKIP_DATE] = skip.date.toString()
            it[Keys.FAJR_SKIP_ZONE] = skip.timezoneId
        }
    }
    
    /**
     * Update global notifications enabled state.
     */
    suspend fun setGlobalNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.GLOBAL_NOTIFICATIONS_ENABLED] = enabled
        }
    }
    
    /**
     * Update reminder offset (minutes before prayer end).
     * @param minutes Must be between 30 and 60
     */
    suspend fun setReminderOffset(minutes: Int) {
        require(minutes in 30..60) { "Offset must be between 30 and 60 minutes" }
        dataStore.edit { prefs ->
            prefs[Keys.REMINDER_OFFSET_MINUTES] = minutes
        }
    }
    
    suspend fun setNotificationStyleEndReminder(style: NotificationStyle) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_STYLE_END] = style.name
        }
    }

    suspend fun setNotificationStylePrayerStart(style: NotificationStyle) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_STYLE_START] = style.name
        }
    }
    
    /**
     * Update individual prayer notification toggle.
     */
    suspend fun setPrayerEnabled(prayerName: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            when (prayerName.uppercase()) {
                "FAJR" -> prefs[Keys.FAJR_ENABLED] = enabled
                "DHUHR" -> prefs[Keys.DHUHR_ENABLED] = enabled
                "ASR" -> prefs[Keys.ASR_ENABLED] = enabled
                "MAGHRIB" -> prefs[Keys.MAGHRIB_ENABLED] = enabled
                "ISHA" -> prefs[Keys.ISHA_ENABLED] = enabled
            }
        }
    }
    
    /**
     * Update all prayer notification preferences at once.
     */
    suspend fun setPrayerNotificationPreferences(prefs: PrayerNotificationPreferences) {
        dataStore.edit { dataStorePrefs ->
            dataStorePrefs[Keys.FAJR_ENABLED] = prefs.fajr
            dataStorePrefs[Keys.DHUHR_ENABLED] = prefs.dhuhr
            dataStorePrefs[Keys.ASR_ENABLED] = prefs.asr
            dataStorePrefs[Keys.MAGHRIB_ENABLED] = prefs.maghrib
            dataStorePrefs[Keys.ISHA_ENABLED] = prefs.isha
        }
    }
    
    /**
     * Toggle debug mode (hidden developer section in Settings).
     */
    suspend fun setDebugModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.DEBUG_MODE_ENABLED] = enabled
        }
    }
    
    suspend fun setUseAmoledTheme(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.USE_AMOLED_THEME] = enabled
        }
    }

    suspend fun setNotifyOnPrayerStart(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIFY_ON_PRAYER_START] = enabled
        }
    }

    /**
     * Update the per-prayer Ezan (adhan) toggle for the prayer-start alert.
     */
    suspend fun setPrayerEzanEnabled(prayerName: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            when (prayerName.uppercase()) {
                "FAJR" -> prefs[Keys.FAJR_EZAN] = enabled
                "DHUHR" -> prefs[Keys.DHUHR_EZAN] = enabled
                "ASR" -> prefs[Keys.ASR_EZAN] = enabled
                "MAGHRIB" -> prefs[Keys.MAGHRIB_EZAN] = enabled
                "ISHA" -> prefs[Keys.ISHA_EZAN] = enabled
            }
        }
    }

    /**
     * Reset all settings to defaults.
     */
    suspend fun resetToDefaults() {
        dataStore.edit { it.clear() }
    }
}

