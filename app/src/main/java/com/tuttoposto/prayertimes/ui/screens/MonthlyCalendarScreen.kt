package com.tuttoposto.prayertimes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuttoposto.prayertimes.ui.theme.PrayerTimesColors
import com.tuttoposto.prayertimes.ui.viewmodels.DayPrayerTimes
import com.tuttoposto.prayertimes.ui.viewmodels.MonthlyCalendarUiState
import com.tuttoposto.prayertimes.ui.viewmodels.MonthlyCalendarViewModel
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthlyCalendarScreen(
    viewModel: MonthlyCalendarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()

    LaunchedEffect(currentMonth) {
        viewModel.loadMonth()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Monthly Calendar",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        MonthNavigator(
            currentMonth = currentMonth,
            onPrevious = viewModel::goToPreviousMonth,
            onNext = viewModel::goToNextMonth
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Column header
        TimesHeader()

        Spacer(modifier = Modifier.height(4.dp))

        when (val state = uiState) {
            is MonthlyCalendarUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            is MonthlyCalendarUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is MonthlyCalendarUiState.Success -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(state.days) { day ->
                        DayRow(day)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthNavigator(
    currentMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )

        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TimesHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val headerStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        val headerColor = MaterialTheme.colorScheme.onSurfaceVariant

        Text("Day", style = headerStyle, color = headerColor, modifier = Modifier.width(36.dp))
        Text("Fajr", style = headerStyle, color = headerColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text("Dhuhr", style = headerStyle, color = headerColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text("Asr", style = headerStyle, color = headerColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text("Mgrb", style = headerStyle, color = headerColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text("Isha", style = headerStyle, color = headerColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
    }

    HorizontalDivider(color = PrayerTimesColors.divider)
}

@Composable
private fun DayRow(day: DayPrayerTimes) {
    val bgColor = if (day.isToday) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        PrayerTimesColors.cardBackground
    }

    val textColor = if (day.isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val timeStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day number
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center
            )

            Text(day.fajr, style = timeStyle, color = textColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(day.dhuhr, style = timeStyle, color = textColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(day.asr, style = timeStyle, color = textColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(day.maghrib, style = timeStyle, color = textColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(day.isha, style = timeStyle, color = textColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }
    }
}
