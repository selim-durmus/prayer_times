package com.tuttoposto.prayertimes.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.tuttoposto.prayertimes.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * DataStore extension for the application preferences.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prayer_times_prefs")

/**
 * Manages persistent storage using Jetpack DataStore.
 * 
 * DataStore is preferred over SharedPreferences because:
 * - It's asynchronous and non-blocking
 * - It handles errors gracefully
 * - It's type-safe with Kotlin coroutines and Flow
 * - It doesn't require blocking the main thread
 * 
 * This class handles:
 * - App settings (notification preferences)
 * - Prayer times cache
 * - Notification schedule cache
 */
class DataStoreManager(private val context: Context) {
    
    private val dataStore = context.dataStore
    
    // ============ KEYS ============
    
    // Settings keys
    private object SettingsKeys {
        val GLOBAL_NOTIFICATIONS_ENABLED = booleanPreferencesKey("global_notifications_enabled")
        val FAJR_ENABLED = booleanPreferencesKey("fajr_enabled")
        val DHUHR_ENABLED = booleanPreferencesKey("dhuhr_enabled")
        val ASR_ENABLED = booleanPreferencesKey("asr_enabled")
        val MAGHRIB_ENABLED = booleanPreferencesKey("maghrib_enabled")
        val ISHA_ENABLED = booleanPreferencesKey("isha_enabled")
        val REMINDER_OFFSET_MINUTES = intPreferencesKey("reminder_offset_minutes")
        val NOTIFICATION_STYLE = stringPreferencesKey("notification_style")
        val NOTIFICATION_STYLE_END = stringPreferencesKey("notification_style_end")
        val NOTIFICATION_STYLE_START = stringPreferencesKey("notification_style_start")
    }
    
    // Prayer times cache keys
    private object PrayerCacheKeys {
        val CACHE_DATE = stringPreferencesKey("prayer_cache_date")
        val CACHE_TIMEZONE = stringPreferencesKey("prayer_cache_timezone")
        val CACHE_LATITUDE = doublePreferencesKey("prayer_cache_latitude")
        val CACHE_LONGITUDE = doublePreferencesKey("prayer_cache_longitude")
        // Prayer times stored as "name|start|end" format, separated by ;;
        val CACHE_PRAYERS = stringPreferencesKey("prayer_cache_prayers")
    }
    
    // Notification schedule cache keys
    private object NotificationCacheKeys {
        val SCHEDULE_DATE = stringPreferencesKey("notification_schedule_date")
        val SCHEDULE_TIMEZONE = stringPreferencesKey("notification_schedule_timezone")
        val SCHEDULE_OFFSET = intPreferencesKey("notification_schedule_offset")
        val SCHEDULE_GLOBAL_ENABLED = booleanPreferencesKey("notification_schedule_global_enabled")
        val SCHEDULE_TOGGLES = stringPreferencesKey("notification_schedule_toggles")
        val SCHEDULE_ENTRIES = stringPreferencesKey("notification_schedule_entries")
    }
    
    // ============ APP SETTINGS ============
    
