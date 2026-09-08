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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import com.macci.kaalerto.demo.DemoArea
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.detail.MeshIcon
import com.macci.kaalerto.mesh.MeshStatus
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
    meshStatus: MeshStatus,
    stormMode: Boolean,
    onModeIconClick: () -> Unit,
    onOpenMenu: () -> Unit,
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
        com.macci.kaalerto.nav.HamburgerButton(
            onClick = onOpenMenu,
            modifier = Modifier.padding(end = 6.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                DemoArea.BARANGAY_NAME,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (isOnline) colors.safeFg else colors.criticalFg, CircleShape),
                )
                Spacer(Modifier.size(6.dp))
                val statusText = if (isOnline) {
                    tr("May koneksyon · $reportsToday ulat ngayong araw", "Online · $reportsToday reports today")
                } else {
                    tr("Walang signal · $reportsToday ulat ngayong araw", "No signal · $reportsToday reports today")
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            MeshStatusLine(meshStatus)
        }
        // Day 10 put the acting role here as a badge; moved to the hamburger drawer
        // (nav/NavDrawer.kt already shows it and already routes to the same role
        // screen) after a real-device test found the header too crowded. The drawer is
        // reached via the same hamburger button already in this row, so nothing here
        // lost reachability — it just stopped being duplicated in two places.
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

/**
 * BUILD_TASKS.md days 6-7's peer counter. Rendered only once the mesh service is
 * actually up (or has actually failed) — a resident who never granted the Bluetooth
 * permissions has no relay, and a permanent "0 kalapit na phone" would read as "nobody
 * is nearby" rather than "this phone isn't looking".
 *
 * Copy follows the design system's own phrase for this, "kalapit na phone"
 * (SOSStatus.dc.html, DetailConfirmed-*.dc.html), not a fresh translation.
 */
@Composable
private fun MeshStatusLine(status: MeshStatus) {
    if (!status.running && status.error == null) return

    val colors = LocalKaAlertoColors.current
    val tint = if (status.error != null) colors.warningFg else MaterialTheme.colorScheme.onSurfaceVariant
    val text = when {
        status.error != null -> status.error
        status.peerCount > 0 -> tr("${status.peerCount} kalapit na phone", "${status.peerCount} nearby phones")
        else -> tr("Naghahanap ng kalapit na phone", "Looking for nearby phones")
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        MeshIcon(tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
