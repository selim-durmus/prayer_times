package com.tuttoposto.prayertimes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuttoposto.prayertimes.R
import com.tuttoposto.prayertimes.data.DivineName
import com.tuttoposto.prayertimes.data.ESMAUL_HUSNA
import com.tuttoposto.prayertimes.data.TESBIHAT
import com.tuttoposto.prayertimes.data.TesbihatPara
import com.tuttoposto.prayertimes.data.Vakit
import com.tuttoposto.prayertimes.data.meaningForGroup
import com.tuttoposto.prayertimes.ui.viewmodels.PrayerStatus
import com.tuttoposto.prayertimes.ui.viewmodels.PrayerTimesUiState
import com.tuttoposto.prayertimes.ui.viewmodels.PrayerTimesViewModel

// Instruction markers used in the generated tesbihat content (see TesbihatContent.kt)
private const val INSTR_OPEN = '⟦'
private const val INSTR_CLOSE = '⟧'

// Amiri (OFL) — a proper Naskh typeface for the Arabic script (see res/font, assets/amiri_OFL.txt)
private val AmiriFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.amiri_regular)
)

private enum class TesbihatMode { OKUNUS, ARAPCA }

/** A logical tesbihat section: a narration lead-in, its recited lines, and an optional meaning. */
private data class TGroup(
    val instruction: String?,
    val lines: List<TesbihatPara>,
    val meaning: String?
)

/**
 * Tasbihat screen - post-prayer dhikr (namaz tesbihatı) and the 99 names (Esmaül Hüsna).
 *
 * Design:
 * - Auto-selects the current prayer (vakit); a chip row lets the user switch manually.
 * - Per-prayer text from herkul.app/tesbihat (TesbihatContent.kt), with an Okunuş / Arapça toggle.
 * - Content is split into logical groups; each group can show a Turkish meaning (TesbihatMeanings.kt),
 *   revealed either by the global "Anlam" toggle or by tapping an individual group.
 * - The complete 99 names of Allah below (collapsible).
 * - Read-only; keeps the screen awake while open.
 */
@Composable
fun TasbihatScreen(
    // Reuses the activity-scoped PrayerTimesViewModel instance (no extra network/location work)
    prayerTimesViewModel: PrayerTimesViewModel = viewModel()
) {
    val uiState by prayerTimesViewModel.uiState.collectAsState()

    val autoVakit = remember(uiState) { currentVakit(uiState) }
    var userSelectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedVakit = userSelectedName?.let { runCatching { Vakit.valueOf(it) }.getOrNull() }
        ?: autoVakit
        ?: Vakit.SABAH

    var mode by rememberSaveable { mutableStateOf(TesbihatMode.OKUNUS) }
    var anlamGlobal by rememberSaveable { mutableStateOf(false) }
    var esmaExpanded by rememberSaveable { mutableStateOf(false) }

    val paragraphs = when (mode) {
        TesbihatMode.OKUNUS -> TESBIHAT[selectedVakit]?.latin
        TesbihatMode.ARAPCA -> TESBIHAT[selectedVakit]?.arabic
    } ?: emptyList()

    // Group + resolve meanings once per vakit/mode (not on every recomposition).
    val groups = remember(selectedVakit, mode) { buildTesbihatGroups(paragraphs) }
    // Per-group meaning reveal; reset when the vakit or mode changes.
    val expanded = remember(selectedVakit, mode) { mutableStateMapOf<Int, Boolean>() }

    // Keep the screen awake while following the tesbihat (hands are busy right after prayer).
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Jump back to the top whenever the prayer or the Okunuş/Arapça mode changes.
    val listState = rememberLazyListState()
    LaunchedEffect(selectedVakit, mode) { listState.scrollToItem(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Tasbihat",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        VakitSelector(selected = selectedVakit, onSelect = { userSelectedName = it.name })

        Spacer(modifier = Modifier.height(10.dp))

        ModeToggle(mode = mode, onSelect = { mode = it })

        Spacer(modifier = Modifier.height(8.dp))

        AnlamToggle(enabled = anlamGlobal, onToggle = { anlamGlobal = !anlamGlobal })

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                SectionHeader("Namaz Tesbihatı — ${selectedVakit.displayTr}")
            }
            itemsIndexed(groups) { index, grp ->
                GroupCard(
                    group = grp,
                    arabic = mode == TesbihatMode.ARAPCA,
                    showMeaning = anlamGlobal || expanded[index] == true,
                    onToggle = { if (grp.meaning != null) expanded[index] = !(expanded[index] ?: false) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SourceNote()
                Spacer(modifier = Modifier.height(16.dp))
                ExpandableSectionHeader(
                    title = "Esmaül Hüsna (99 İsim)",
                    expanded = esmaExpanded,
                    onToggle = { esmaExpanded = !esmaExpanded }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (esmaExpanded) {
                items(ESMAUL_HUSNA) { name ->
                    NameCard(name)
                }
            }
        }
    }
}

/** Determine the current prayer's vakit from the prayer-times state, for auto-selection. */
private fun currentVakit(state: PrayerTimesUiState): Vakit? {
    val success = state as? PrayerTimesUiState.Success ?: return null
    val current = success.prayers.firstOrNull { it.status == PrayerStatus.CURRENT }
    val target = current ?: success.prayers.lastOrNull { it.status == PrayerStatus.PAST }
    return Vakit.fromPrayerName(target?.name)
}

/** Split flat paragraphs into logical groups; narration (non-rtl instruction-only) lines start a group. */
private fun buildTesbihatGroups(paras: List<TesbihatPara>): List<TGroup> {
    data class Acc(val instruction: String?, val lines: MutableList<TesbihatPara>)
    val acc = mutableListOf<Acc>()
    for (p in paras) {
        if (isInstructionOnly(p.text) && !p.rtl) {
            acc.add(Acc(stripMarkers(p.text).trim(), mutableListOf()))
        } else {
            if (acc.isEmpty()) acc.add(Acc(null, mutableListOf()))
            acc.last().lines.add(p)
        }
    }
    return acc.map {
        val joined = it.lines.joinToString(" ") { l -> stripMarkers(l.text) }
        TGroup(it.instruction, it.lines, meaningForGroup(it.instruction, joined))
    }
}

@Composable
private fun GroupCard(
    group: TGroup,
    arabic: Boolean,
    showMeaning: Boolean,
    onToggle: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = group.meaning != null, onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            group.instruction?.let { instr ->
                Text(
                    text = instr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = muted,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            group.lines.forEach { line ->
                val annotated = annotateTesbihat(line.text, accent, textColor)
                if (arabic && line.rtl) {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = AmiriFont,
                        color = textColor,
                        textAlign = if (line.center) TextAlign.Center else TextAlign.End,
                        lineHeight = 44.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        fontWeight = FontWeight.Medium,
                        textAlign = if (line.center) TextAlign.Center else TextAlign.Start,
                        lineHeight = 28.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (line.center) 0.dp else 12.dp)
                    )
                }
            }

            if (group.meaning != null && showMeaning) {
                MeaningBlock(group.meaning)
            }
        }
    }
}

@Composable
private fun MeaningBlock(meaning: String) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.07f))
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent.copy(alpha = 0.5f))
        )
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = "Anlamı",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = meaning,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

