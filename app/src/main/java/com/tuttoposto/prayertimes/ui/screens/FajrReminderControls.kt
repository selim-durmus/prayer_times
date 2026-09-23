package com.tuttoposto.prayertimes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tuttoposto.prayertimes.data.models.FajrReminderTiming
import com.tuttoposto.prayertimes.data.models.PrayerTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FajrMinutePicker(savedMinutes: Int, nextWindow: PrayerTime?, onMinutes: (Int) -> Unit) {
    val range = FajrReminderTiming.selectionRange(nextWindow)
    if (range == null) {
        Text(if (nextWindow == null) "Saved Fajr times are needed to adjust the wake-up time."
            else "This Fajr window is shorter than 5 minutes. The reminder stays limited to Fajr start.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var minutes by remember(savedMinutes, range) { mutableFloatStateOf(savedMinutes.coerceIn(range).toFloat()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    TextButton(onClick = { input = minutes.roundToInt().toString(); editing = true }) {
        Text("${minutes.roundToInt()} minutes before sunrise · Edit", style = MaterialTheme.typography.titleSmall)
    }
    Slider(
        value = minutes, onValueChange = { minutes = it },
        onValueChangeFinished = { onMinutes(minutes.roundToInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceAtLeast(0),
        enabled = range.last > range.first, modifier = Modifier.fillMaxWidth()
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 60, 90).filter { it in range }.forEach { preset ->
            FilterChip(selected = minutes.roundToInt() == preset,
                onClick = { minutes = preset.toFloat(); onMinutes(preset) }, label = { Text("$preset min") })
        }
    }
    val start = Instant.ofEpochMilli(requireNotNull(nextWindow).startTimeMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE HH:mm"))
    Text(
        text = "Up to ${range.last} minutes before sunrise · Fajr starts $start",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
    if (editing) {
        val parsed = FajrReminderTiming.parseMinutes(input, range)
        fun save() { parsed?.let { minutes = it.toFloat(); onMinutes(it); editing = false } }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Minutes before sunrise") },
            text = {
                Column {
                    OutlinedTextField(
                        value = input, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) input = it },
                        singleLine = true, label = { Text("Minutes") }, isError = parsed == null,
                        supportingText = { Text("Enter ${range.first}–${range.last} minutes, within the Fajr window.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }), modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = { TextButton(onClick = { save() }, enabled = parsed != null) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }
}
