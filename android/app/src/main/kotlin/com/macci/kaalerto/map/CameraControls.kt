package com.macci.kaalerto.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * A one-shot camera move. [nonce] makes a second tap on the same place move the camera
 * again — a data class holding only the coordinates would compare equal and the effect
 * keyed on it would not re-run.
 */
data class CameraRequest(val lat: Double, val lon: Double, val nonce: Long = System.nanoTime())

/**
 * The map's camera jumps, stacked above the "Silungan" control.
 *
 * Both are one-shot: the camera moves once and then belongs to the user again. There is
 * deliberately no follow mode — a camera that chases GPS makes the demo unrepeatable and
 * puts a live location stream next to the render path.
 *
 * Labelled, not icon-only, for the reason the evacuation control was relabelled on
 * 7 September: a bare pin in a map corner reads as whatever the viewer expects it to be.
 */
@Composable
fun MapCameraControls(
    locating: Boolean,
    onLocateMe: () -> Unit,
    showDemoJump: Boolean,
    onJumpToDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showDemoJump) {
            // Same in both languages: it names a place, and "Demo" is what the sample reports are
            // called everywhere else in this build. A small chip now, with the place in its
            // description, since it only matters to someone who has panned away from the demo area.
            val demoDescription = "Demo: San Nicolas"
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, LocalKaAlertoColors.current.borderEmphasis)
                    .clickable(onClick = onJumpToDemo)
                    .semantics { contentDescription = demoDescription }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Demo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        // Icon-only: the crosshair is what every map uses for "where am I", it has a description for
        // screen readers, and the label was costing a wide button for a control people know on sight.
        val locateDescription = if (locating) tr("Hinahanap ang lokasyon…", "Locating…") else tr("Nasaan ako", "My location")
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, LocalKaAlertoColors.current.borderEmphasis)
                .clickable(onClick = onLocateMe)
                .semantics { contentDescription = locateDescription },
            contentAlignment = Alignment.Center,
        ) {
            if (locating) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onBackground)
            } else {
                LocateIcon(MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

/** A crosshair: a ring, a dot, and four ticks. Drawn here because the icon set in the build has no such glyph. */
@Composable
private fun LocateIcon(color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(24.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val ring = size.width * 0.28f
        val tick = size.width * 0.16f
        drawCircle(color, radius = ring, center = c, style = Stroke(width = 2.2f * density / 2f))
        drawCircle(color, radius = size.width * 0.08f, center = c)
        val w = 2.2f * density / 2f
        drawLine(color, Offset(c.x, 0f), Offset(c.x, tick), strokeWidth = w)
        drawLine(color, Offset(c.x, size.height - tick), Offset(c.x, size.height), strokeWidth = w)
        drawLine(color, Offset(0f, c.y), Offset(tick, c.y), strokeWidth = w)
        drawLine(color, Offset(size.width - tick, c.y), Offset(size.width, c.y), strokeWidth = w)
    }
}
