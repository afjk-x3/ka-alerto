package com.macci.kaalerto.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import com.macci.kaalerto.ui.theme.SeverityColors

/** Map-Normal.dc.html's floating legend card — same colours as [MarkerIcons], spelled out. */
@Composable
fun MapLegend(modifier: Modifier = Modifier) {
    val colors = LocalKaAlertoColors.current
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            LegendRow(Color(android.graphics.Color.parseColor(SeverityColors.S3)), "Hindi madaanan")
            LegendRow(Color(android.graphics.Color.parseColor(SeverityColors.S2)), "Hindi madaanan ng sasakyan")
            LegendRow(Color(android.graphics.Color.parseColor(SeverityColors.S1)), "Madaanan, mag-ingat")
            LegendRow(Color(0xFFB9A98F), "Luma na — kailangang tingnan", muted = true)
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
