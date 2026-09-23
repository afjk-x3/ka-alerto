package com.macci.kaalerto.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import com.macci.kaalerto.ui.theme.SeverityColors

/**
 * Map-Normal.dc.html's floating legend card — same colours as [MarkerIcons], spelled out.
 *
 * **Collapsed to a small chip by default.** Open, the card covered roughly a quarter of the map's
 * width and an eighth of its height on a small phone, permanently, for something a person needs
 * once. The chip still shows the three danger colours as a hint of what it holds; a tap opens the
 * card and a tap on the card closes it.
 */
@Composable
fun MapLegend(modifier: Modifier = Modifier) {
    val colors = LocalKaAlertoColors.current
    var expanded by remember { mutableStateOf(false) }
    val label = tr("Kahulugan ng kulay", "Colour key")
    if (!expanded) {
        Surface(
            modifier = modifier
                .heightIn(min = 44.dp)
                .clickable { expanded = true }
                .semantics { contentDescription = label },
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, colors.borderEmphasis),
        ) {
            Row(
                modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(SeverityColors.S3, SeverityColors.S2, SeverityColors.S1).forEach {
                    Box(Modifier.size(width = 10.dp, height = 5.dp).background(Color(android.graphics.Color.parseColor(it))))
                    Spacer(Modifier.size(3.dp))
                }
                Spacer(Modifier.size(5.dp))
                Text(tr("Kahulugan", "Legend"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        return
    }
    Surface(
        modifier = modifier.clickable { expanded = false }.semantics { contentDescription = label },
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            LegendRow(Color(android.graphics.Color.parseColor(SeverityColors.S3)), tr("Hindi madaanan", "Impassable"))
            LegendRow(Color(android.graphics.Color.parseColor(SeverityColors.S2)), tr("Hindi madaanan ng sasakyan", "Impassable by vehicle"))
            LegendRow(Color(android.graphics.Color.parseColor(SeverityColors.S1)), tr("Madaanan, mag-ingat", "Passable, be careful"))
            LegendRow(Color(0xFFB9A98F), tr("Luma na — kailangang tingnan", "Stale — needs a look"), muted = true)
            // PRD FR-1.5's statement lives here now rather than in a permanent strip under
            // the map, which cost a full row of map height for a sentence read once.
            Text(
                tr(
                    "Galing sa ulat ng residente. Maaaring may kulang o luma. Tingnan pa rin ang nasa harap mo.",
                    "From resident reports. May be incomplete or outdated. Still check what's in front of you.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp).widthIn(max = 240.dp),
            )
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, muted: Boolean = false) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(width = 16.dp, height = 5.dp).background(color),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
        )
    }
}
