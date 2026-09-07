package com.macci.kaalerto.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.identity.roleBadge
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * The three-stroke hamburger glyph, drawn the way every other icon in this app is
 * (`detail/DetailIcons.kt`'s `CheckIcon`, `map/EvacMarkers.kt`'s roof glyph) rather than
 * pulled from Material's icon set — nothing else in the app does, and switching sources
 * for one control would be the inconsistency the design otherwise avoids.
 */
@Composable
fun HamburgerIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val stroke = size.height * 0.14f
        val ys = listOf(size.height * 0.22f, size.height * 0.5f, size.height * 0.78f)
        ys.forEach { y ->
            drawLine(
                color = tint,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/**
 * A hamburger button, gated the same way every optional affordance in this app is —
 * `null` means "not offered here", not "offered but disabled". The four SOS-request
 * screens (SOSHold → RescueCard) never pass a callback: their full-brightness black
 * backgrounds are a deliberate, tested design (`sos/RescueCardScreen.kt`'s "a card a
 * stranger has to read in the dark is worth the power"), and a header row would eat
 * into exactly the screen space that decision is about. The registration gate
 * (`identity/OnboardingScreen.kt`) also withholds it on a first run — PRD §9 makes
 * registration required, and a drawer link back to the map would be a way around that.
 */
@Composable
fun HamburgerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clickable(onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        HamburgerIcon(
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(width = 17.dp, height = 12.dp),
        )
    }
}

/**
 * The drawer itself: a scrim plus a sliding panel, both rendered by [KaAlertoApp] as an
 * overlay above whichever screen is showing underneath — not by each screen — so every
 * chrome-bearing screen gets the identical drawer with one shared implementation rather
 * than nine slightly different copies.
 *
 * Four destinations, matching what actually exists today. Nothing here is invented:
 * "Papel mo sa barangay" and "Mga silungan" are the exact routes the role badge and the
 * floating shelter button already open — this is a second way to reach them, not a
 * replacement, per the day-10 and day-10.1 controls staying where they are. "Ang
 * profile ko" opens the same registration screen already used to edit a name
 * (`RoleScreen`'s "Baguhin" does this today); a dedicated profile screen would have
 * duplicated the name + home-pin editing `OnboardingScreen` already does correctly.
 */
@Composable
fun NavDrawer(
    open: Boolean,
    myDisplayName: String,
    myRole: String,
    onDismiss: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenRoles: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenEvac: () -> Unit,
) {
    if (!open) return
    val colors = LocalKaAlertoColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                myDisplayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                roleBadge(myRole),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(colors.border),
        )
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            DrawerRow("Mapa", onClick = { onDismiss(); onOpenMap() })
            DrawerRow("Papel mo sa barangay", onClick = { onDismiss(); onOpenRoles() })
            DrawerRow("Ang profile ko", onClick = { onDismiss(); onOpenProfile() })
            DrawerRow("Mga silungan", onClick = { onDismiss(); onOpenEvac() })
        }
        Box(Modifier.weight(1f))
        Text(
            // The two controls this drawer deliberately does not carry, said plainly
            // rather than leaving their absence unexplained. Storm and SOS both need to
            // be one tap away, not one drawer-open plus one tap.
            "Ang Storm mode at SOS ay nasa mismong screen — hindi kailangang buksan ito.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun DrawerRow(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
