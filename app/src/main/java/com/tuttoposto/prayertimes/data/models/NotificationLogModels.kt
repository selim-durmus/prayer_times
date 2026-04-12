package com.tuttoposto.prayertimes.data.models

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Represents a notification-related event for debugging.
 * Tracks when notifications are scheduled, cancelled, fired, etc.
 */
data class NotificationLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: NotificationEventType = NotificationEventType.UNKNOWN,
    val prayerName: String = "",
    val details: String = ""
) {
    fun formatTimestamp(): String {
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )
        return dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }
    
    fun getIcon(): String = when (eventType) {
        NotificationEventType.SCHEDULED -> "📅"
        NotificationEventType.CANCELLED -> "❌"
        NotificationEventType.FIRED -> "🔔"
        NotificationEventType.SKIPPED -> "⏭️"
        NotificationEventType.ERROR -> "⚠️"
        NotificationEventType.UNKNOWN -> "❓"
    }
}

/**
 * Type of notification event.
 */
enum class NotificationEventType {
    SCHEDULED,      // Alarm was set in AlarmManager
    CANCELLED,      // Alarm was cancelled
    FIRED,          // NotificationReceiver received the alarm
    SKIPPED,        // Notification was skipped (past time, disabled, etc.)
    ERROR,          // Error occurred
    UNKNOWN
}

/**
 * Persistent cache of recent notification events.
 */
data class NotificationLogCache(
    val entries: List<NotificationLogEntry> = emptyList()
) {
    companion object {
        const val MAX_LOG_ENTRIES = 50
    }
    
    /**
     * Add a new log entry, keeping only the most recent MAX_LOG_ENTRIES.
     */
    fun addEntry(entry: NotificationLogEntry): NotificationLogCache {
        val newEntries = (listOf(entry) + entries).take(MAX_LOG_ENTRIES)
        return copy(entries = newEntries)
    }
}

