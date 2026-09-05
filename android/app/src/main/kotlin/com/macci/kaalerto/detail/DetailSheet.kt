package com.macci.kaalerto.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.FeatureSummary
import com.macci.kaalerto.data.severityTextFor
import com.macci.kaalerto.ui.theme.SeverityColors
import kotlinx.coroutines.launch

private fun colorFor(severity: String): Color =
    Color(android.graphics.Color.parseColor(SeverityColors.forSeverity(severity)))

/** Every report gives its author name and role, per the architecture guardrail that the name rides in the event, never a lookup. */
private fun sourceLabel(event: Event): String = "${event.authorName} · ${roleLabel(event.authorRole)}"

private fun roleLabel(role: String): String = when (role) {
    "official" -> "Barangay official"
    "responder" -> "Responder"
    else -> "Resident"
}

private fun originLabel(event: Event): String = when (event.origin) {
    "mesh" -> "Mesh" + if (event.hopCount > 0) " · ${event.hopCount} hops" else ""
    "sms" -> "SMS"
    "server" -> "Server"
    "seed" -> "Seed data"
    else -> "Direkta" // local — authored on this device
}

private fun ageLabel(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "Ngayon lang"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60}h ${minutes % 60}m ago"
    }
}

private fun bucketLabel(bucket: String): String = when (bucket) {
    "official" -> "Official"
    "confirmed" -> "Confirmed"
    "likely" -> "Likely"
    else -> "Unverified"
}

/** Slug featureRefs (geohash cells) get a generic label; seed data's named-street slugs get prettified. */
private fun featureDisplayName(featureRef: String): String =
    if (featureRef.contains('-')) {
        featureRef.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    } else {
        "Ulat sa lugar na ito"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    summary: FeatureSummary,
    onDismiss: () -> Unit,
    onCheckInPerson: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDisputeDialog by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val latestReport = summary.events.firstOrNull { it.type == "flood_report" }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(featureDisplayName(summary.featureRef), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (summary.isStale) {
                        Text("Naka-decay · ${ageLabel(System.currentTimeMillis() - summary.lastEventMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SeverityBadge(summary.severity)
            }

            Spacer(Modifier.height(12.dp))

            if (latestReport?.waterLevel != null && !summary.isConflicted) {
                val (fil, en) = severityTextFor(summary.severity)
                InfoCard {
                    Text(fil, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
            }

            val anchorEvent = latestReport ?: summary.events.first()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard(modifier = Modifier.weight(1f)) {
                    Text("HULING ULAT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ageLabel(System.currentTimeMillis() - summary.lastEventMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                InfoCard(modifier = Modifier.weight(1f)) {
                    Text("PAANO DUMATING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(originLabel(anchorEvent), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (summary.isConflicted) {
                ConflictSection(summary)
                Spacer(Modifier.height(16.dp))
                Text("Malapit ka ba? Tulungan mo kaming i-check.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onCheckInPerson(summary.lat, summary.lon) }, modifier = Modifier.fillMaxWidth()) {
                    Text("I-check ko ngayon")
                }
            } else {
                ConfidenceSection(summary)
                Spacer(Modifier.height(16.dp))
                Text("Nandiyan ka ba ngayon?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (submitting) return@Button
                            submitting = true
                            scope.launch {
                                submitConfirm(context, summary.featureRef, summary.severity)
                                submitting = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Tama") }
                    OutlinedButton(onClick = { showDisputeDialog = true }, modifier = Modifier.weight(1f)) {
                        Text("Iba na")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("ULAT (${summary.events.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            summary.events.take(5).forEach { event -> EventHistoryRow(event) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDisputeDialog) {
        DisputeReasonDialog(
            onSelect = { reason ->
                showDisputeDialog = false
                submitting = true
                scope.launch {
                    submitDispute(context, summary.featureRef, summary.severity, reason)
                    submitting = false
                }
            },
            onDismiss = { showDisputeDialog = false },
        )
    }
}

@Composable
private fun SeverityBadge(severity: String) {
    Box(
        modifier = Modifier
            .background(colorFor(severity), RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(severity, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

@Composable
private fun ConfidenceSection(summary: FeatureSummary) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(bucketLabel(summary.bucket), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { summary.confidence.toFloat() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            "${summary.confirmCount} nag-confirm · ${summary.disputeCount} nag-dispute",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConflictSection(summary: FeatureSummary) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("⚠ Magkaibang ulat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.height(4.dp))
            Text(
                "Ituring na hindi madaanan hangga't walang nakakumpirma.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Conflicting reports — treat as impassable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("ANG MGA ULAT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    // The two positions that actually disagree, not the whole history — one from
    // each side of the split, most recent first, matching the artboard's framing.
    val reports = summary.events.filter { it.severity != null }
    val dangerous = reports.filter { (it.severity == "S2" || it.severity == "S3") }.maxByOrNull { it.timestampMs }
    val safe = reports.filter { (it.severity == "S0" || it.severity == "S1") }.maxByOrNull { it.timestampMs }
    listOfNotNull(dangerous, safe).sortedByDescending { it.timestampMs }.forEach { EventHistoryRow(it) }
    Spacer(Modifier.height(12.dp))
    Text(
        "Hindi namin pinipili kung sino ang tama, at hindi rin namin pinagsasama. Ipinapakita namin ang hindi pagkakasundo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EventHistoryRow(event: Event) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 36.dp)
                .background(event.severity?.let { colorFor(it) } ?: Color.Gray, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            val label = when (event.type) {
                "confirm" -> "Kumpirmasyon · ${sourceLabel(event)}"
                "dispute" -> "Dispute (${event.disputeReason ?: "?"}) · ${sourceLabel(event)}"
                else -> sourceLabel(event)
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            val detail = listOfNotNull(event.waterLevel, event.note).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(ageLabel(System.currentTimeMillis() - event.timestampMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DisputeReasonDialog(onSelect: (DisputeReason) -> Unit, onDismiss: () -> Unit) {
    val reasons = listOf(
        DisputeReason.CLEARED_NOW to ("Humupa na" to "Cleared now"),
        DisputeReason.WORSE to ("Lumala" to "Worse now"),
        DisputeReason.SHALLOWER to ("Bumaba" to "Shallower now"),
        DisputeReason.WRONG_LOCATION to ("Maling lokasyon" to "Wrong location"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ano ang nangyari?") },
        text = {
            Column {
                reasons.forEach { (reason, copy) ->
                    val (fil, en) = copy
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(reason) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(fil, fontWeight = FontWeight.SemiBold)
                        Text(en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kanselahin") }
        },
    )
}
