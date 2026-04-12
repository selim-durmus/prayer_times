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
import com.tuttoposto.prayertimes.data.models.NotificationStyle
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
class SettingsRepository(private val context: Context) {

    private fun parseNotificationStyle(raw: String): NotificationStyle? {
        return try {
            NotificationStyle.valueOf(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    
    private object Keys {
        val GLOBAL_NOTIFICATIONS_ENABLED = booleanPreferencesKey("global_notifications_enabled")
        val REMINDER_OFFSET_MINUTES = intPreferencesKey("reminder_offset_minutes")
        /** Legacy single style; used when [NOTIFICATION_STYLE_END] / [NOTIFICATION_STYLE_START] are absent. */
        val NOTIFICATION_STYLE = stringPreferencesKey("notification_style")
        val NOTIFICATION_STYLE_END = stringPreferencesKey("notification_style_end")
        val NOTIFICATION_STYLE_START = stringPreferencesKey("notification_style_start")
        val DEBUG_MODE_ENABLED = booleanPreferencesKey("debug_mode_enabled")
        val USE_AMOLED_THEME = booleanPreferencesKey("use_amoled_theme")
        val NOTIFY_ON_PRAYER_START = booleanPreferencesKey("notify_on_prayer_start")
        val USE_EZAN_FOR_PRAYER_START = booleanPreferencesKey("use_ezan_for_prayer_start")

        // Per-prayer toggles
        val FAJR_ENABLED = booleanPreferencesKey("fajr_enabled")
        val DHUHR_ENABLED = booleanPreferencesKey("dhuhr_enabled")
        val ASR_ENABLED = booleanPreferencesKey("asr_enabled")
        val MAGHRIB_ENABLED = booleanPreferencesKey("maghrib_enabled")
        val ISHA_ENABLED = booleanPreferencesKey("isha_enabled")
    }
    
    /**
     * Flow of current app settings.
     * Emits default values for any missing preferences.
     */
    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val legacyStyle = prefs[Keys.NOTIFICATION_STYLE]?.let { parseNotificationStyle(it) }
        val endStyle = prefs[Keys.NOTIFICATION_STYLE_END]?.let { parseNotificationStyle(it) }
            ?: legacyStyle
            ?: NotificationStyle.NORMAL
        val startStyle = prefs[Keys.NOTIFICATION_STYLE_START]?.let { parseNotificationStyle(it) }
            ?: legacyStyle
            ?: NotificationStyle.NORMAL
        AppSettings(
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
            useEzanForPrayerStart = prefs[Keys.USE_EZAN_FOR_PRAYER_START] ?: true
        )
    }
    
    /**
     * Get current settings (non-flow version).
     */
    suspend fun getSettings(): AppSettings {
        return settingsFlow.first()
    }
    
    /**
     * Update global notifications enabled state.
     */
    suspend fun setGlobalNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.GLOBAL_NOTIFICATIONS_ENABLED] = enabled
        }
    }
    
    /**
     * Update reminder offset (minutes before prayer end).
     * @param minutes Must be between 30 and 60
     */
    suspend fun setReminderOffset(minutes: Int) {
        require(minutes in 30..60) { "Offset must be between 30 and 60 minutes" }
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.REMINDER_OFFSET_MINUTES] = minutes
        }
    }
    
    suspend fun setNotificationStyleEndReminder(style: NotificationStyle) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_STYLE_END] = style.name
        }
    }

    suspend fun setNotificationStylePrayerStart(style: NotificationStyle) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_STYLE_START] = style.name
        }
    }
    
    /**
     * Update individual prayer notification toggle.
     */
    suspend fun setPrayerEnabled(prayerName: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
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
        context.settingsDataStore.edit { dataStorePrefs ->
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
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEBUG_MODE_ENABLED] = enabled
        }
    }
    
    suspend fun setUseAmoledTheme(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.USE_AMOLED_THEME] = enabled
        }
    }

    suspend fun setNotifyOnPrayerStart(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.NOTIFY_ON_PRAYER_START] = enabled
        }
    }

    suspend fun setUseEzanForPrayerStart(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.USE_EZAN_FOR_PRAYER_START] = enabled
        }
    }

    /**
     * Reset all settings to defaults.
     */
    suspend fun resetToDefaults() {
        context.settingsDataStore.edit { it.clear() }
    }
}

