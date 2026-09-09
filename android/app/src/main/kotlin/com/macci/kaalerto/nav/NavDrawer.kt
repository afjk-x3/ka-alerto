package com.macci.kaalerto.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.i18n.AppLanguage
import com.macci.kaalerto.i18n.tr
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
    // The drawn icon and the clickable area are deliberately different sizes. The outer
    // box keeps the original 24dp layout footprint — the barangay-title truncation
    // regression this caused once already was from growing *that* number, so it stays
    // put. The inner box is a real 44-48dp Material touch target (matching the role
    // badge's 48dp height and the Storm toggle's 48dp box in MapHeader) that overflows
    // the outer box's bounds: Compose doesn't clip a Box's children to its own size by
    // default, so the inner box renders — and receives taps — beyond the 24dp the row
    // actually reserves for it. Tap target grows; nothing else in the header shrinks.
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onClick),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            HamburgerIcon(
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(width = 17.dp, height = 12.dp),
            )
        }
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
 * profile ko" opens `identity/ProfileScreen.kt` — a dedicated editing screen, separate
 * from the first-run gate (`OnboardingScreen`) since 7 Sep. `RoleScreen`'s "Baguhin"
 * opens the same screen.
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
    currentLanguage: AppLanguage,
    onSetLanguage: (AppLanguage) -> Unit,
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
            // Without this, a tap on the panel's own non-interactive areas (the
            // identity block, the dividers, the footer text) falls through to the
            // scrim Box behind it and closes the drawer — the panel has to consume
            // every tap in its bounds, not just the ones its rows already handle.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
        // No windowInsetsPadding here: the drawer is a child of KaAlertoApp's root Box,
        // which already reserves the status/nav bar area for every screen. Padding again
        // here would double the gap above the identity block.
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
            DrawerRow(tr("Mapa", "Map"), onClick = { onDismiss(); onOpenMap() })
            DrawerRow(tr("Papel mo sa barangay", "Your role in the barangay"), onClick = { onDismiss(); onOpenRoles() })
            DrawerRow(tr("Ang profile ko", "My profile"), onClick = { onDismiss(); onOpenProfile() })
            DrawerRow(tr("Mga silungan", "Evacuation centres"), onClick = { onDismiss(); onOpenEvac() })
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(colors.border),
        )
        LanguageToggleRow(currentLanguage, onSetLanguage)
        Box(Modifier.weight(1f))
        Text(
            // The two controls this drawer deliberately does not carry, said plainly
            // rather than leaving their absence unexplained. Storm and SOS both need to
            // be one tap away, not one drawer-open plus one tap.
            tr(
                "Ang Storm mode at SOS ay nasa mismong screen — hindi kailangang buksan ito.",
                "Storm mode and SOS both live on the screen itself — no need to open this.",
            ),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** "Add a quick language toggle (to English) inside the sidebar" — a two-way switch, not a picker, since there are only two languages to switch between. */
@Composable
private fun LanguageToggleRow(current: AppLanguage, onSet: (AppLanguage) -> Unit) {
    val colors = LocalKaAlertoColors.current
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            tr("WIKA", "LANGUAGE"),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.padding(top = 6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, colors.border)),
        ) {
            LanguageOption("Filipino", selected = current == AppLanguage.FIL, onClick = { onSet(AppLanguage.FIL) }, modifier = Modifier.weight(1f))
            LanguageOption("English", selected = current == AppLanguage.EN, onClick = { onSet(AppLanguage.EN) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
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
