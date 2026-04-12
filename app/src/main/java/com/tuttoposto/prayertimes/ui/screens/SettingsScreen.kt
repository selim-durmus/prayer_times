package com.tuttoposto.prayertimes.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuttoposto.prayertimes.data.models.NotificationEventType
import com.tuttoposto.prayertimes.data.models.NotificationLogEntry
import com.tuttoposto.prayertimes.data.models.NotificationStyle
import com.tuttoposto.prayertimes.R
import com.tuttoposto.prayertimes.data.models.SyncLogEntry
import com.tuttoposto.prayertimes.data.models.SyncSource
import com.tuttoposto.prayertimes.ui.theme.PrayerTimesColors
import com.tuttoposto.prayertimes.ui.viewmodels.NotificationStatusInfo
import com.tuttoposto.prayertimes.ui.viewmodels.PrayerTimeDebugInfo
import com.tuttoposto.prayertimes.ui.viewmodels.SettingsUiState
import com.tuttoposto.prayertimes.ui.viewmodels.SettingsViewModel
import kotlin.math.roundToInt

/**
 * Settings screen - controls for notification behavior and debug info.
 * 
 * Sections:
 * 1. Notifications - Global toggle, per-prayer toggles, offset, style
 * 2. Sync Status - Last sync, next sync, permission status, force sync button
 * 3. Today's Schedule - Debug info showing prayer times and notification status
 * 4. Sync Log - Recent sync attempts for debugging
 * 5. Testing - Test notification button
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header - Long press for 3 seconds to toggle debug mode
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            // This triggers after ~500ms, but we want 3 seconds
                            // The actual delay is handled inside
                        },
                        onPress = {
                            // Start timing when press begins
                            val startTime = System.currentTimeMillis()
                            val wasReleased = tryAwaitRelease()
                            val pressDuration = System.currentTimeMillis() - startTime
                            
                            // If held for 3+ seconds, toggle debug mode
                            if (pressDuration >= 3000) {
                                viewModel.toggleDebugMode()
                                val message = if (uiState.debugModeEnabled) 
                                    "Debug mode disabled" 
                                else 
                                    "Debug mode enabled"
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
        )
        
        // Notifications Section (always visible)
        NotificationsSection(
            state = uiState,
            onGlobalToggle = viewModel::setGlobalNotificationsEnabled,
            onPrayerToggle = viewModel::setPrayerEnabled,
            onOffsetChange = viewModel::setReminderOffset,
            onEndReminderStyleChange = viewModel::setNotificationStyleEndReminder,
            onPrayerStartStyleChange = viewModel::setNotificationStylePrayerStart,
            onNotifyPrayerStartChange = viewModel::setNotifyOnPrayerStart,
            onUseEzanForPrayerStartChange = viewModel::setUseEzanForPrayerStart
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Display Section (always visible)
        SectionCard(title = "Display") {
            SettingsToggleRow(
                title = "AMOLED black theme",
                subtitle = "Pure black background for OLED screens",
                checked = uiState.useAmoledTheme,
                onCheckedChange = viewModel::setUseAmoledTheme
            )
        }
        
        // === DEBUG SECTIONS (hidden by default) ===
        if (uiState.debugModeEnabled) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Sync Status Section
            SyncStatusSection(
                state = uiState,
                onForceSync = viewModel::forceSync
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Today's Schedule Section
            TodayScheduleSection(
                prayerTimes = uiState.prayerTimesInfo,
                notificationStatus = uiState.notificationStatusInfo,
                hasPrayerData = uiState.hasPrayerData
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Sync Log Section
            if (uiState.recentSyncLogs.isNotEmpty()) {
                SyncLogSection(logs = uiState.recentSyncLogs)
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Notification Log Section (for debugging alarm fires)
            NotificationLogSection(
                logs = uiState.recentNotificationLogs,
                onClearLog = viewModel::clearNotificationLog
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Testing Section
            TestingSection(
                onTestNotification = viewModel::sendTestNotification,
                onDelayedTestNotification = viewModel::sendDelayedTestNotification,
                onTestPrayerStartNotification = viewModel::sendTestPrayerStartNotification
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Footer
        Text(
            text = "Created by Selim Durmus",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun NotificationsSection(
    state: SettingsUiState,
    onGlobalToggle: (Boolean) -> Unit,
    onPrayerToggle: (String, Boolean) -> Unit,
    onOffsetChange: (Int) -> Unit,
    onEndReminderStyleChange: (NotificationStyle) -> Unit,
    onPrayerStartStyleChange: (NotificationStyle) -> Unit,
    onNotifyPrayerStartChange: (Boolean) -> Unit,
    onUseEzanForPrayerStartChange: (Boolean) -> Unit
) {
    SectionCard(title = "Notifications") {
        // Global toggle
        SettingsToggleRow(
            title = "Enable prayer notifications",
            subtitle = "Master toggle for all notifications",
            checked = state.globalNotificationsEnabled,
            onCheckedChange = onGlobalToggle
        )
        
        if (state.globalNotificationsEnabled) {
            HorizontalDivider(
                color = PrayerTimesColors.divider,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            // Per-prayer toggles
            Text(
                text = "Prayer Notifications",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            SettingsToggleRow(
                title = "Fajr",
                checked = state.fajrEnabled,
                onCheckedChange = { onPrayerToggle("FAJR", it) }
            )
            SettingsToggleRow(
                title = "Dhuhr",
                checked = state.dhuhrEnabled,
                onCheckedChange = { onPrayerToggle("DHUHR", it) }
            )
            SettingsToggleRow(
                title = "Asr",
                checked = state.asrEnabled,
                onCheckedChange = { onPrayerToggle("ASR", it) }
            )
            SettingsToggleRow(
                title = "Maghrib",
                checked = state.maghribEnabled,
                onCheckedChange = { onPrayerToggle("MAGHRIB", it) }
            )
            SettingsToggleRow(
                title = "Isha",
                checked = state.ishaEnabled,
                onCheckedChange = { onPrayerToggle("ISHA", it) }
            )

            HorizontalDivider(
                color = PrayerTimesColors.divider,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            SettingsToggleRow(
                title = stringResource(R.string.settings_notify_prayer_start),
                subtitle = stringResource(R.string.settings_notify_prayer_start_sub),
                checked = state.notifyOnPrayerStart,
                onCheckedChange = onNotifyPrayerStartChange
            )

            if (state.notifyOnPrayerStart) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_use_ezan_prayer_start),
                    checked = state.useEzanForPrayerStart,
                    onCheckedChange = onUseEzanForPrayerStartChange
                )
            }
            
            HorizontalDivider(
                color = PrayerTimesColors.divider,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            // Reminder offset slider
            ReminderOffsetSlider(
                currentValue = state.reminderOffsetMinutes,
                onValueChange = onOffsetChange
            )
            
            HorizontalDivider(
                color = PrayerTimesColors.divider,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            NotificationStyleBlock(
                title = stringResource(R.string.settings_notification_style_before_end),
                currentStyle = state.notificationStyleEndReminder,
                onStyleChange = onEndReminderStyleChange
            )

            if (state.notifyOnPrayerStart) {
                Spacer(modifier = Modifier.height(16.dp))
                NotificationStyleBlock(
                    title = stringResource(R.string.settings_notification_style_prayer_start),
                    currentStyle = state.notificationStylePrayerStart,
                    onStyleChange = onPrayerStartStyleChange
                )
            }

            HorizontalDivider(
                color = PrayerTimesColors.divider,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            // Permissions status
            PermissionsStatusSection()
            
            HorizontalDivider(
                color = PrayerTimesColors.divider,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            // Calculation info
            CalculationInfoSection()
        }
    }
}

@Composable
private fun PermissionsStatusSection() {
    val context = LocalContext.current
    
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // Not required before Android 13
    }

    val hasLocationPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val hasFgsMediaPlayback = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
    ) == PackageManager.PERMISSION_GRANTED
    
    Column {
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Notification permission
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasNotificationPermission) "✓ Granted" else "✗ Denied",
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasNotificationPermission) 
                    PrayerTimesColors.success 
                else 
                    MaterialTheme.colorScheme.error
            )
        }
        
        // Location permission
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Location",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasLocationPermission) "✓ Granted" else "✗ Denied",
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasLocationPermission) 
                    PrayerTimesColors.success 
                else 
                    MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.permission_fgs_media_playback),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasFgsMediaPlayback) "✓ Granted" else "✗ Denied",
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasFgsMediaPlayback) {
                    PrayerTimesColors.success
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        
        // Battery optimization disclaimer
        Text(
            text = "💡 For best results, disable battery optimization for this app in your device settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun CalculationInfoSection() {
    Column {
        Text(
            text = "Calculation Method",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Method",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "ISNA",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "School",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Hanafi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = "Islamic Society of North America (ISNA) angles with Hanafi juristic method for Asr calculation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun SyncStatusSection(
    state: SettingsUiState,
    onForceSync: () -> Unit
) {
    val context = LocalContext.current
    
    SectionCard(title = "Sync Status") {
        // Last sync time
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Last successful sync",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = state.lastSyncTime ?: "Never",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.lastSyncTime != null) 
                    PrayerTimesColors.success 
                else 
                    MaterialTheme.colorScheme.error
            )
        }
        
        // Next scheduled sync
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Next midnight sync",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = state.nextMidnightSync ?: "Not scheduled",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Android's actual next alarm (most reliable)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Android AlarmManager",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = state.androidNextAlarm,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (state.androidNextAlarm.contains("No alarm")) 
                    MaterialTheme.colorScheme.error 
                else 
                    PrayerTimesColors.success
            )
        }
        
        HorizontalDivider(
            color = PrayerTimesColors.divider,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        
        // Exact alarms permission status
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Exact alarms permission",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Required for reliable midnight sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (state.canScheduleExactAlarms) {
                Text(
                    text = "✓ Granted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrayerTimesColors.success
                )
            } else {
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text(
                        text = "Grant",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        if (!state.canScheduleExactAlarms) {
            Text(
                text = "⚠️ Without this permission, the midnight sync alarm may be delayed, " +
                       "potentially causing missed morning prayer notifications.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Force sync button
        Button(
            onClick = onForceSync,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSyncing,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (state.isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "  Syncing...",
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else {
                Text(text = "Force sync now")
            }
        }
        
        Text(
            text = "Manually fetch prayer times and reschedule notifications",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SyncLogSection(logs: List<SyncLogEntry>) {
    SectionCard(title = "Recent Sync Log") {
        Text(
            text = "Last ${logs.size} sync attempts (newest first)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        logs.forEach { log ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Status indicator
                Text(
                    text = if (log.success) "✓" else "✗",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (log.success) PrayerTimesColors.success else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp)
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = log.source.toDisplayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = log.formatTimestamp(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Text(
                        text = log.message + if (log.success && log.notificationsScheduled > 0) 
                            " (${log.notificationsScheduled} notifications)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (log.success) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
            
            if (log != logs.last()) {
                HorizontalDivider(
                    color = PrayerTimesColors.divider.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

private fun SyncSource.toDisplayName(): String = when (this) {
    SyncSource.MIDNIGHT_ALARM -> "Midnight Alarm"
    SyncSource.MANUAL -> "Manual"
    SyncSource.APP_OPEN -> "App Open"
    SyncSource.BOOT -> "Boot"
    SyncSource.WORKMANAGER -> "WorkManager"
    SyncSource.UNKNOWN -> "Unknown"
}

@Composable
private fun NotificationLogSection(
    logs: List<NotificationLogEntry>,
    onClearLog: () -> Unit
) {
    SectionCard(title = "Notification Events Log") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${logs.size} events",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (logs.isNotEmpty()) {
                TextButton(onClick = onClearLog) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        if (logs.isEmpty()) {
            Text(
                text = "No events logged yet. Try force syncing or sending a test notification.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            // Scrollable log container with fixed height
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                logs.forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Event icon
                        Text(
                            text = log.getIcon(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${log.prayerName} - ${log.eventType.toDisplayName()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = log.eventType.toColor()
                                )
                                Text(
                                    text = log.formatTimestamp(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Text(
                                text = log.details,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    
                    if (log != logs.last()) {
                        HorizontalDivider(
                            color = PrayerTimesColors.divider.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun NotificationEventType.toDisplayName(): String = when (this) {
    NotificationEventType.SCHEDULED -> "Scheduled"
    NotificationEventType.CANCELLED -> "Cancelled"
    NotificationEventType.FIRED -> "FIRED!"
    NotificationEventType.SKIPPED -> "Skipped"
    NotificationEventType.ERROR -> "Error"
    NotificationEventType.UNKNOWN -> "Unknown"
}

@Composable
private fun NotificationEventType.toColor(): Color = when (this) {
    NotificationEventType.SCHEDULED -> PrayerTimesColors.success
    NotificationEventType.CANCELLED -> MaterialTheme.colorScheme.error
    NotificationEventType.FIRED -> MaterialTheme.colorScheme.primary
    NotificationEventType.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    NotificationEventType.ERROR -> MaterialTheme.colorScheme.error
    NotificationEventType.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ReminderOffsetSlider(
    currentValue: Int,
    onValueChange: (Int) -> Unit
) {
    var sliderValue by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    
    Column {
        Text(
            text = "Reminder Offset",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = "Notify ${sliderValue.roundToInt()} minutes before prayer ends",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "30",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChange(sliderValue.roundToInt()) },
                valueRange = 30f..60f,
                steps = 5, // 30, 35, 40, 45, 50, 55, 60
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            
            Text(
                text = "60",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotificationStyleBlock(
    title: String,
    currentStyle: NotificationStyle,
    onStyleChange: (NotificationStyle) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StyleButton(
                text = stringResource(R.string.settings_style_normal),
                selected = currentStyle == NotificationStyle.NORMAL,
                onClick = { onStyleChange(NotificationStyle.NORMAL) },
                modifier = Modifier.weight(1f)
            )

            StyleButton(
                text = stringResource(R.string.settings_style_alarm),
                selected = currentStyle == NotificationStyle.ALARMY,
                onClick = { onStyleChange(NotificationStyle.ALARMY) },
                modifier = Modifier.weight(1f)
            )
        }
        
        Text(
            text = when (currentStyle) {
                NotificationStyle.NORMAL ->
                    stringResource(R.string.settings_style_normal_desc)
                NotificationStyle.ALARMY ->
                    stringResource(R.string.settings_style_alarm_desc)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun StyleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) 
                MaterialTheme.colorScheme.onPrimary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text = text)
    }
}

@Composable
private fun TodayScheduleSection(
    prayerTimes: List<PrayerTimeDebugInfo>,
    notificationStatus: List<NotificationStatusInfo>,
    hasPrayerData: Boolean
) {
    SectionCard(title = "Today's Schedule") {
        if (!hasPrayerData) {
            Text(
                text = "No prayer data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Prayer times table
            Text(
                text = "Prayer Times",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            prayerTimes.forEach { prayer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = prayer.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${prayer.startTime} - ${prayer.endTime}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            HorizontalDivider(
                color = PrayerTimesColors.divider,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            // Notification status
            Text(
                text = "Scheduled Notifications (System Verified)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            notificationStatus.forEach { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = status.prayerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = status.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            status.status.contains("Alarm at") -> PrayerTimesColors.success
                            status.status.contains("Disabled") -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TestingSection(
    onTestNotification: () -> Unit,
    onDelayedTestNotification: () -> Unit,
    onTestPrayerStartNotification: () -> Unit
) {
    SectionCard(title = "Testing") {
        Text(
            text = "Send a test notification to verify your settings are working correctly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Button(
            onClick = onTestNotification,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Send test notification (in 5 seconds)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onTestPrayerStartNotification,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Test prayer-start notification (in 5 seconds, Maghrib)")
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Test if alarms survive app close: Schedule notification, close app, wait 2 min.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Button(
            onClick = onDelayedTestNotification,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onError
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "🧪 Delayed test (2 min) - CLOSE APP AFTER!")
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = PrayerTimesColors.cardBackground
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
