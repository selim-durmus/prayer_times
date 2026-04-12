package com.tuttoposto.prayertimes.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tuttoposto.prayertimes.data.models.NotificationEventType
import com.tuttoposto.prayertimes.data.models.Prayer
import com.tuttoposto.prayertimes.data.repository.NotificationLogRepository
import com.tuttoposto.prayertimes.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that handles scheduled notification alarms.
 * 
 * When an alarm fires, this receiver:
 * 1. Logs the event for debugging
 * 2. Reads current settings to get notification style
 * 3. Shows the appropriate notification
 */
class NotificationReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NotificationReceiver"
        private const val ACTION_PRAYER_NOTIFICATION = "com.tuttoposto.prayertimes.PRAYER_NOTIFICATION"
        private const val ACTION_TEST_NOTIFICATION = "com.tuttoposto.prayertimes.TEST_NOTIFICATION"
        const val ACTION_TEST_PRAYER_START = "com.tuttoposto.prayertimes.TEST_PRAYER_START"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "🔔 Received broadcast: ${intent.action}")
        
        when (intent.action) {
            ACTION_PRAYER_NOTIFICATION -> handlePrayerNotification(context, intent)
            NotificationScheduler.ACTION_PRAYER_START -> handlePrayerStarted(context, intent)
            ACTION_TEST_NOTIFICATION -> handleTestNotification(context)
            ACTION_TEST_PRAYER_START -> handleTestPrayerStartNotification(context)
            Intent.ACTION_BOOT_COMPLETED -> handleBootCompleted(context)
            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }

    private fun handlePrayerStarted(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(NotificationScheduler.EXTRA_PRAYER_NAME) ?: run {
            Log.e(TAG, "❌ Prayer start received but no prayer name!")
            return
        }

        Log.d(TAG, "🔔 Prayer START fired: $prayerName")

        val pendingResult = goAsync()
        val notificationLogRepository = NotificationLogRepository(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationLogRepository.logEvent(
                    NotificationEventType.FIRED,
                    prayerName,
                    "Prayer start alarm fired"
                )

                val settingsRepository = SettingsRepository(context)
                val settings = settingsRepository.getSettings()

                val prayer = Prayer.fromName(prayerName)
                if (!settings.globalNotificationsEnabled || !settings.notifyOnPrayerStart ||
                    prayer == null || !settings.prayerNotificationPreferences.isEnabled(prayer)
                ) {
                    notificationLogRepository.logEvent(
                        NotificationEventType.SKIPPED,
                        prayerName,
                        "Start skipped (settings off or prayer disabled)"
                    )
                } else {
                    NotificationHelper.showPrayerStartedNotification(
                        context = context,
                        prayerName = prayerName,
                        style = settings.notificationStylePrayerStart,
                        useEzan = settings.useEzanForPrayerStart
                    )
                    Log.d(TAG, "✅ Prayer start notification SHOWN for $prayerName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing prayer start for $prayerName", e)
                notificationLogRepository.logEvent(
                    NotificationEventType.ERROR,
                    prayerName,
                    "Start error: ${e.message}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    private fun handlePrayerNotification(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(NotificationScheduler.EXTRA_PRAYER_NAME) ?: run {
            Log.e(TAG, "❌ Prayer notification received but no prayer name!")
            return
        }
        val minutesRemaining = intent.getIntExtra(NotificationScheduler.EXTRA_MINUTES_REMAINING, 30)
        
        Log.d(TAG, "🔔 Prayer notification FIRED: $prayerName, $minutesRemaining minutes remaining")
        
        // Use goAsync() for longer-running operations in BroadcastReceiver
        val pendingResult = goAsync()
        val notificationLogRepository = NotificationLogRepository(context)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Log that we received the alarm - this is CRITICAL for debugging
                notificationLogRepository.logEvent(
                    NotificationEventType.FIRED,
                    prayerName,
                    "Alarm fired! $minutesRemaining min before end"
                )
                
                val settingsRepository = SettingsRepository(context)
                val settings = settingsRepository.getSettings()
                
                // Double-check that notifications are still enabled
                if (settings.globalNotificationsEnabled) {
                    NotificationHelper.showPrayerNotification(
                        context = context,
                        prayerName = prayerName,
                        minutesRemaining = minutesRemaining,
                        style = settings.notificationStyleEndReminder
                    )
                    Log.d(TAG, "✅ Notification SHOWN for $prayerName")
                } else {
                    Log.d(TAG, "⚠️ Notifications disabled globally, not showing for $prayerName")
                    notificationLogRepository.logEvent(
                        NotificationEventType.SKIPPED,
                        prayerName,
                        "Global notifications disabled at fire time"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing notification for $prayerName", e)
                notificationLogRepository.logEvent(
                    NotificationEventType.ERROR,
                    prayerName,
                    "Error: ${e.message}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    private fun handleTestPrayerStartNotification(context: Context) {
        Log.d(TAG, "🔔 Test prayer-start FIRED (debug)")

        val pendingResult = goAsync()
        val notificationLogRepository = NotificationLogRepository(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationLogRepository.logEvent(
                    NotificationEventType.FIRED,
                    "TEST_START",
                    "Test prayer-start (Maghrib) fired"
                )

                val settingsRepository = SettingsRepository(context)
                val settings = settingsRepository.getSettings()

                NotificationHelper.showPrayerStartedNotification(
                    context = context,
                    prayerName = Prayer.MAGHRIB.name,
                    style = settings.notificationStylePrayerStart,
                    useEzan = settings.useEzanForPrayerStart,
                    notificationIdOverride = NotificationHelper.NOTIFICATION_ID_TEST_PRAYER_START
                )
                Log.d(TAG, "✅ Test prayer-start notification SHOWN")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing test prayer-start", e)
                notificationLogRepository.logEvent(
                    NotificationEventType.ERROR,
                    "TEST_START",
                    "Error: ${e.message}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleTestNotification(context: Context) {
        Log.d(TAG, "🔔 Test notification FIRED")
        
        val pendingResult = goAsync()
        val notificationLogRepository = NotificationLogRepository(context)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationLogRepository.logEvent(
                    NotificationEventType.FIRED,
                    "TEST",
                    "Test alarm fired!"
                )
                
                val settingsRepository = SettingsRepository(context)
                val settings = settingsRepository.getSettings()
                
                NotificationHelper.showTestNotification(
                    context = context,
                    offsetMinutes = settings.reminderOffsetMinutes,
                    style = settings.notificationStyleEndReminder
                )
                Log.d(TAG, "✅ Test notification SHOWN")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing test notification", e)
                notificationLogRepository.logEvent(
                    NotificationEventType.ERROR,
                    "TEST",
                    "Error: ${e.message}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    /**
     * Handle device boot - reschedule notifications.
     * AlarmManager alarms are cleared on device reboot, so we need to reschedule.
     */
    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "📱 Boot completed, will reschedule via WorkManager")
        
        // Log boot event
        val notificationLogRepository = NotificationLogRepository(context)
        CoroutineScope(Dispatchers.IO).launch {
            notificationLogRepository.logEvent(
                NotificationEventType.CANCELLED,
                "ALL",
                "Device rebooted - alarms cleared"
            )
        }
    }
}
