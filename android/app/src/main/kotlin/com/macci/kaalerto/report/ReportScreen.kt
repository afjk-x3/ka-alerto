package com.macci.kaalerto.report

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.data.severityTextFor
import com.macci.kaalerto.net.rememberIsOnline
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
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
    val colors = LocalKaAlertoColors.current
    val isOnline by rememberIsOnline()

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
    // S1's amber is too light for white text to sit on legibly — Report-Normal.dc.html
    // itself gives S1 dark text and S2/S3 white, rather than one colour for all three.
    val onSeverityColor = if (derivedSeverity == "S1") Color(0xFF14171A) else Color.White

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

        // Location card. Shows raw coordinates, not a resolved street name like the
        // artboard's "Sampaloc St" — that needs reverse geocoding against the bundled
        // OSM route data, a real feature no build day has scheduled yet, not a UI change.
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            border = BorderStroke(1.dp, colors.border),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = colors.safeFg)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "%.5f, %.5f".format(initialLat, initialLon),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val accuracyText = initialAccuracyMeters?.let { "GPS ±${it.toInt()} m" } ?: "Itinakda sa mapa"
                    Text(
                        accuracyText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Baguhin",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(onClick = onChangeLocation),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Body / Vehicle mode tabs — one shared border around both tabs, per
        // Report-Normal.dc.html, not a border per tab (which reads as a seam between them).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground)),
        ) {
            ModeTab(
                label = "Katawan",
                selected = mode == ReportMode.BODY,
                modifier = Modifier.weight(1f),
                icon = { tint -> PersonGlyph(tint = tint, modifier = Modifier.size(18.dp)) },
            ) {
                mode = ReportMode.BODY
                severityOverride = null
            }
            ModeTab(
                label = "Sasakyan",
                selected = mode == ReportMode.VEHICLE,
                modifier = Modifier.weight(1f),
                icon = { tint -> VehicleGlyph(id = "car", tint = tint, modifier = Modifier.size(18.dp)) },
            ) {
                mode = ReportMode.VEHICLE
                severityOverride = null
            }
        }

        Spacer(Modifier.height(16.dp))

        if (mode == ReportMode.BODY) {
            BodyIllustration(
                levelId = selected.id,
                waterColor = severityColor,
                modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 64.dp),
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
                    modifier = Modifier.size(44.dp).border(2.dp, onSeverityColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(derivedSeverity, color = onSeverityColor, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        if (severityOverride != null) "MANUAL NA SEVERITY" else "IRE-REPORT BILANG",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSeverityColor.copy(alpha = 0.75f),
                    )
                    Text(severityFil, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onSeverityColor)
                    Text(severityEn, style = MaterialTheme.typography.bodySmall, color = onSeverityColor.copy(alpha = 0.85f))
                }
            }
        }
        Text(
            if (severityOverride != null) "Manu-mano itong itinakda. Pindutin para baguhin." else "Awtomatiko itong nakuha sa lalim. Pindutin para baguhin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Report-Normal.dc.html also has an optional-photo row here (FR-2.6). Left out
        // deliberately: photo capture (camera intent, local storage, hash-only relay)
        // isn't built by any day yet, and a tappable row that does nothing would
        // misrepresent what the app can do.

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(enabled = !submitting) {
                    submitting = true
                    scope.launch {
                        submitReport(context, selected, derivedSeverity, initialLat, initialLon)
                        submitting = false
                        onSubmitted()
                    }
                },
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (submitting) "Ipinapadala…" else "Ipadala ang ulat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShieldGlyph(
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.size(7.dp))
            Text(
                if (isOnline) "Ise-save sa phone mo kahit walang signal" else "Walang signal — ise-save muna sa phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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
private fun ModeTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable (tint: Color) -> Unit,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(tint)
        Spacer(Modifier.size(8.dp))
        Text(
            label,
            textAlign = TextAlign.Center,
            color = tint,
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                textAlign = TextAlign.Center,
                color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                option.en,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
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
