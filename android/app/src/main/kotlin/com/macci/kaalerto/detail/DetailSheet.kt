package com.macci.kaalerto.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.FeatureSummary
import com.macci.kaalerto.data.severityTextFor
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.report.BODY_LEVELS
import com.macci.kaalerto.report.BodyIllustration
import com.macci.kaalerto.report.PhotoStore
import com.macci.kaalerto.report.VEHICLE_LEVELS
import com.macci.kaalerto.report.VehicleGlyph
import com.macci.kaalerto.report.WaterLevelOption
import com.macci.kaalerto.report.decodeReportPhotoPayload
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import com.macci.kaalerto.ui.theme.SeverityColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private fun colorFor(severity: String): Color =
    Color(android.graphics.Color.parseColor(SeverityColors.forSeverity(severity)))

/** Every report gives its author name and role, per the architecture guardrail that the name rides in the event, never a lookup. */
@Composable
private fun sourceLabel(event: Event): String = "${event.authorName} · ${detailRoleLabel(event.authorRole)}"

@Composable
private fun detailRoleLabel(role: String): String = when (role) {
    "official" -> tr("Kagawad", "Barangay official")
    "responder" -> "Responder"
    else -> tr("Residente", "Resident")
}

@Composable
private fun originLabel(event: Event): String = when (event.origin) {
    "mesh" -> "Mesh"
    "sms" -> "SMS"
    "server" -> tr("Server", "Server")
    "seed" -> tr("Seed data", "Seed data")
    else -> tr("Direkta", "Direct") // local — authored on this device
}

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

// Was English-only in both languages before this pass — CLAUDE.md's own known-issues
// list flagged "18 min ago" as a stray English string on an otherwise-Filipino screen.
@Composable
private fun ageLabel(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> tr("Ngayon lang", "Just now")
        minutes < 60 -> tr("$minutes min ang nakalipas", "$minutes min ago")
        else -> tr("${minutes / 60}h ${minutes % 60}m ang nakalipas", "${minutes / 60}h ${minutes % 60}m ago")
    }
}

// Also flagged in CLAUDE.md's known issues: these were English-only regardless of
// the rest of the screen being Filipino.
@Composable
private fun bucketLabel(bucket: String): String = when (bucket) {
    "official" -> tr("Opisyal", "Official")
    "confirmed" -> tr("Kumpirmado", "Confirmed")
    "likely" -> tr("Malamang", "Likely")
    else -> tr("Hindi pa beripikado", "Unverified")
}

