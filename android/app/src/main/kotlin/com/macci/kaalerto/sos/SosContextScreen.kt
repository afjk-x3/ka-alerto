package com.macci.kaalerto.sos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * design/artboards/SOSContext.dc.html.
 *
 * The single most important thing on this screen is the sentence at the top: **the
 * request is already going out.** FR-4.3 and BUILD_TASKS.md day 8's "context screen
 * **does not block** transmission" — the SOS event was written before this composable
 * existed, and every answer here is an amendment that follows it. Both exits ("Tapos
 * na" and "Laktawan") reach the same place; neither is a send button, because there is
 * nothing left to send.
 *
 * Each answer is committed the moment it is tapped rather than batched behind the
 * footer, so a phone that dies mid-form has still delivered what was answered.
 */
@Composable
fun SosContextScreen(
    snapshot: SosSnapshot,
    elapsedLabel: String,
    onAmend: (SosContext) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(snapshot.context) }

    fun update(next: SosContext) {
        draft = next
        onAmend(next)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SosColors.Background),
    ) {
        SosLiveBanner(
            title = "Ipinapadala na ang SOS mo",
            subtitle = "$elapsedLabel · %.4f, %.4f".format(snapshot.lat, snapshot.lon),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SosColors.Surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            BroadcastGlyph(SosColors.Mesh, Modifier.size(26.dp))
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    "Padala na ang lokasyon mo. Opsyonal lang ito.",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = SosColors.PrimaryText,
                )
                Text(
                    "Your location is already going out. Answering only adds detail — it never delays the request.",
                    fontSize = 13.sp,
                    color = SosColors.SecondaryText,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Question("Ilan kayo diyan?") {
                OptionGrid(
                    options = SosContext.PEOPLE_OPTIONS,
                    selected = setOfNotNull(draft.people),
                    onTap = { update(draft.copy(people = it)) },
                )
                Spacer(Modifier.size(7.dp))
                OptionRow(
                    options = SosContext.COMPANION_OPTIONS,
                    selected = draft.companions.toSet(),
                    onTap = { tapped ->
                        val next = if (tapped in draft.companions) draft.companions - tapped else draft.companions + tapped
                        update(draft.copy(companions = next))
                    },
                )
            }

            Question("May kailangan bang medikal?") {
                OptionRow(
                    options = SosContext.MEDICAL_OPTIONS,
                    selected = draft.medical.toSet(),
                    onTap = { update(draft.copy(medical = SosContext.toggleMedical(draft.medical, it))) },
                    // Medical need is the field that changes who gets dispatched, so it
                    // is the one the artboard styles in red rather than plain white.
                    accent = SosColors.CriticalOnDark,
                    accentBackground = SosColors.CriticalSurface,
                    accentText = SosColors.CriticalSoft,
                )
            }

            Question("Gaano kataas ang tubig?") {
                OptionGrid(
                    options = SosContext.WATER_OPTIONS,
                    selected = setOfNotNull(draft.water),
                    onTap = { update(draft.copy(water = it)) },
                    selectedBackground = SosColors.Critical,
                    selectedText = SosColors.CardBackground,
                )
                Spacer(Modifier.size(7.dp))
                OptionRow(
                    options = SosContext.TREND_OPTIONS,
                    selected = setOfNotNull(draft.trend),
                    onTap = { update(draft.copy(trend = it)) },
                )
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .background(SosColors.Surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(width = 3.dp, height = 34.dp).background(SosColors.Mesh))
            Spacer(Modifier.size(11.dp))
            Text(
                "Bawat sagot ay sumusunod agad sa naunang padala — hindi na inuulit ang lokasyon.",
                fontSize = 13.sp,
                color = SosColors.SecondaryText,
            )
        }

        Box(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                .fillMaxWidth()
                .height(56.dp)
                .background(SosColors.PrimaryText)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text("Tapos na — ipakita ang status", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SosColors.Background)
        }
        Box(
            modifier = Modifier
                .padding(bottom = 20.dp)
                .fillMaxWidth()
                .height(40.dp)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Laktawan — hayaan lang itong tumakbo",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = SosColors.HoldSecondaryText,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Composable
private fun Question(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SosColors.PrimaryText)
        content()
    }
}

/** Four equal columns, as the artboard's `repeat(4, minmax(0, 1fr))` grids. */
@Composable
private fun OptionGrid(
    options: List<String>,
    selected: Set<String>,
    onTap: (String) -> Unit,
    selectedBackground: Color = SosColors.PrimaryText,
    selectedText: Color = SosColors.Background,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { option ->
            val isSelected = option in selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .background(if (isSelected) selectedBackground else SosColors.Surface)
                    .border(1.5.dp, if (isSelected) selectedBackground else SosColors.Border)
                    .clickable { onTap(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) selectedText else SosColors.SecondaryText,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Content-width chips that wrap, for the multi-select answers. */
@Composable
private fun OptionRow(
    options: List<String>,
    selected: Set<String>,
    onTap: (String) -> Unit,
    accent: Color? = null,
    accentBackground: Color = SosColors.Surface,
    accentText: Color = SosColors.PrimaryText,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { option ->
            val isSelected = option in selected
            val background = when {
                isSelected && accent != null -> accentBackground
                isSelected -> SosColors.PrimaryText
                else -> SosColors.Surface
            }
            val border = when {
                isSelected && accent != null -> accent
                isSelected -> SosColors.PrimaryText
                else -> SosColors.Border
            }
            val text = when {
                isSelected && accent != null -> accentText
                isSelected -> SosColors.Background
                else -> SosColors.SecondaryText
            }
            Box(
                modifier = Modifier
                    .background(background)
                    .border(1.5.dp, border)
                    .clickable { onTap(option) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    option,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = text,
                )
            }
        }
    }
}
