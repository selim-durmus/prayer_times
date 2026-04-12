package com.tuttoposto.prayertimes.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tuttoposto.prayertimes.data.models.SyncLogCache
import com.tuttoposto.prayertimes.data.models.SyncLogEntry
import com.tuttoposto.prayertimes.data.models.SyncSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncLogDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_log"
)

/**
 * Repository for managing sync log entries.
 * Persists sync attempt history for debugging purposes.
 */
class SyncLogRepository(private val context: Context) {
    
    private object Keys {
        val ENTRIES_JSON = stringPreferencesKey("entries_json")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val NEXT_MIDNIGHT_SYNC = longPreferencesKey("next_midnight_sync")
    }
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val entriesListType = Types.newParameterizedType(
        List::class.java,
        SyncLogEntry::class.java
    )
    private val entriesAdapter = moshi.adapter<List<SyncLogEntry>>(entriesListType)
    
    /**
     * Flow of current sync log cache.
     */
    val syncLogFlow: Flow<SyncLogCache> = context.syncLogDataStore.data.map { prefs ->
        val entriesJson = prefs[Keys.ENTRIES_JSON]
        val entries = if (entriesJson != null) {
            try {
                entriesAdapter.fromJson(entriesJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        
        SyncLogCache(
            entries = entries,
            lastSyncTimestamp = prefs[Keys.LAST_SYNC_TIMESTAMP],
            nextMidnightSyncTimestamp = prefs[Keys.NEXT_MIDNIGHT_SYNC]
        )
    }
    
    /**
     * Get current sync log cache (non-flow version).
     */
    suspend fun getSyncLogCache(): SyncLogCache {
        return syncLogFlow.first()
    }
    
    /**
     * Log a sync attempt.
     */
    suspend fun logSyncAttempt(
        source: SyncSource,
        success: Boolean,
        message: String,
        notificationsScheduled: Int = 0
    ) {
        val entry = SyncLogEntry(
            timestamp = System.currentTimeMillis(),
            source = source,
            success = success,
            message = message,
            notificationsScheduled = notificationsScheduled
        )
        
        context.syncLogDataStore.edit { prefs ->
            val currentCache = getSyncLogCache()
            val newCache = currentCache.addEntry(entry)
            
            prefs[Keys.ENTRIES_JSON] = entriesAdapter.toJson(newCache.entries)
            
            if (success) {
                prefs[Keys.LAST_SYNC_TIMESTAMP] = entry.timestamp
            }
        }
    }
    
    /**
     * Update the next scheduled midnight sync time.
     */
    suspend fun setNextMidnightSync(timestamp: Long) {
        context.syncLogDataStore.edit { prefs ->
            prefs[Keys.NEXT_MIDNIGHT_SYNC] = timestamp
        }
    }
    
    /**
     * Clear all sync logs.
     */
    suspend fun clearLogs() {
        context.syncLogDataStore.edit { it.clear() }
    }
}