/** Slug featureRefs (geohash cells) get a generic label; seed data's named-street slugs get prettified. */
@Composable
private fun featureDisplayName(featureRef: String): String =
    if (featureRef.contains('-')) {
        featureRef.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    } else {
        tr("Ulat sa lugar na ito", "Report at this spot")
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    summary: FeatureSummary,
    onDismiss: () -> Unit,
    onCheckInPerson: (lat: Double, lon: Double) -> Unit,
    /** Non-null only for a barangay official — day 10's ruling screen for this feature. */
    onOfficialStatus: (() -> Unit)? = null,
    /**
     * Non-null when this device has not registered. A confirm or a dispute is an
     * authored event carrying this person's name, so it goes through PRD §9's gate
     * first; the sheet reopens on the way back rather than dropping what they were doing.
     */
    onNeedsRegistration: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalKaAlertoColors.current
    var showDisputeDialog by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val latestReport = summary.events.firstOrNull { it.type == "flood_report" }

    // Lighter than the default scrim: the map behind stays dimly visible rather than
    // fully hidden, evoking Detail*.dc.html's "map peeking through" header without a
    // second, fake, unsynced map render — this is the real map, at its real position.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        scrimColor = Color.Black.copy(alpha = 0.4f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(featureDisplayName(summary.featureRef), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (summary.isStale) {
                        Text(
                            tr("Naka-decay", "Decayed") + " · ${ageLabel(System.currentTimeMillis() - summary.lastEventMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SeverityBadge(summary.severity)
            }

            Spacer(Modifier.height(12.dp))

            if (latestReport?.waterLevel != null && !summary.isConflicted) {
                ReadingCard(waterLevelId = latestReport.waterLevel, severity = summary.severity)
                Spacer(Modifier.height(12.dp))
            }

            decodeReportPhotoPayload(latestReport?.payload)?.let { photo ->
                PhotoStatusCard(photoHash = photo.photoHash)
                Spacer(Modifier.height(12.dp))
            }

            val anchorEvent = latestReport ?: summary.events.first()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard(modifier = Modifier.weight(1f)) {
                    Text(tr("HULING ULAT", "LATEST REPORT"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ageLabel(System.currentTimeMillis() - summary.lastEventMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        timeFormat.format(summary.lastEventMs),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InfoCard(modifier = Modifier.weight(1f)) {
                    Text(tr("PAANO DUMATING", "HOW IT ARRIVED"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (anchorEvent.origin == "mesh") {
                            MeshIcon(tint = colors.safeFg, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.size(6.dp))
                        }
                        Text(originLabel(anchorEvent), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (anchorEvent.origin == "mesh" && anchorEvent.hopCount > 0) {
                        Text("${anchorEvent.hopCount} hops", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (summary.officialSeverity != null) {
                OfficialBanner(summary)
                Spacer(Modifier.height(12.dp))
            }

            if (summary.isConflicted) {
                ConflictSection(summary)
                Spacer(Modifier.height(16.dp))
                Text(
                    tr("Malapit ka ba? Tulungan mo kaming i-check.", "Are you nearby? Help us check."),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                ActionBar(
                    label = tr("I-check ko ngayon", "I'll check it now"),
                    icon = { tint -> MagnifierIcon(tint, Modifier.size(20.dp)) },
                    onClick = { onCheckInPerson(summary.lat, summary.lon) },
                    background = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                ConfidenceSection(summary)
                Spacer(Modifier.height(16.dp))
                Text(
                    tr("Nandiyan ka ba ngayon?", "Are you there right now?"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionBar(
                        // Both actions await a GPS fix (ConfirmDisputeSubmit), which can
                        // take seconds. `submitting` used to only block a double-tap; a
                        // button that swallows the press and changes nothing reads as
                        // broken, which is the same failure the SOS button had.
                        label = if (submitting) tr("Kinukuha…", "Getting…") else tr("Tama", "Correct"),
                        icon = { tint -> CheckIcon(tint, Modifier.size(18.dp)) },
                        onClick = {
                            if (onNeedsRegistration != null) return@ActionBar onNeedsRegistration()
                            if (submitting) return@ActionBar
                            submitting = true
                            scope.launch {
                                submitConfirm(context, summary.featureRef, summary.severity)
                                submitting = false
                            }
                        },
                        background = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    ActionBar(
                        label = if (submitting) tr("Kinukuha…", "Getting…") else tr("Iba na", "It's different"),
                        icon = { tint -> XIcon(tint, Modifier.size(18.dp)) },
                        onClick = {
                            if (onNeedsRegistration != null) onNeedsRegistration()
                            else showDisputeDialog = true
                        },
                        background = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        border = BorderStroke(1.5.dp, colors.borderEmphasis),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (onOfficialStatus != null) {
                Spacer(Modifier.height(10.dp))
                ActionBar(
                    label = tr("Mag-post ng opisyal na status", "Post an official status"),
                    icon = { tint -> CheckIcon(tint, Modifier.size(18.dp)) },
                    onClick = onOfficialStatus,
                    background = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onBackground),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(tr("ULAT (${summary.events.size})", "REPORTS (${summary.events.size})"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** A plain rectangular action bar (not Material's default pill `Button`) — matches the black full-width bars every artboard uses for primary actions. */
@Composable
private fun ActionBar(
    label: String,
    icon: @Composable (tint: Color) -> Unit,
    onClick: () -> Unit,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = background,
        border = border,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon(contentColor)
            Spacer(Modifier.size(9.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
        }
    }
}

/**
 * Seed fixtures store the Filipino word here ("bukong-bukong"); live reports
 * (report/ReportSubmit.kt) store the option's English id ("ankle") — both are the same
 * field, just written by two different sources that never agreed on a format. Resolving
 * either to the same [WaterLevelOption] is cheaper and safer than picking one fixture
 * format to be "correct" and leaving the other undrawn.
 */
private fun resolveLevelOption(waterLevelId: String): WaterLevelOption? =
    (BODY_LEVELS + VEHICLE_LEVELS).find { it.id == waterLevelId || it.fil.equals(waterLevelId, ignoreCase = true) }

/** The small figure/vehicle icon next to the water-level reading — reuses the same drawing as the report form so a resident recognizes it as "the depth someone picked," not a new picture. */
@Composable
private fun ReadingCard(waterLevelId: String, severity: String) {
    val severityColor = colorFor(severity)
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val option = resolveLevelOption(waterLevelId)
            val bodyOption = option?.takeIf { it in BODY_LEVELS }
            val vehicleOption = option?.takeIf { it in VEHICLE_LEVELS }
            when {
                bodyOption != null -> BodyIllustration(
                    levelId = bodyOption.id,
                    waterColor = severityColor,
                    animate = false,
                    modifier = Modifier.size(width = 42.dp, height = 58.dp),
                )
                vehicleOption != null -> VehicleGlyph(
                    id = vehicleOption.id,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.size(16.dp))
            val (fil, en) = severityTextFor(severity)
            val label = bodyOption?.let { tr("Hanggang ${it.fil.lowercase()}", "Up to ${it.en.lowercase()}") }
                ?: vehicleOption?.let { tr(it.fil, it.en) }
                ?: tr(fil, en)
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The photo's hash always rides with the event (report/ReportPhoto.kt); the photo
 * itself only rides as far as this device's own storage — never over SMS or mesh
 * relay. So this card has two honest states: a thumbnail on the device that took it,
 * or a plain statement that a photo exists somewhere without one here — never a
 * spinner pretending the image is still arriving, since nothing is fetching it.
 */
@Composable
private fun PhotoStatusCard(photoHash: String) {
    val context = LocalContext.current
    val thumbnail = remember(photoHash) { PhotoStore.loadThumbnail(context, photoHash) }
    var showFullImage by remember { mutableStateOf(false) }
    InfoCard {
        Text(tr("LARAWAN", "PHOTO"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = tr("Larawan ng ulat — i-tap para palakihin", "Report photo — tap to enlarge"),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clickable { showFullImage = true },
            )
            if (showFullImage) {
                FullscreenPhotoViewer(photoHash = photoHash, onDismiss = { showFullImage = false })
            }
        } else {
            Text(
                tr(
                    "May larawang nakalakip, pero wala ito sa phone na ito — dumating lang ang hash, hindi ang larawan.",
                    "A photo is attached, but it isn't on this phone — only its hash arrived, not the photo.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Full-screen photo view, reached by tapping the thumbnail. Loads a larger decode of
 * the same file ([PhotoStore.loadThumbnail]'s downsampling still applies — a report
 * photo never needs its raw capture resolution on screen, even enlarged) rather than
 * adding a second storage path.
 */
@Composable
private fun FullscreenPhotoViewer(photoHash: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val large = remember(photoHash) { PhotoStore.loadThumbnail(context, photoHash, maxDimension = 1600) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
        ) {
            if (large != null) {
                Image(
                    bitmap = large.asImageBitmap(),
                    contentDescription = tr("Larawan ng ulat", "Report photo"),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Filled.Close, contentDescription = tr("Isara", "Close"), tint = Color.White)
            }
        }
    }
}

@Composable
private fun SeverityBadge(severity: String) {
    val isConflict = severity == "SX"
    val onColor = Color.White
    Box(
        modifier = Modifier
            .then(
                if (isConflict) Modifier.background(conflictHatchBrush()) else Modifier.background(colorFor(severity))
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isConflict) {
                SeverityBadgeIcon(severity, tint = onColor, modifier = Modifier.size(15.dp))
                Spacer(Modifier.size(6.dp))
            }
            Text(severity, color = onColor, fontWeight = FontWeight.Bold)
        }
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
    val colors = LocalKaAlertoColors.current
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row {
            Box(modifier = Modifier.width(3.dp).background(MaterialTheme.colorScheme.onBackground))
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConfidenceIcon(summary.bucket, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.size(9.dp))
                    Text(bucketLabel(summary.bucket), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { summary.confidence.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = colors.borderEmphasis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    tr(
                        "${summary.confirmCount} nag-confirm · ${summary.disputeCount} nag-dispute",
                        "${summary.confirmCount} confirmed · ${summary.disputeCount} disputed",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConflictSection(summary: FeatureSummary) {
    val colors = LocalKaAlertoColors.current
    Surface(
        color = colors.criticalBg,
        border = BorderStroke(1.5.dp, colors.criticalFg.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            WarningTriangleIcon(tint = colors.criticalFg, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tr("Magkaibang ulat", "Conflicting reports"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.criticalFg)
                Spacer(Modifier.height(4.dp))
                Text(
                    tr(
                        "Ituring na hindi madaanan hangga't walang nakakumpirma.",
                        "Treat as impassable until someone confirms.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(tr("ANG DALAWANG ULAT", "THE TWO REPORTS"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    // The two positions that actually disagree, not the whole history — one from
    // each side of the split, most recent first, matching the artboard's framing.
    val reports = summary.events.filter { it.severity != null }
    val dangerous = reports.filter { (it.severity == "S2" || it.severity == "S3") }.maxByOrNull { it.timestampMs }
    val safe = reports.filter { (it.severity == "S0" || it.severity == "S1") }.maxByOrNull { it.timestampMs }
    listOfNotNull(dangerous, safe).sortedByDescending { it.timestampMs }.forEach { event ->
        ConflictReportRow(event)
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(4.dp))
    Text(
        tr(
            "Hindi namin pinipili kung sino ang tama, at hindi rin namin pinagsasama. Ipinapakita namin ang hindi pagkakasundo.",
            "We don't pick who's right, and we don't average them together. We show the disagreement.",
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConflictReportRow(event: Event) {
    val color = event.severity?.let { colorFor(it) } ?: Color.Gray
    // Callers only ever pass events already filtered to severity != null.
    val (fil, en) = severityTextFor(event.severity ?: "S0")
    val severityText = tr(fil, en)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                timeFormat.format(event.timestampMs),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                val option = event.waterLevel?.let { resolveLevelOption(it) }
                val levelLabel = option?.let { tr(it.fil, it.en) } ?: event.waterLevel
                Text(levelLabel?.let { tr("Hanggang $it", "Up to $it") } ?: severityText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(tr("$severityText · nasa lugar", "$severityText · on scene"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(modifier = Modifier.size(width = 10.dp, height = 34.dp).background(color))
        }
    }
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
                "confirm" -> "${tr("Kumpirmasyon", "Confirmation")} · ${sourceLabel(event)}"
                "dispute" -> "${tr("Dispute", "Dispute")} (${event.disputeReason ?: "?"}) · ${sourceLabel(event)}"
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
        title = { Text(tr("Ano ang nangyari?", "What happened?")) },
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
                        Text(tr(fil, en), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr("Kanselahin", "Cancel")) }
        },
    )
}

/**
 * Day 10's official banner, in two states.
 *
 * When the ruling is in force it says what the barangay's position is and who set it.
 * When it is held by the second-official gate it says *that*, because a resident
 * looking at a road needs to know an official has called it clear and the map has not
 * accepted that yet — hiding a pending ruling would be as misleading as applying it.
 *
 * The contradiction line is BUILD_TASKS.md day 10's own requirement: contradicting crowd
 * reports stay visible, with a note saying how many.
 */
@Composable
private fun OfficialBanner(summary: FeatureSummary) {
    val colors = LocalKaAlertoColors.current
    val pending = summary.pendingSecondOfficial
    val accent = if (pending) colors.warningFg else colors.safeFg
    val background = if (pending) colors.warningBg else colors.safeBg
    val (fil, en) = severityTextFor(summary.officialSeverity ?: "S0")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConfidenceIcon("official", accent, Modifier.size(18.dp))
            Spacer(Modifier.size(9.dp))
            Text(
                if (pending) {
                    tr("Opisyal na status — naghihintay ng pangalawang opisyal", "Official status — waiting on a second official")
                } else {
                    tr("Opisyal na status", "Official status")
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Text(
            tr(fil, en),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 3.dp),
        )
        summary.officialAuthorName?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (summary.contradictingCount > 0) {
            Text(
                tr(
                    "${summary.contradictingCount} residente ang nag-uulat ng mas malala kaysa sa opisyal na status. Nananatili silang nakikita.",
                    "${summary.contradictingCount} residents are reporting worse than the official status. They stay visible.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
