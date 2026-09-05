package com.macci.kaalerto.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.data.severityTextFor
import com.macci.kaalerto.ui.theme.SeverityColors
import kotlinx.coroutines.launch

@Composable
fun ReportScreen(
    initialLat: Double,
    initialLon: Double,
    initialAccuracyMeters: Float?,
    onChangeLocation: () -> Unit,
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(ReportMode.BODY) }
    // An index into the current mode's 4 options, not an id: switching between Katawan
    // and Sasakyan keeps "how deep", since both scales are ordered shallow to deep.
    var selectedIndex by remember { mutableStateOf(0) }
    var severityOverride by remember { mutableStateOf<String?>(null) }
    var showOverrideDialog by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    val levels = levelsFor(mode)
    val selected = levels[selectedIndex]
    val derivedSeverity = severityOverride ?: selected.severity
    val (severityFil, severityEn) = severityTextFor(derivedSeverity)
    val severityColor = Color(android.graphics.Color.parseColor(SeverityColors.forSeverity(derivedSeverity)))

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Bumalik")
            }
            Column {
                Text("Gaano kalalim?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("How deep is the water?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Location card.
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "%.5f, %.5f".format(initialLat, initialLon),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val accuracyText = initialAccuracyMeters?.let { "GPS ±${it.toInt()} m" } ?: "Itinakda sa mapa"
                    Text(accuracyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onChangeLocation) { Text("Baguhin") }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Body / Vehicle mode tabs.
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            ModeTab("Katawan", selected = mode == ReportMode.BODY, modifier = Modifier.weight(1f)) {
                mode = ReportMode.BODY
                severityOverride = null
            }
            ModeTab("Sasakyan", selected = mode == ReportMode.VEHICLE, modifier = Modifier.weight(1f)) {
                mode = ReportMode.VEHICLE
                severityOverride = null
            }
        }

        Spacer(Modifier.height(16.dp))

        if (mode == ReportMode.BODY) {
            BodyIllustration(
                levelId = selected.id,
                waterColor = severityColor,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(16.dp))
        }

        // 4-option grid.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            levels.forEachIndexed { index, option ->
                LevelChip(
                    option = option,
                    isVehicleMode = mode == ReportMode.VEHICLE,
                    selected = index == selectedIndex,
                    modifier = Modifier.weight(1f),
                ) {
                    selectedIndex = index
                    severityOverride = null
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Derived severity — tappable to override (FR-2.1: "derive... automatically", BUILD_TASKS.md day 3: "allow override").
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { showOverrideDialog = true },
            color = severityColor,
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).border(2.dp, Color.White, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(derivedSeverity, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        if (severityOverride != null) "MANUAL NA SEVERITY" else "IRE-REPORT BILANG",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(severityFil, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(severityEn, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
        Text(
            if (severityOverride != null) "Manu-mano itong itinakda. Pindutin para baguhin." else "Awtomatiko itong nakuha sa lalim. Pindutin para baguhin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (submitting) return@Button
                submitting = true
                scope.launch {
                    submitReport(context, selected, derivedSeverity, initialLat, initialLon)
                    submitting = false
                    onSubmitted()
                }
            },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(if (submitting) "Ipinapadala…" else "Ipadala ang ulat")
        }

        Text(
            "Ise-save sa phone mo kahit walang signal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    if (showOverrideDialog) {
        SeverityOverrideDialog(
            current = derivedSeverity,
            onSelect = { severity ->
                severityOverride = severity
                showOverrideDialog = false
            },
            onDismiss = { showOverrideDialog = false },
        )
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 14.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun LevelChip(
    option: WaterLevelOption,
    isVehicleMode: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.aspectRatio(0.9f).clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isVehicleMode) {
                VehicleGlyph(
                    id = option.id,
                    tint = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                option.fil,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                option.en,
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = if (selected) MaterialTheme.colorScheme.surface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeverityOverrideDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Palitan ang severity") },
        text = {
            Column {
                listOf("S1", "S2", "S3").forEach { severity ->
                    val (fil, en) = severityTextFor(severity)
                    val color = Color(android.graphics.Color.parseColor(SeverityColors.forSeverity(severity)))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(severity) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(16.dp).background(color, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text("$severity — $fil", fontWeight = if (severity == current) FontWeight.Bold else FontWeight.Normal)
                            Text(en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kanselahin") }
        },
    )
}
