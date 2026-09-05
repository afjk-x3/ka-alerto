package com.macci.kaalerto.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.demo.DemoArea
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * Map-Normal.dc.html / Map-Storm.dc.html's top bar. The artboards show this as "Synced
 * just now" / "Walang signal" — language for a sync pipeline that isn't wired into the
 * app yet (server sync is build day 13). This shows what's actually true right now:
 * real connectivity, and how many reports exist on this device today.
 */
@Composable
fun MapHeader(
    isOnline: Boolean,
    reportsToday: Int,
    stormMode: Boolean,
    onModeIconClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                DemoArea.BARANGAY_NAME,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (isOnline) colors.safeFg else colors.criticalFg, CircleShape),
                )
                Spacer(Modifier.size(6.dp))
                val statusText = if (isOnline) {
                    "May koneksyon · $reportsToday ulat ngayong araw"
                } else {
                    "Walang signal · $reportsToday ulat ngayong araw"
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .border(BorderStroke(1.dp, colors.border)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onModeIconClick, modifier = Modifier.size(48.dp)) {
                ModeToggleIcon(
                    stormMode = stormMode,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
