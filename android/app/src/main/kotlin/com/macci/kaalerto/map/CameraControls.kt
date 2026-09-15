package com.macci.kaalerto.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
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
            // Same in both languages: it names a place, and "Demo" is what the sample
            // reports are called everywhere else in this build.
            MapControlButton(label = "Demo: San Nicolas", onClick = onJumpToDemo)
        }
        MapControlButton(
            label = if (locating) tr("Hinahanap…", "Locating…") else tr("Nasaan ako", "My location"),
            onClick = onLocateMe,
            showPin = true,
        )
    }
}

@Composable
private fun MapControlButton(label: String, onClick: () -> Unit, showPin: Boolean = false) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, LocalKaAlertoColors.current.borderEmphasis)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPin) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
