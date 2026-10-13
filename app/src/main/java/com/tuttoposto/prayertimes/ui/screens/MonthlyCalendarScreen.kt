package com.tuttoposto.prayertimes.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuttoposto.prayertimes.ui.theme.PrayerTimesColors
import com.tuttoposto.prayertimes.ui.theme.TabularNumbers
import com.tuttoposto.prayertimes.ui.viewmodels.DayPrayerTimes
import com.tuttoposto.prayertimes.ui.viewmodels.MonthlyCalendarUiState
import com.tuttoposto.prayertimes.ui.viewmodels.MonthlyCalendarViewModel
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthlyCalendarScreen(
    viewModel: MonthlyCalendarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()

    // Month changes load from the navigator itself; this only covers the first visit.
    LaunchedEffect(Unit) { viewModel.loadMonth() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp)
    ) {
        MonthNavigator(
            currentMonth = currentMonth,
            onPrevious = viewModel::goToPreviousMonth,
            onNext = viewModel::goToNextMonth,
            onToday = viewModel::goToToday
        )

        when (val state = uiState) {
            is MonthlyCalendarUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 3.dp)
            }
            is MonthlyCalendarUiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            is MonthlyCalendarUiState.Success -> {
                val listState = rememberLazyListState()
                // Open on today (with a little context above it) whenever this month is shown.
                LaunchedEffect(state.days) {
                    val today = state.days.indexOfFirst { it.isToday }
                    listState.scrollToItem(if (today > 2) today - 2 else 0)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    stickyHeader { TimesHeader() }
                    items(state.days, key = { it.dayOfMonth }) { day -> DayRow(day) }
                }
            }
        }
    }
}

@Composable
private fun MonthNavigator(
    currentMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month",
                tint = MaterialTheme.colorScheme.primary)
        }
        if (currentMonth != YearMonth.now()) {
            TextButton(onClick = onToday) { Text("Today") }
        }
    }
}

@Composable
private fun TimesHeader() {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val style = MaterialTheme.typography.labelMedium
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text("Day", style = style, color = color, modifier = Modifier.width(52.dp))
            listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").forEach {
                Text(it, style = style, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    maxLines = 1)
            }
        }
        HorizontalDivider(color = PrayerTimesColors.divider)
    }
}

@Composable
private fun DayRow(day: DayPrayerTimes) {
    val textColor = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val timeStyle = MaterialTheme.typography.bodyMedium.merge(TabularNumbers)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (day.isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.background
            )
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(52.dp)) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            day.date?.let {
                Text(
                    text = it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.isToday) textColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        listOf(day.fajr, day.dhuhr, day.asr, day.maghrib, day.isha).forEach {
            Text(it, style = timeStyle, color = textColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }
    }
}
