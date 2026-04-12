package com.tuttoposto.prayertimes.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tuttoposto.prayertimes.data.models.NotificationScheduleCache
import com.tuttoposto.prayertimes.data.models.Prayer
import com.tuttoposto.prayertimes.data.models.PrayerNotificationEntry
import com.tuttoposto.prayertimes.notifications.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.notificationScheduleDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_schedule_cache"
)

/**
 * Repository for persisting the notification schedule cache.
 * 
 * This cache is used to:
 * 1. Avoid duplicate rescheduling when nothing has changed
 * 2. Track what notifications are currently scheduled
 * 3. Restore schedule information after app restart
 */
class NotificationScheduleCacheRepository(private val context: Context) {
    
    private object Keys {
        val DATE = stringPreferencesKey("schedule_date")
        val TIMEZONE = stringPreferencesKey("schedule_timezone")
        val OFFSET_MINUTES = intPreferencesKey("schedule_offset_minutes")
        val GLOBAL_ENABLED = booleanPreferencesKey("schedule_global_enabled")
        
        // Per-prayer toggles stored in cache
        val FAJR_TOGGLE = booleanPreferencesKey("schedule_fajr_toggle")
        val DHUHR_TOGGLE = booleanPreferencesKey("schedule_dhuhr_toggle")
        val ASR_TOGGLE = booleanPreferencesKey("schedule_asr_toggle")
        val MAGHRIB_TOGGLE = booleanPreferencesKey("schedule_maghrib_toggle")
        val ISHA_TOGGLE = booleanPreferencesKey("schedule_isha_toggle")
        
        // Scheduled notification entries (stored as notification time millis, 0 = not scheduled)
        val FAJR_NOTIFICATION_TIME = longPreferencesKey("schedule_fajr_time")
        val DHUHR_NOTIFICATION_TIME = longPreferencesKey("schedule_dhuhr_time")
        val ASR_NOTIFICATION_TIME = longPreferencesKey("schedule_asr_time")
        val MAGHRIB_NOTIFICATION_TIME = longPreferencesKey("schedule_maghrib_time")
        val ISHA_NOTIFICATION_TIME = longPreferencesKey("schedule_isha_time")

        val FAJR_START_TIME = longPreferencesKey("schedule_fajr_start_time")
        val DHUHR_START_TIME = longPreferencesKey("schedule_dhuhr_start_time")
        val ASR_START_TIME = longPreferencesKey("schedule_asr_start_time")
        val MAGHRIB_START_TIME = longPreferencesKey("schedule_maghrib_start_time")
        val ISHA_START_TIME = longPreferencesKey("schedule_isha_start_time")
    }
    
    /**
     * Flow of current notification schedule cache.
     */
    val scheduleCacheFlow: Flow<NotificationScheduleCache> = context.notificationScheduleDataStore.data.map { prefs ->
        val dateStr = prefs[Keys.DATE]
        if (dateStr == null) {
            return@map NotificationScheduleCache.EMPTY
        }
        
        val prayerToggles = mapOf(
            Prayer.FAJR.name to (prefs[Keys.FAJR_TOGGLE] ?: true),
            Prayer.DHUHR.name to (prefs[Keys.DHUHR_TOGGLE] ?: true),
            Prayer.ASR.name to (prefs[Keys.ASR_TOGGLE] ?: true),
            Prayer.MAGHRIB.name to (prefs[Keys.MAGHRIB_TOGGLE] ?: true),
            Prayer.ISHA.name to (prefs[Keys.ISHA_TOGGLE] ?: true)
        )
        
        val entries = buildList {
            val fajrTime = prefs[Keys.FAJR_NOTIFICATION_TIME] ?: 0L
            if (fajrTime > 0) {
                add(PrayerNotificationEntry(Prayer.FAJR.name, fajrTime, NotificationHelper.NOTIFICATION_ID_FAJR))
            }
            
            val dhuhrTime = prefs[Keys.DHUHR_NOTIFICATION_TIME] ?: 0L
            if (dhuhrTime > 0) {
                add(PrayerNotificationEntry(Prayer.DHUHR.name, dhuhrTime, NotificationHelper.NOTIFICATION_ID_DHUHR))
            }
            
            val asrTime = prefs[Keys.ASR_NOTIFICATION_TIME] ?: 0L
            if (asrTime > 0) {
                add(PrayerNotificationEntry(Prayer.ASR.name, asrTime, NotificationHelper.NOTIFICATION_ID_ASR))
            }
            
            val maghribTime = prefs[Keys.MAGHRIB_NOTIFICATION_TIME] ?: 0L
            if (maghribTime > 0) {
                add(PrayerNotificationEntry(Prayer.MAGHRIB.name, maghribTime, NotificationHelper.NOTIFICATION_ID_MAGHRIB))
            }
            
            val ishaTime = prefs[Keys.ISHA_NOTIFICATION_TIME] ?: 0L
            if (ishaTime > 0) {
                add(PrayerNotificationEntry(Prayer.ISHA.name, ishaTime, NotificationHelper.NOTIFICATION_ID_ISHA))
            }
        }

        val startEntries = buildList {
            val t = prefs[Keys.FAJR_START_TIME] ?: 0L
            if (t > 0) {
                add(
                    PrayerNotificationEntry(
                        Prayer.FAJR.name,
                        t,
                        NotificationHelper.NOTIFICATION_ID_START_FAJR
                    )
                )
            }
            val t2 = prefs[Keys.DHUHR_START_TIME] ?: 0L
            if (t2 > 0) {
                add(
                    PrayerNotificationEntry(
                        Prayer.DHUHR.name,
                        t2,
                        NotificationHelper.NOTIFICATION_ID_START_DHUHR
                    )
                )
            }
            val t3 = prefs[Keys.ASR_START_TIME] ?: 0L
            if (t3 > 0) {
                add(
                    PrayerNotificationEntry(
                        Prayer.ASR.name,
                        t3,
                        NotificationHelper.NOTIFICATION_ID_START_ASR
                    )
                )
            }
            val t4 = prefs[Keys.MAGHRIB_START_TIME] ?: 0L
            if (t4 > 0) {
                add(
                    PrayerNotificationEntry(
                        Prayer.MAGHRIB.name,
                        t4,
                        NotificationHelper.NOTIFICATION_ID_START_MAGHRIB
                    )
                )
            }
            val t5 = prefs[Keys.ISHA_START_TIME] ?: 0L
            if (t5 > 0) {
                add(
                    PrayerNotificationEntry(
                        Prayer.ISHA.name,
                        t5,
                        NotificationHelper.NOTIFICATION_ID_START_ISHA
                    )
                )
            }
        }
        
        NotificationScheduleCache(
            date = LocalDate.parse(dateStr),
            timezoneId = prefs[Keys.TIMEZONE] ?: "",
            reminderOffsetMinutes = prefs[Keys.OFFSET_MINUTES] ?: 30,
            globalNotificationsEnabled = prefs[Keys.GLOBAL_ENABLED] ?: true,
            prayerToggles = prayerToggles,
            entries = entries,
            prayerStartEntries = startEntries
        )
    }
    
