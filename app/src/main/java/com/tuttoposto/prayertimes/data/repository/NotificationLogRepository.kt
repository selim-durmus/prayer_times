package com.tuttoposto.prayertimes.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tuttoposto.prayertimes.data.models.NotificationEventType
import com.tuttoposto.prayertimes.data.models.NotificationLogCache
import com.tuttoposto.prayertimes.data.models.NotificationLogEntry
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.notificationLogDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_log"
)

/**
 * Repository for persisting notification event logs.
 * Used for debugging notification scheduling and delivery issues.
 */
class NotificationLogRepository(private val context: Context) {
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val cacheAdapter = moshi.adapter(NotificationLogCache::class.java)
    
    companion object {
        private val NOTIFICATION_LOG_KEY = stringPreferencesKey("notification_log_cache")
    }
    
    val notificationLogFlow: Flow<NotificationLogCache> = context.notificationLogDataStore.data.map { prefs ->
        val json = prefs[NOTIFICATION_LOG_KEY]
        if (json != null) {
            try {
                cacheAdapter.fromJson(json) ?: NotificationLogCache()
            } catch (e: Exception) {
                NotificationLogCache()
            }
        } else {
            NotificationLogCache()
        }
    }
    
    suspend fun saveNotificationLog(cache: NotificationLogCache) {
        context.notificationLogDataStore.edit { prefs ->
            prefs[NOTIFICATION_LOG_KEY] = cacheAdapter.toJson(cache)
        }
    }
    
    suspend fun getNotificationLog(): NotificationLogCache {
        return notificationLogFlow.first()
    }
    
    /**
     * Log a notification event.
     */
    suspend fun logEvent(
        eventType: NotificationEventType,
        prayerName: String,
        details: String
    ) {
        val currentCache = getNotificationLog()
        val entry = NotificationLogEntry(
            timestamp = System.currentTimeMillis(),
            eventType = eventType,
            prayerName = prayerName,
            details = details
        )
        saveNotificationLog(currentCache.addEntry(entry))
    }
    
    /**
     * Synchronous version for use in BroadcastReceivers.
     */
    fun logEventSync(
        eventType: NotificationEventType,
        prayerName: String,
        details: String
    ) {
        runBlocking {
            logEvent(eventType, prayerName, details)
        }
    }
    
    suspend fun clearLog() {
        saveNotificationLog(NotificationLogCache())
    }
}

