package com.macci.kaalerto.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import com.macci.kaalerto.ui.theme.SeverityColors

/** BUILD_TASKS.md day 5: "severity toggles + recency chips" — restyled per Map-Normal.dc.html's two-preset filter row. */
val ALL_SEVERITIES = listOf("S0", "S1", "S2", "S3")

/** The artboard's "Impassable" preset — anything that blocks at least some traffic. */
val IMPASSABLE_SEVERITIES = setOf("S2", "S3")

enum class RecencyFilter(val label: String, val windowMillis: Long?) {
    // Label deliberately not "Lahat" — that pill already sits two spots to the left in
    // the same row, and identical labels next to each other read as a rendering bug.
    ALL("Kailanman", null),
    LAST_HOUR("1h", 60 * 60_000L),
    LAST_3H("3h", 3 * 60 * 60_000L),
    LAST_DAY("24h", 24 * 60 * 60_000L),
}

@Composable
fun FilterBar(
    selectedSeverities: Set<String>,
    onToggleSeverity: (String) -> Unit,
    recency: RecencyFilter,
    onRecencyChange: (RecencyFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    val showingAll = selectedSeverities.containsAll(ALL_SEVERITIES)
    var recencyMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        FilterPill(
            label = "Lahat",
            selected = showingAll,
            swatchColor = null,
            onClick = { if (!showingAll) ALL_SEVERITIES.forEach { if (it !in selectedSeverities) onToggleSeverity(it) } },
        )
        FilterPill(
            label = "Impassable",
            selected = !showingAll && selectedSeverities == IMPASSABLE_SEVERITIES,
            swatchColor = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(SeverityColors.S3)),
            onClick = {
                ALL_SEVERITIES.forEach { severity ->
                    val shouldBeSelected = severity in IMPASSABLE_SEVERITIES
                    if (shouldBeSelected != (severity in selectedSeverities)) onToggleSeverity(severity)
                }
            },
        )

        Box {
            Row(
                modifier = Modifier
                    .border(androidx.compose.foundation.BorderStroke(1.dp, colors.border))
                    .clickable { recencyMenuOpen = true }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(recency.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(expanded = recencyMenuOpen, onDismissRequest = { recencyMenuOpen = false }) {
                RecencyFilter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onRecencyChange(option)
                            recencyMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    swatchColor: androidx.compose.ui.graphics.Color?,
    onClick: () -> Unit,
) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
            .border(androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else colors.border))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatchColor != null) {
            Box(modifier = Modifier.size(width = 12.dp, height = 4.dp).background(swatchColor))
            Spacer(Modifier.size(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A feature passes the filter bar if its severity is selected (conflicts always show — hiding a live disagreement is worse than a noisy map) and it's within the chosen recency window. */
fun passesFilter(
    severity: String,
    isConflicted: Boolean,
    lastEventMs: Long,
    selectedSeverities: Set<String>,
    recency: RecencyFilter,
    now: Long,
): Boolean {
    val severityOk = isConflicted || severity in selectedSeverities
    val recencyOk = recency.windowMillis == null || (now - lastEventMs) <= recency.windowMillis
    return severityOk && recencyOk
}
