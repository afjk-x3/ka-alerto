package com.macci.kaalerto.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/** Map-Normal.dc.html's "FR-1.12 guidance disclaimer" strip, copy verbatim. */
@Composable
fun MapDisclaimer(modifier: Modifier = Modifier) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.recessedSurface)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            "Galing sa ulat ng residente. Maaaring may kulang o luma. Tingnan pa rin ang nasa harap mo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