    /**
     * Flow of current app settings.
     */
    val appSettingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
        val legacy = preferences[SettingsKeys.NOTIFICATION_STYLE]?.let { raw ->
            try {
                NotificationStyle.valueOf(raw)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        val end = preferences[SettingsKeys.NOTIFICATION_STYLE_END]?.let { raw ->
            try {
                NotificationStyle.valueOf(raw)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: legacy ?: NotificationStyle.NORMAL
        val start = preferences[SettingsKeys.NOTIFICATION_STYLE_START]?.let { raw ->
            try {
                NotificationStyle.valueOf(raw)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: legacy ?: NotificationStyle.NORMAL
        AppSettings(
            globalNotificationsEnabled = preferences[SettingsKeys.GLOBAL_NOTIFICATIONS_ENABLED] ?: true,
            prayerNotificationPreferences = PrayerNotificationPreferences(
                fajr = preferences[SettingsKeys.FAJR_ENABLED] ?: true,
                dhuhr = preferences[SettingsKeys.DHUHR_ENABLED] ?: true,
                asr = preferences[SettingsKeys.ASR_ENABLED] ?: true,
                maghrib = preferences[SettingsKeys.MAGHRIB_ENABLED] ?: true,
                isha = preferences[SettingsKeys.ISHA_ENABLED] ?: true
            ),
            reminderOffsetMinutes = preferences[SettingsKeys.REMINDER_OFFSET_MINUTES] ?: 30,
            notificationStyleEndReminder = end,
            notificationStylePrayerStart = start
        )
    }
    
    /**
     * Update global notifications enabled setting.
     */
    suspend fun setGlobalNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.GLOBAL_NOTIFICATIONS_ENABLED] = enabled
        }
    }
    
    /**
     * Update prayer notification preference.
     */
    suspend fun setPrayerEnabled(prayer: Prayer, enabled: Boolean) {
        dataStore.edit { preferences ->
            val key = when (prayer) {
                Prayer.FAJR -> SettingsKeys.FAJR_ENABLED
                Prayer.DHUHR -> SettingsKeys.DHUHR_ENABLED
                Prayer.ASR -> SettingsKeys.ASR_ENABLED
                Prayer.MAGHRIB -> SettingsKeys.MAGHRIB_ENABLED
                Prayer.ISHA -> SettingsKeys.ISHA_ENABLED
            }
            preferences[key] = enabled
        }
    }
    
    /**
     * Update reminder offset minutes.
     */
    suspend fun setReminderOffsetMinutes(minutes: Int) {
        val clampedMinutes = minutes.coerceIn(
            AppSettings.MIN_OFFSET_MINUTES,
            AppSettings.MAX_OFFSET_MINUTES
        )
        dataStore.edit { preferences ->
            preferences[SettingsKeys.REMINDER_OFFSET_MINUTES] = clampedMinutes
        }
    }
    
    suspend fun setNotificationStyleEndReminder(style: NotificationStyle) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.NOTIFICATION_STYLE_END] = style.name
        }
    }

    suspend fun setNotificationStylePrayerStart(style: NotificationStyle) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.NOTIFICATION_STYLE_START] = style.name
        }
    }
    
    // ============ PRAYER TIMES CACHE ============
    
    /**
     * Flow of cached prayer times (nullable if no cache exists).
     */
    val prayerTimesCacheFlow: Flow<PrayerTimesCache?> = dataStore.data.map { preferences ->
        val dateStr = preferences[PrayerCacheKeys.CACHE_DATE] ?: return@map null
        val timezone = preferences[PrayerCacheKeys.CACHE_TIMEZONE] ?: return@map null
        val latitude = preferences[PrayerCacheKeys.CACHE_LATITUDE] ?: return@map null
        val longitude = preferences[PrayerCacheKeys.CACHE_LONGITUDE] ?: return@map null
        val prayersStr = preferences[PrayerCacheKeys.CACHE_PRAYERS] ?: return@map null
        
        val prayers = prayersStr.split(";;").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size == 3) {
                PrayerTime(
                    name = parts[0],
                    startTimeMillis = parts[1].toLongOrNull() ?: return@mapNotNull null,
                    endTimeMillis = parts[2].toLongOrNull() ?: return@mapNotNull null
                )
            } else null
        }
        
        PrayerTimesCache(
            date = LocalDate.parse(dateStr),
            timezoneId = timezone,
            latitude = latitude,
            longitude = longitude,
            prayers = prayers
        )
    }
    
    /**
     * Save prayer times cache.
     */
    suspend fun savePrayerTimesCache(cache: PrayerTimesCache) {
        dataStore.edit { preferences ->
            preferences[PrayerCacheKeys.CACHE_DATE] = cache.date.toString()
            preferences[PrayerCacheKeys.CACHE_TIMEZONE] = cache.timezoneId
            preferences[PrayerCacheKeys.CACHE_LATITUDE] = cache.latitude
            preferences[PrayerCacheKeys.CACHE_LONGITUDE] = cache.longitude
            
            val prayersStr = cache.prayers.joinToString(";;") { prayer ->
                "${prayer.name}|${prayer.startTimeMillis}|${prayer.endTimeMillis}"
            }
            preferences[PrayerCacheKeys.CACHE_PRAYERS] = prayersStr
        }
    }
    
    /**
     * Clear prayer times cache.
     */
    suspend fun clearPrayerTimesCache() {
        dataStore.edit { preferences ->
            preferences.remove(PrayerCacheKeys.CACHE_DATE)
            preferences.remove(PrayerCacheKeys.CACHE_TIMEZONE)
            preferences.remove(PrayerCacheKeys.CACHE_LATITUDE)
            preferences.remove(PrayerCacheKeys.CACHE_LONGITUDE)
            preferences.remove(PrayerCacheKeys.CACHE_PRAYERS)
        }
    }
    
    // ============ NOTIFICATION SCHEDULE CACHE ============
    
    /**
     * Flow of notification schedule cache (nullable if none exists).
     */
    val notificationScheduleCacheFlow: Flow<NotificationScheduleCache?> = dataStore.data.map { preferences ->
        val dateStr = preferences[NotificationCacheKeys.SCHEDULE_DATE] ?: return@map null
        val timezone = preferences[NotificationCacheKeys.SCHEDULE_TIMEZONE] ?: return@map null
        val offset = preferences[NotificationCacheKeys.SCHEDULE_OFFSET] ?: return@map null
        val globalEnabled = preferences[NotificationCacheKeys.SCHEDULE_GLOBAL_ENABLED] ?: return@map null
        val togglesStr = preferences[NotificationCacheKeys.SCHEDULE_TOGGLES] ?: return@map null
        val entriesStr = preferences[NotificationCacheKeys.SCHEDULE_ENTRIES] ?: ""
        
        // Parse toggles: "Fajr=true,Dhuhr=false,..."
        val toggles = togglesStr.split(",").mapNotNull { pair ->
            val parts = pair.split("=")
            if (parts.size == 2) {
                parts[0] to (parts[1].toBooleanStrictOrNull() ?: false)
            } else null
        }.toMap()
        
        // Parse entries: "name|timeMillis|id;;name|timeMillis|id;;..."
        val entries = if (entriesStr.isEmpty()) {
            emptyList()
        } else {
            entriesStr.split(";;").mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size == 3) {
                    PrayerNotificationEntry(
                        prayerName = parts[0],
                        notificationTimeMillis = parts[1].toLongOrNull() ?: return@mapNotNull null,
                        notificationId = parts[2].toIntOrNull() ?: return@mapNotNull null
                    )
                } else null
            }
        }
        
        NotificationScheduleCache(
            date = LocalDate.parse(dateStr),
            timezoneId = timezone,
            reminderOffsetMinutes = offset,
            globalNotificationsEnabled = globalEnabled,
            prayerToggles = toggles,
            entries = entries
        )
    }
    
    /**
     * Save notification schedule cache.
     */
    suspend fun saveNotificationScheduleCache(cache: NotificationScheduleCache) {
        dataStore.edit { preferences ->
            preferences[NotificationCacheKeys.SCHEDULE_DATE] = cache.date.toString()
            preferences[NotificationCacheKeys.SCHEDULE_TIMEZONE] = cache.timezoneId
            preferences[NotificationCacheKeys.SCHEDULE_OFFSET] = cache.reminderOffsetMinutes
            preferences[NotificationCacheKeys.SCHEDULE_GLOBAL_ENABLED] = cache.globalNotificationsEnabled
            
            val togglesStr = cache.prayerToggles.entries.joinToString(",") { (key, value) ->
                "$key=$value"
            }
            preferences[NotificationCacheKeys.SCHEDULE_TOGGLES] = togglesStr
            
            val entriesStr = cache.entries.joinToString(";;") { entry ->
                "${entry.prayerName}|${entry.notificationTimeMillis}|${entry.notificationId}"
            }
            preferences[NotificationCacheKeys.SCHEDULE_ENTRIES] = entriesStr
        }
    }
    
    /**
     * Clear notification schedule cache.
     */
    suspend fun clearNotificationScheduleCache() {
        dataStore.edit { preferences ->
            preferences.remove(NotificationCacheKeys.SCHEDULE_DATE)
            preferences.remove(NotificationCacheKeys.SCHEDULE_TIMEZONE)
            preferences.remove(NotificationCacheKeys.SCHEDULE_OFFSET)
            preferences.remove(NotificationCacheKeys.SCHEDULE_GLOBAL_ENABLED)
            preferences.remove(NotificationCacheKeys.SCHEDULE_TOGGLES)
            preferences.remove(NotificationCacheKeys.SCHEDULE_ENTRIES)
        }
    }
}

