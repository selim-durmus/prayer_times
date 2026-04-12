package com.tuttoposto.prayertimes.data.models

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Represents the result of a sync attempt (midnight sync or manual sync).
 */
data class SyncLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val source: SyncSource = SyncSource.UNKNOWN,
    val success: Boolean = false,
    val message: String = "",
    val notificationsScheduled: Int = 0
) {
    fun formatTimestamp(): String {
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )
        return dateTime.format(DateTimeFormatter.ofPattern("MMM d, HH:mm:ss"))
    }
}

/**
 * Source of the sync operation.
 */
enum class SyncSource {
    MIDNIGHT_ALARM,     // Triggered by 00:05 alarm
    MANUAL,             // User pressed "Force Sync" button
    APP_OPEN,           // Triggered when app opened and date changed
    BOOT,               // Triggered after device boot
    WORKMANAGER,        // Triggered by WorkManager periodic sync
    UNKNOWN
}

/**
 * Persistent cache of recent sync logs.
 * Stores the last N sync attempts for debugging.
 */
data class SyncLogCache(
    val entries: List<SyncLogEntry> = emptyList(),
    val lastSyncTimestamp: Long? = null,
    val nextMidnightSyncTimestamp: Long? = null
) {
    companion object {
        const val MAX_LOG_ENTRIES = 10
    }
    
    /**
     * Add a new log entry, keeping only the most recent MAX_LOG_ENTRIES.
     */
    fun addEntry(entry: SyncLogEntry): SyncLogCache {
        val newEntries = (listOf(entry) + entries).take(MAX_LOG_ENTRIES)
        return copy(
            entries = newEntries,
            lastSyncTimestamp = if (entry.success) entry.timestamp else lastSyncTimestamp
        )
    }
    
    fun formatLastSync(): String? {
        val timestamp = lastSyncTimestamp ?: return null
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )
        return dateTime.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
    }
    
    fun formatNextMidnightSync(): String? {
        val timestamp = nextMidnightSyncTimestamp ?: return null
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )
        return dateTime.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
    }
}

