package com.macci.kaalerto.report

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.data.severityTextFor
import com.macci.kaalerto.i18n.tr
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
    onSubmitted: (featureRef: String) -> Unit,
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
    // The photo never leaves this device (report/ReportPhoto.kt) — only its hash rides
    // with the event. photoHash is what's actually submitted; photoPreview is only for
    // the thumbnail on this screen.
    var photoHash by remember { mutableStateOf<String?>(null) }
    var photoPreview by remember { mutableStateOf<Bitmap?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            photoHash = PhotoStore.storeBitmap(context, bitmap)
            photoPreview = bitmap
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val hash = PhotoStore.storeUri(context, uri)
            photoHash = hash
            photoPreview = hash?.let { PhotoStore.loadThumbnail(context, it) }
        }
    }

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Bumalik", "Back"))
            }
            Text(tr("Gaano kalalim?", "How deep is the water?"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                    val accuracyText = initialAccuracyMeters?.let { "GPS ±${it.toInt()} m" } ?: tr("Itinakda sa mapa", "Set on the map")
                    Text(
                        accuracyText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    tr("Baguhin", "Change"),
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
                label = tr("Katawan", "Body"),
                selected = mode == ReportMode.BODY,
                modifier = Modifier.weight(1f),
                icon = { tint -> PersonGlyph(tint = tint, modifier = Modifier.size(18.dp)) },
            ) {
                mode = ReportMode.BODY
                severityOverride = null
            }
            ModeTab(
                label = tr("Sasakyan", "Vehicle"),
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
                        if (severityOverride != null) tr("MANUAL NA SEVERITY", "MANUAL SEVERITY") else tr("IRE-REPORT BILANG", "WILL BE REPORTED AS"),
                        style = MaterialTheme.typography.labelSmall,
                        color = onSeverityColor.copy(alpha = 0.75f),
                    )
                    Text(tr(severityFil, severityEn), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onSeverityColor)
                }
            }
        }
        Text(
            if (severityOverride != null) {
                tr("Manu-mano itong itinakda. Pindutin para baguhin.", "This was set manually. Tap to change it.")
            } else {
                tr("Awtomatiko itong nakuha sa lalim. Pindutin para baguhin.", "This was worked out automatically from the depth. Tap to change it.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Report-Normal.dc.html's optional-photo row (FR-2.6). The photo itself never
        // leaves this device — only its hash rides with the event (see ReportPhoto.kt) —
        // so a peer that only receives the relayed event has proof a photo exists, not
        // the photo. There is no server or mesh photo transport built, and this row
        // doesn't claim there is.
        PhotoRow(
            preview = photoPreview,
            onTakePhoto = { cameraLauncher.launch(null) },
            onPickPhoto = { galleryLauncher.launch("image/*") },
            onRemove = {
                photoHash = null
                photoPreview = null
            },
        )

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(enabled = !submitting) {
                    submitting = true
                    scope.launch {
                        val featureRef = submitReport(context, selected, derivedSeverity, initialLat, initialLon, photoHash)
                        submitting = false
                        onSubmitted(featureRef)
                    }
                },
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (submitting) tr("Ipinapadala…", "Sending…") else tr("Ipadala ang ulat", "Send the report"),
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
                if (isOnline) {
                    tr("Ise-save sa phone mo kahit walang signal", "Saved on your phone even without signal")
                } else {
                    tr("Walang signal — ise-save muna sa phone", "No signal — saving to your phone first")
                },
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

/**
 * Camera intent + system gallery picker, not CameraX and not a permission-gated
 * capture flow — both routes hand off to an app the device already trusts, so this
 * app never needs the CAMERA runtime permission itself (matches lint.xml's rule that
 * every `uses-feature` here stays optional, no new required capability).
 */
@Composable
private fun PhotoRow(
    preview: Bitmap?,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalKaAlertoColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            tr("Larawan (opsyonal)", "Photo (optional)"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (preview != null) {
            Box {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = tr("Larawan ng ulat", "Report photo"),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .border(1.dp, colors.border),
                )
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = onRemove)
                        .padding(6.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = tr("Alisin ang larawan", "Remove the photo"), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PhotoActionButton(
                    label = tr("Kumuha ng larawan", "Take a photo"),
                    icon = { tint -> CameraGlyph(tint, Modifier.size(18.dp)) },
                    onClick = onTakePhoto,
                    modifier = Modifier.weight(1f),
                )
                PhotoActionButton(
                    label = tr("Pumili mula sa gallery", "Pick from gallery"),
                    icon = { tint -> GalleryGlyph(tint, Modifier.size(18.dp)) },
                    onClick = onPickPhoto,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Never a claim beyond what the app can back up — see the call site's comment.
        Text(
            tr(
                "Nananatili sa phone mo ang larawan. Isang hash lang nito ang ipinapadala.",
                "The photo stays on your phone. Only its hash is sent.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PhotoActionButton(
    label: String,
    icon: @Composable (tint: Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = modifier
            .border(1.dp, colors.borderEmphasis)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
    }
}

/** A camera body + shutter circle — drawn the way every other icon here is (NavDrawer.kt's HamburgerIcon rationale), not pulled from an extended icon pack this app doesn't otherwise depend on. */
@Composable
private fun CameraGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.7f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
            style = stroke,
        )
        drawRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.32f, 0f),
            size = androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.24f),
        )
        drawCircle(color = tint, radius = w * 0.2f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.58f), style = stroke)
    }
}

/** A picture frame with a mountain fold — the standard gallery-picker shape. */
@Composable
private fun GalleryGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            size = androidx.compose.ui.geometry.Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f),
            style = stroke,
        )
        drawCircle(color = tint, radius = w * 0.08f, center = androidx.compose.ui.geometry.Offset(w * 0.28f, h * 0.32f))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.12f, h * 0.82f)
            lineTo(w * 0.4f, h * 0.5f)
            lineTo(w * 0.6f, h * 0.68f)
            lineTo(w * 0.78f, h * 0.46f)
            lineTo(w * 0.9f, h * 0.82f)
            close()
        }
        drawPath(path, color = tint, style = stroke)
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
                tr(option.fil, option.en),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SeverityOverrideDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Palitan ang severity", "Change the severity")) },
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
                        Text("$severity — ${tr(fil, en)}", fontWeight = if (severity == current) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr("Kanselahin", "Cancel")) }
        },
    )
}
