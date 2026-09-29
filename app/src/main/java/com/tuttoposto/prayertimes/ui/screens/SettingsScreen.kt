package com.tuttoposto.prayertimes.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuttoposto.prayertimes.data.models.NotificationEventType
import com.tuttoposto.prayertimes.data.models.NotificationLogEntry
import com.tuttoposto.prayertimes.data.models.NotificationStyle
import com.tuttoposto.prayertimes.data.models.ReminderSound
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
 * Daily Fajr actions stay visible; infrequent setup is grouped into saved disclosures.
 * Existing setting callbacks and hidden diagnostics are shared with the original screen.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val previewRunning by viewModel.previewRunning.collectAsState()
    val previewMessage by viewModel.previewMessage.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshReadiness()
                delay(5_000)
            }
        }
    }
    
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
        
        FajrWakeUpSection(
            state = uiState,
            onPrayerEnabled = { viewModel.setPrayerEnabled("FAJR", it) },
            onStartEzan = { viewModel.setPrayerEzanEnabled("FAJR", it) },
            onEnabled = { viewModel.setFajrWakeEnabled(it) },
            onMinutes = { viewModel.setFajrWakeMinutes(it) },
            onSound = { viewModel.setFajrWakeSound(it) },
            onStyle = { viewModel.setFajrWakeStyle(it) },
            onStart = { viewModel.setFajrWakeStart(it) },
            previewRunning = previewRunning,
            previewMessage = previewMessage,
            onTest = viewModel::testFajrReminder,
            onStopTest = viewModel::stopFajrPreview,
            onSkip = { viewModel.skipFajrWakeUp(uiState.fajrSkipTarget) },
            onUndoSkip = { viewModel.undoFajrSkip(uiState.fajrSkippedOccurrence) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        SectionCard(title = "Notifications") {
            SettingsToggleRow(
                title = "Enable prayer notifications",
                subtitle = "Master switch for all prayer alerts",
                checked = uiState.globalNotificationsEnabled,
                onCheckedChange = viewModel::setGlobalNotificationsEnabled
            )
            AlarmReadinessSection(uiState.readinessChecks)
        }

        Spacer(modifier = Modifier.height(16.dp))
        NotificationsSection(
            state = uiState,
            onPrayerToggle = viewModel::setPrayerEnabled,
            onOffsetChange = viewModel::setReminderOffset,
            onEndReminderStyleChange = viewModel::setNotificationStyleEndReminder,
            onPrayerStartStyleChange = viewModel::setNotificationStylePrayerStart,
            onNotifyPrayerStartChange = viewModel::setNotifyOnPrayerStart,
            onPrayerEzanToggle = viewModel::setPrayerEzanEnabled
        )

        Spacer(modifier = Modifier.height(16.dp))
        ExpandableSectionCard(title = "Appearance & calculation",
            summary = if (uiState.useAmoledTheme) "AMOLED black · ISNA / Hanafi" else "Dark theme · ISNA / Hanafi") {
            SettingsToggleRow(
                title = "AMOLED black theme",
                subtitle = "Pure black background for OLED screens",
                checked = uiState.useAmoledTheme,
                onCheckedChange = viewModel::setUseAmoledTheme
            )
            HorizontalDivider(color = PrayerTimesColors.divider, modifier = Modifier.padding(vertical = 12.dp))
            CalculationInfoSection()
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
    onPrayerToggle: (String, Boolean) -> Unit,
    onOffsetChange: (Int) -> Unit,
    onEndReminderStyleChange: (NotificationStyle) -> Unit,
    onPrayerStartStyleChange: (NotificationStyle) -> Unit,
    onNotifyPrayerStartChange: (Boolean) -> Unit,
    onPrayerEzanToggle: (String, Boolean) -> Unit
) {
    val prayers = listOf(
        Triple("FAJR", state.fajrEnabled, state.fajrEzan),
        Triple("DHUHR", state.dhuhrEnabled, state.dhuhrEzan),
        Triple("ASR", state.asrEnabled, state.asrEzan),
        Triple("MAGHRIB", state.maghribEnabled, state.maghribEzan),
        Triple("ISHA", state.ishaEnabled, state.ishaEzan)
    ).filterNot { it.first == "FAJR" && state.fajrWakeUp.enabled }
    ExpandableSectionCard(
        title = "Prayer reminders",
        summary = "${state.reminderOffsetMinutes} min before end · ${if (state.notificationStyleEndReminder == NotificationStyle.ALARMY) "Alarm-like" else "Normal"}"
    ) {
        Text(
            if (state.fajrWakeUp.enabled) "Fajr has its own settings in the card above. These prayer switches also control prayer-start alerts."
            else "Prayer switches control both before-end reminders and prayer-start alerts.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        prayers.forEach { (name, enabled, _) ->
            SettingsToggleRow(
                title = com.tuttoposto.prayertimes.data.models.Prayer.valueOf(name).displayName,
                subtitle = if (enabled) "Alerts enabled" else "Alerts off",
                checked = enabled,
                onCheckedChange = { onPrayerToggle(name, it) }
            )
        }
        HorizontalDivider(color = PrayerTimesColors.divider, modifier = Modifier.padding(vertical = 12.dp))
        ReminderOffsetSlider(state.reminderOffsetMinutes, onOffsetChange)
        NotificationStyleBlock(
            title = stringResource(R.string.settings_notification_style_before_end),
            currentStyle = state.notificationStyleEndReminder,
            onStyleChange = onEndReminderStyleChange
        )
        Text(
            "Before-end reminders include Remind in 10 min. Close to the prayer end, the delay shortens to leave at least 2 minutes. Separate Fajr wake-ups stay unchanged.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    ExpandableSectionCard(
        title = "Prayer-start alerts",
        summary = if (state.notifyOnPrayerStart) "On · Ezan and alert style" else "Off · Optional alerts when prayer begins"
    ) {
        SettingsToggleRow(
            title = stringResource(R.string.settings_notify_prayer_start),
            subtitle = stringResource(R.string.settings_notify_prayer_start_sub),
            checked = state.notifyOnPrayerStart,
            onCheckedChange = onNotifyPrayerStartChange
        )
        if (state.notifyOnPrayerStart) {
            NotificationStyleBlock(
                title = stringResource(R.string.settings_notification_style_prayer_start),
                currentStyle = state.notificationStylePrayerStart,
                onStyleChange = onPrayerStartStyleChange
            )
            HorizontalDivider(color = PrayerTimesColors.divider, modifier = Modifier.padding(vertical = 12.dp))
            Text("Ezan at prayer start", style = MaterialTheme.typography.titleSmall)
            prayers.forEach { (name, enabled, ezan) ->
                SettingsToggleRow(
                    title = com.tuttoposto.prayertimes.data.models.Prayer.valueOf(name).displayName,
                    subtitle = if (!enabled) "Prayer alerts are off in Prayer reminders"
                        else if (ezan) "Ezan" else if (state.notificationStylePrayerStart == NotificationStyle.ALARMY) "Alarm sound" else "Notification sound",
                    checked = ezan,
                    onCheckedChange = { onPrayerEzanToggle(name, it) }
                )
            }
            if (state.fajrWakeUp.enabled) Text(
                "Fajr start permission and Ezan are in Fajr wake-up settings above.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FajrWakeUpSection(
    state: SettingsUiState,
    onPrayerEnabled: (Boolean) -> Unit,
    onStartEzan: (Boolean) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onMinutes: (Int) -> Unit,
    onSound: (ReminderSound) -> Unit,
    onStyle: (NotificationStyle) -> Unit,
    onStart: (Boolean) -> Unit,
    previewRunning: Boolean,
    previewMessage: String?,
    onTest: () -> Unit,
    onStopTest: () -> Unit,
    onSkip: () -> Unit,
    onUndoSkip: () -> Unit
) {
    val wake = state.fajrWakeUp
    val displayedMinutes = com.tuttoposto.prayertimes.data.models.FajrReminderTiming.selectionRange(state.nextFajrWindow)
        ?.let { wake.minutesBeforeSunrise.coerceIn(it) } ?: wake.minutesBeforeSunrise
    val context = LocalContext.current
    val fullScreen = wake.enabled && wake.style == NotificationStyle.ALARMY
    val accessNeeded = state.readinessChecks.firstOrNull {
        it.action == com.tuttoposto.prayertimes.notifications.ReadinessAction.FULL_SCREEN && it.needsAttention
    }
    SectionCard(title = "Fajr wake-up") {
        FajrSkipControls(state, onSkip, onUndoSkip)
        SettingsDisclosure(
            title = "Wake-up settings",
            summary = if (wake.enabled) "$displayedMinutes min before sunrise · Separate reminder"
                else "Shared ${state.reminderOffsetMinutes}-minute reminder",
            keepOpen = previewRunning
        ) {
            SettingsToggleRow(
                title = "Use separate Fajr reminder",
                subtitle = "Replaces Fajr’s shared before-end reminder.",
                checked = wake.enabled,
                onCheckedChange = onEnabled
            )
            if (wake.enabled) {
                SettingsToggleRow(
                    title = "Enable Fajr notifications",
                    subtitle = "Controls all Fajr alerts, including the wake-up reminder.",
                    checked = state.fajrEnabled,
                    onCheckedChange = onPrayerEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = PrayerTimesColors.divider)
                FajrMinutePicker(wake.minutesBeforeSunrise, state.nextFajrWindow, onMinutes)
                Text("Wake-up sound", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                // Full-width options remain readable at larger font sizes.
                ReminderSound.entries.forEach { sound ->
                    StyleButton(
                        text = when (sound) {
                            ReminderSound.EZAN -> "Ezan"
                            ReminderSound.ALARM -> "Alarm sound"
                            ReminderSound.NOTIFICATION -> "Notification sound"
                        },
                        selected = wake.sound == sound,
                        onClick = { onSound(sound) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                NotificationStyleBlock(
                    title = "Wake-up alert style",
                    currentStyle = wake.style,
                    onStyleChange = onStyle
                )
                Text(
                    text = "Alarm-like uses alarm volume and a Fajr-only lock-screen alarm with Stop. Normal uses notification volume and stays a notification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )
                SettingsToggleRow(
                    title = "Also allow the Fajr start alert",
                    subtitle = "Optional extra alert when Fajr begins. Also requires Prayer-start alerts below to be on.",
                    checked = wake.notifyAtStart,
                    onCheckedChange = onStart
                )
            }
            if (!wake.enabled) {
                Text(
                    text = "Fajr is in Prayer reminders below and uses the shared ${state.reminderOffsetMinutes}-minute reminder and style.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (wake.enabled && state.notifyOnPrayerStart && wake.notifyAtStart) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = PrayerTimesColors.divider)
                SettingsToggleRow(
                    title = "Ezan for Fajr start alert",
                    subtitle = "Only affects the alert when Fajr begins. The wake-up reminder uses its own sound.",
                    checked = state.fajrEzan,
                    onCheckedChange = onStartEzan
                )
            }
            TextButton(
                onClick = {
                    if (previewRunning) onStopTest()
                    else if (fullScreen && accessNeeded != null) openReadinessSettings(context, accessNeeded)
                    else onTest()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(when {
                previewRunning -> "Stop test"
                fullScreen && accessNeeded != null -> "Allow full-screen alarms to test"
                fullScreen -> "Test my Fajr alarm (10 s)"
                else -> "Test my Fajr reminder"
            }) }
            Text(
                text = previewMessage ?: if (fullScreen)
                    "Test starts after 10 seconds so you can lock your phone, then stops after 8 seconds. Android may show a banner while unlocked; tap it to open the alarm. Works even if app alerts are paused."
                    else "Plays your current Fajr sound and style now, even if alerts are paused. Stops after 8 seconds; real alarms take priority.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (wake.enabled && (!state.globalNotificationsEnabled || !state.fajrEnabled)) {
                Text(
                    text = "Fajr alerts are paused until both prayer notifications and Fajr notifications are enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FajrSkipControls(state: SettingsUiState, onSkip: () -> Unit, onUndo: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = when {
                !state.globalNotificationsEnabled -> "Paused: prayer notifications are off."
                !state.fajrEnabled -> "Paused: Fajr notifications are off."
                state.fajrNextWakeUp != null -> "Next wake-up: ${state.fajrNextWakeUp}"
                else -> "No upcoming wake-up scheduled. Saved prayer times are required."
            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary
        )
        val skipped = state.fajrSkippedOccurrence
        if (skipped != null) {
            val today = java.time.LocalDate.now(java.time.ZoneId.of(skipped.timezoneId))
            val date = skipped.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d"))
            val label = if (skipped.date == today) "today ($date)" else date
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wake-up skipped for $label", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onUndo, enabled = !state.fajrSkipBusy) { Text("Undo") }
            }
            Text("Only this wake-up is skipped. Prayer-start alerts stay unchanged.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (state.fajrSkipTarget != null) {
            Button(onClick = onSkip, enabled = !state.fajrSkipBusy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Skip next Fajr wake-up")
            }
        }
        state.fajrSkipError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
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
private fun ExpandableSectionCard(title: String, summary: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PrayerTimesColors.cardBackground)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            SettingsDisclosure(title, summary, content = content)
        }
    }
}

@Composable
private fun SettingsDisclosure(
    title: String,
    summary: String,
    keepOpen: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visible = expanded || keepOpen
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .semantics { stateDescription = if (visible) "Expanded" else "Collapsed" }
                .clickable(role = Role.Button, enabled = !keepOpen) { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Text(if (keepOpen) "Testing" else if (visible) "Hide" else "Edit",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (visible) {
            HorizontalDivider(color = PrayerTimesColors.divider, modifier = Modifier.padding(vertical = 12.dp))
            content()
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