/** True when the whole paragraph is narration/guidance (everything sits inside ⟦ ⟧ markers). */
private fun isInstructionOnly(text: String): Boolean {
    val builder = StringBuilder()
    var i = 0
    while (i < text.length) {
        val open = text.indexOf(INSTR_OPEN, i)
        if (open == -1) {
            builder.append(text, i, text.length)
            break
        }
        builder.append(text, i, open)
        val close = text.indexOf(INSTR_CLOSE, open + 1)
        i = if (close == -1) text.length else close + 1
    }
    return builder.isBlank()
}

private fun stripMarkers(text: String): String =
    text.replace(INSTR_OPEN.toString(), "").replace(INSTR_CLOSE.toString(), "")

/** Build an AnnotatedString, coloring instruction/narration segments (marked with ⟦ ⟧). */
@Composable
private fun annotateTesbihat(
    text: String,
    instructionColor: Color,
    textColor: Color
): AnnotatedString = buildAnnotatedString {
    val normal = SpanStyle(color = textColor)
    val instr = SpanStyle(color = instructionColor, fontWeight = FontWeight.Medium)
    var i = 0
    while (i < text.length) {
        val open = text.indexOf(INSTR_OPEN, i)
        if (open == -1) {
            withStyle(normal) { append(text.substring(i)) }
            break
        }
        if (open > i) {
            withStyle(normal) { append(text.substring(i, open)) }
        }
        val close = text.indexOf(INSTR_CLOSE, open + 1)
        if (close == -1) {
            withStyle(instr) { append(text.substring(open + 1)) }
            break
        }
        withStyle(instr) { append(text.substring(open + 1, close)) }
        i = close + 1
    }
}

@Composable
private fun VakitSelector(selected: Vakit, onSelect: (Vakit) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Vakit.entries.forEach { vakit ->
            FilterChip(
                selected = vakit == selected,
                onClick = { onSelect(vakit) },
                label = {
                    Text(
                        text = vakit.displayTr,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun ModeToggle(mode: TesbihatMode, onSelect: (TesbihatMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ModeChip("Okunuş", mode == TesbihatMode.OKUNUS, Modifier.weight(1f)) {
            onSelect(TesbihatMode.OKUNUS)
        }
        ModeChip("Arapça", mode == TesbihatMode.ARAPCA, Modifier.weight(1f)) {
            onSelect(TesbihatMode.ARAPCA)
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun AnlamToggle(enabled: Boolean, onToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = enabled,
            onClick = onToggle,
            label = {
                Text(
                    text = if (enabled) "Anlam: Açık" else "Anlam: Kapalı",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        if (!enabled) {
            Text(
                text = "İpucu: bir bölüme dokunarak anlamını tek tek açabilirsiniz.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SourceNote() {
    Text(
        text = "Kaynak: herkul.app/tesbihat",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun ExpandableSectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Daralt" else "Genişlet",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun NameCard(name: DivineName) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.order.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.transliteration,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = name.meaning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Text(
                text = name.arabic,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = AmiriFont,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
        }
    }
}
