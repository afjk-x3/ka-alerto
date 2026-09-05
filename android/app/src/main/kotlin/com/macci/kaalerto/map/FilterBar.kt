package com.macci.kaalerto.map

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.ui.theme.SeverityColors

/** BUILD_TASKS.md day 5: "severity toggles + recency chips". */
val ALL_SEVERITIES = listOf("S0", "S1", "S2", "S3")

enum class RecencyFilter(val label: String, val windowMillis: Long?) {
    ALL("Lahat", null),
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
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ALL_SEVERITIES.forEach { severity ->
            val color = Color(android.graphics.Color.parseColor(SeverityColors.forSeverity(severity)))
            FilterChip(
                selected = severity in selectedSeverities,
                onClick = { onToggleSeverity(severity) },
                label = { Text(severity) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.25f)),
            )
        }
        RecencyFilter.entries.forEach { option ->
            FilterChip(
                selected = recency == option,
                onClick = { onRecencyChange(option) },
                label = { Text(option.label) },
            )
        }
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