    /**
     * Get current schedule cache (non-flow version).
     */
    suspend fun getScheduleCache(): NotificationScheduleCache {
        return scheduleCacheFlow.first()
    }
    
    /**
     * Save updated schedule cache.
     */
    suspend fun saveScheduleCache(cache: NotificationScheduleCache) {
        context.notificationScheduleDataStore.edit { prefs ->
            prefs[Keys.DATE] = cache.date.toString()
            prefs[Keys.TIMEZONE] = cache.timezoneId
            prefs[Keys.OFFSET_MINUTES] = cache.reminderOffsetMinutes
            prefs[Keys.GLOBAL_ENABLED] = cache.globalNotificationsEnabled
            
            // Save toggles
            prefs[Keys.FAJR_TOGGLE] = cache.prayerToggles[Prayer.FAJR.name] ?: true
            prefs[Keys.DHUHR_TOGGLE] = cache.prayerToggles[Prayer.DHUHR.name] ?: true
            prefs[Keys.ASR_TOGGLE] = cache.prayerToggles[Prayer.ASR.name] ?: true
            prefs[Keys.MAGHRIB_TOGGLE] = cache.prayerToggles[Prayer.MAGHRIB.name] ?: true
            prefs[Keys.ISHA_TOGGLE] = cache.prayerToggles[Prayer.ISHA.name] ?: true
            
            // Save notification times (0 means not scheduled)
            val entryMap = cache.entries.associateBy { it.prayerName }
            prefs[Keys.FAJR_NOTIFICATION_TIME] = entryMap[Prayer.FAJR.name]?.notificationTimeMillis ?: 0L
            prefs[Keys.DHUHR_NOTIFICATION_TIME] = entryMap[Prayer.DHUHR.name]?.notificationTimeMillis ?: 0L
            prefs[Keys.ASR_NOTIFICATION_TIME] = entryMap[Prayer.ASR.name]?.notificationTimeMillis ?: 0L
            prefs[Keys.MAGHRIB_NOTIFICATION_TIME] = entryMap[Prayer.MAGHRIB.name]?.notificationTimeMillis ?: 0L
            prefs[Keys.ISHA_NOTIFICATION_TIME] = entryMap[Prayer.ISHA.name]?.notificationTimeMillis ?: 0L

            val startMap = cache.prayerStartEntries.associateBy { it.prayerName }
            prefs[Keys.FAJR_START_TIME] = startMap[Prayer.FAJR.name]?.notificationTimeMillis ?: 0L
            prefs[Keys.DHUHR_START_TIME] = startMap[Prayer.DHUHR.name]?.notificationTimeMillis ?: 0L
            prefs[Keys.ASR_START_TIME] = startMap[Prayer.ASR.name]?.notificationTimeMillis ?: 0L
            prefs[Keys.MAGHRIB_START_TIME] = startMap[Prayer.MAGHRIB.name]?.notificationTimeMillis ?: 0L
            prefs[Keys.ISHA_START_TIME] = startMap[Prayer.ISHA.name]?.notificationTimeMillis ?: 0L
        }
    }
    
    /**
     * Clear the schedule cache.
     */
    suspend fun clearCache() {
        context.notificationScheduleDataStore.edit { it.clear() }
    }
}

