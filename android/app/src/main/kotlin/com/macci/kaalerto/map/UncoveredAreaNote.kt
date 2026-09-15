package com.macci.kaalerto.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.i18n.tr

/**
 * The camera has settled somewhere no offline pack covers, so the basemap there is
 * whatever the network can fetch — blank in airplane mode. Said plainly rather than left
 * for somebody to discover mid-flood.
 *
 * [onDownloadHere] is non-null only while online: a button that cannot work offline is
 * the dead-switch the registration screen already refuses to show.
 */
@Composable
fun UncoveredAreaNote(onDownloadHere: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tr(
                "Walang offline na mapa rito — lalabas lang kapag may signal.",
                "No offline map here — it only shows with signal.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onDownloadHere != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                tr("I-download ang mapa rito", "Download the map here"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onDownloadHere)
                    .padding(horizontal = 8.dp, vertical = 14.dp),
            )
        }
    }
}
