package com.tuttoposto.prayertimes.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuttoposto.prayertimes.data.models.PrayerCountdown
import com.tuttoposto.prayertimes.data.models.PrayerTimeline
import com.tuttoposto.prayertimes.ui.theme.PrayerTimesColors
import com.tuttoposto.prayertimes.ui.theme.TabularNumbers
import com.tuttoposto.prayertimes.ui.viewmodels.PrayerStatus
import com.tuttoposto.prayertimes.ui.viewmodels.PrayerTimeDisplay
import com.tuttoposto.prayertimes.ui.viewmodels.PrayerTimesUiState
import com.tuttoposto.prayertimes.ui.viewmodels.PrayerTimesViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Today: a live countdown to the next prayer (or to the end of the current one), followed by
 * today's windows. Pull down anywhere to refresh from the network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(viewModel: PrayerTimesViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    // Coming back to the app restores alarms and the widget tick from saved times.
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::manualRefresh,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            PrayerTimesUiState.Loading -> CenteredMessage {
                CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 3.dp)
            }
            PrayerTimesUiState.NoData -> NoDataContent(isRefreshing, viewModel::manualRefresh)
            is PrayerTimesUiState.Success -> TodayContent(state, viewModel)
        }
    }
}

@Composable
private fun TodayContent(state: PrayerTimesUiState.Success, viewModel: PrayerTimesViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") { TodayHeader(state) }
        state.countdown?.let { countdown ->
            item(key = "hero") {
                val now by viewModel.clock.collectAsStateWithLifecycle()
                CountdownCard(countdown, now)
            }
        }
        state.fallbackMessage?.let { message ->
            item(key = "fallback") { Banner(Icons.Outlined.CloudOff, message) }
        }
        state.asrNote?.let { note ->
            item(key = "asr") { Banner(Icons.Outlined.Info, note) }
        }
        item(key = "list") { PrayerList(state.prayers) }
        item(key = "footer") {
            Text(
                text = "Saved through ${state.savedThrough} · Downloaded ${state.downloaded}\nPull down to refresh",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun TodayHeader(state: PrayerTimesUiState.Success) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(
            text = state.dateLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        if (!state.hijriDate.isNullOrEmpty()) {
            Text(
                text = state.hijriDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Icon(
                Icons.Outlined.LocationOn, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = state.locationName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CountdownCard(countdown: PrayerCountdown, now: Long) {
    val ends = countdown.phase == PrayerCountdown.Phase.ENDS
    val time = Instant.ofEpochMilli(countdown.targetMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    val remaining = PrayerTimeline.formatSeconds(countdown.remainingMillis(now))
    val caption = when {
        ends && countdown.prayerName == "Fajr" -> "left · sunrise at $time"
        ends -> "left · ends at $time"
        else -> "until it begins at $time"
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (ends) "NOW" else "NEXT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                if (countdown.isFallback) {
                    Spacer(Modifier.width(8.dp))
                    Text("≈ saved times", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                text = countdown.prayerName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = remaining,
                style = MaterialTheme.typography.displayLarge.merge(TabularNumbers).copy(fontSize = 56.sp, lineHeight = 64.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics {
                    contentDescription = PrayerTimeline.formatMinutes(countdown.remainingMillis(now))
                }
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { countdown.progress(now) },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
    }
}

@Composable
private fun PrayerList(prayers: List<PrayerTimeDisplay>) {
    val next = prayers.firstOrNull { it.status == PrayerStatus.UPCOMING }?.name
        .takeIf { prayers.none { it.status == PrayerStatus.CURRENT } }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            prayers.forEachIndexed { index, prayer ->
                PrayerRow(prayer, isNext = prayer.name == next)
                if (index < prayers.lastIndex) {
                    HorizontalDivider(
                        color = PrayerTimesColors.divider,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrayerRow(prayer: PrayerTimeDisplay, isNext: Boolean) {
    val current = prayer.status == PrayerStatus.CURRENT
    val nameColor by animateColorAsState(
        when (prayer.status) {
            PrayerStatus.CURRENT -> PrayerTimesColors.currentPrayer
            PrayerStatus.UPCOMING -> PrayerTimesColors.upcomingPrayer
            PrayerStatus.PAST -> PrayerTimesColors.pastPrayer
        },
        label = "prayerColor"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(
                when {
                    current -> PrayerTimesColors.currentPrayer
                    isNext -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = prayer.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            color = nameColor,
            modifier = Modifier.weight(1f)
        )
        if (current || isNext) {
            StatusPill(if (current) "Now" else "Next", highlighted = current)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = prayer.startTime,
            style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
            color = nameColor
        )
        Text(
            text = "  –  ${prayer.endTime}",
            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (prayer.status == PrayerStatus.PAST) 0.6f else 0.9f
            )
        )
    }
}

@Composable
private fun StatusPill(text: String, highlighted: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
internal fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    // Scrollable so pull-to-refresh still works from the empty state.
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        item { content() }
    }
}

@Composable
private fun NoDataContent(isRefreshing: Boolean, onRetry: () -> Unit) {
    CenteredMessage {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text("No prayer times yet", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "Allow location access and connect to the internet once. After that, times are saved for offline use.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry, enabled = !isRefreshing) { Text("Try again") }
        }
    }
}
