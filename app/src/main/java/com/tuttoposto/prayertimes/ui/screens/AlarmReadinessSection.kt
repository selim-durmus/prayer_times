package com.tuttoposto.prayertimes.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tuttoposto.prayertimes.notifications.ReadinessAction
import com.tuttoposto.prayertimes.notifications.ReadinessCheck

@Composable
internal fun AlarmReadinessSection(checks: List<ReadinessCheck>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val problems = checks.count { it.needsAttention }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
            Column(Modifier.weight(1f)) {
                Text("Alarm readiness", style = MaterialTheme.typography.titleSmall)
                Text(when {
                    checks.isEmpty() -> "Checking…"
                    problems == 0 -> "Checks passed for current settings"
                    else -> "$problems ${if (problems == 1) "item needs" else "items need"} attention"
                }, style = MaterialTheme.typography.bodySmall,
                    color = if (problems > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Details") }
        }
        if (expanded) {
            checks.forEach { check ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(check.label, style = MaterialTheme.typography.bodyMedium)
                        Text(check.detail, style = MaterialTheme.typography.bodySmall,
                            color = if (check.needsAttention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (check.action != null) TextButton(onClick = { openReadinessSettings(context, check) }) {
                        Text(if (check.needsAttention) "Review" else "Open")
                    }
                }
            }
            Text("Checks reflect this moment, not a delivery guarantee. Try your sound before relying on it to wake you.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

internal fun openReadinessSettings(context: Context, check: ReadinessCheck) {
    val intent = when (check.action) {
        ReadinessAction.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        ReadinessAction.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        ReadinessAction.FULL_SCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        ReadinessAction.CHANNEL -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, check.channelId)
        ReadinessAction.SOUND -> Intent(Settings.ACTION_SOUND_SETTINGS)
        ReadinessAction.MODES -> Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    try { context.startActivity(intent) }
    catch (_: Exception) {
        try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
        catch (_: Exception) { Toast.makeText(context, "Open Android Settings to review this setting.", Toast.LENGTH_SHORT).show() }
    }
}
