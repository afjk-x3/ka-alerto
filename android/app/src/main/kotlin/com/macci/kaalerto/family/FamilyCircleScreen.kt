package com.macci.kaalerto.family

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.nav.HamburgerButton
import com.macci.kaalerto.sos.QrCode
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import kotlin.math.min
import kotlinx.coroutines.delay

/** Rapid-repeat-tap guard for "Ligtas ako" — long enough to absorb an anxious double/
 * triple tap, short enough that a genuine second check-in a minute later is never
 * blocked. */
private const val CHECKIN_TAP_COOLDOWN_MS = 3_000L

/** "Aking Pamilya" — a household circle joined by QR, plus a one-tap "Ligtas ako".
 * Deliberately no ViewModel: [statuses] and [myQrContent] are computed by the caller
 * (`ui/KaAlertoApp.kt`) from the shared event stream, the same pattern
 * `Screen.EvacCentres` already uses — this screen is presentation only. */
@Composable
fun FamilyCircleScreen(
    myQrContent: String,
    myLastCheckInMs: Long?,
    statuses: List<CircleMemberStatus>,
    onCheckIn: () -> Unit,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenScanner: () -> Unit,
    onShowMyQr: () -> Unit,
    modifier: Modifier = Modifier,
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                // onOpenMenu was already a parameter and already wired all the way from
                // KaAlertoApp.kt's drawer state — the button that actually calls it was
                // just never placed here. Same HamburgerButton + spacing ProfileScreen.kt
                // uses next to its own title.
                HamburgerButton(onClick = onOpenMenu, modifier = Modifier.padding(end = 12.dp, top = 3.dp))
                Column {
                    Text(tr("Pamilya", "Family"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    // +1: statuses never includes the viewer's own device — effectiveCircle
                    // explicitly excludes myAuthorId from its result — but the count here is
                    // "how many people are in this circle," which includes the viewer.
                    Text(
                        tr(
                            "${statuses.size + 1} tao · nasa phone lang, walang server",
                            "${statuses.size + 1} people · phone only, no server",
                        ),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            Box(Modifier.clickable(onClick = onBack).padding(8.dp)) {
                Text(tr("Isara", "Close"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Spacer(Modifier.height(14.dp))

        // My Status Card — this device's own check-ins only, never confused with a
        // circle member's (see CircleReducer.kt's myLastCheckInMs doc comment for the
        // bug this replaced). Always shown, not just once checked in: an absent card
        // reads as "nothing to see here," when the honest state is "you haven't told
        // your circle yet" — the same "absence is not the same claim as presence"
        // reasoning this app already applies to member rows and the rescue queue.
        MyStatusCard(lastCheckInMs = myLastCheckInMs)
        Spacer(Modifier.height(14.dp))

        // Members header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("ANG BILIG MO", "YOUR CIRCLE"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(11.dp))

        // Members list. `fill = true` (the default) rather than `false` — this claims
        // the space between "YOUR CIRCLE" and the bottom action section even when the
        // list is short or empty, so "Ligtas ako" and the QR buttons anchor to the
        // bottom of the screen like every other primary-action screen in this app,
        // instead of floating up directly under the header with a large empty gap
        // beneath them.
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (statuses.isEmpty()) {
                // A blank area here reads as "this screen is broken," not "you haven't
                // paired anyone yet" — the same reasoning that already keeps the rescue
                // queue and the evac list from ever going silently blank.
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PersonOutlineIcon(
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            tr("Wala ka pang kasama sa bilog", "No one in your circle yet"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            tr("Magdagdag gamit ang QR sa ibaba", "Add someone using the QR below"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(statuses, key = { it.authorId }) { status ->
                CircleMemberRow(status = status)
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // Bottom section: Ligtas ako button + QR prompt
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Ligtas ako button - full width, 64dp height, black background
            var lastCheckInAtMs by remember { mutableStateOf<Long?>(null) }
            var checkInEnabled by remember { mutableStateOf(true) }
            LaunchedEffect(lastCheckInAtMs) {
                if (lastCheckInAtMs != null) {
                    checkInEnabled = false
                    delay(CHECKIN_TAP_COOLDOWN_MS)
                    checkInEnabled = true
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color(0xFF14171A))
                    .clickable(enabled = checkInEnabled) {
                        onCheckIn()
                        lastCheckInAtMs = System.currentTimeMillis()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(
                        tr("Ligtas ako", "I'm safe"),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            // Adding someone works from either side: show your own QR for them to scan,
            // or scan theirs — the circle-unification fold treats a scan from either
            // device as the same mutual invite (specs/2026-09-12-circle-unification-redesign.md).
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QrActionButton(
                    icon = { tint -> QrCodeIcon(tint = tint, modifier = Modifier.size(14.dp)) },
                    label = tr("Ipakita ang QR ko", "Show my QR"),
                    onClick = onShowMyQr,
                    modifier = Modifier.weight(1f),
                )
                QrActionButton(
                    icon = { tint -> ScanIcon(tint = tint, modifier = Modifier.size(14.dp)) },
                    label = tr("I-scan ang QR", "Scan a QR"),
                    onClick = onOpenScanner,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                tr("Magdagdag ng tao gamit ang QR — walang internet", "Add people using QR — no internet"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** One half of the "show mine" / "scan theirs" pair below the check-in button — same
 * bordered-box shape as this app's other paired choices (e.g. `identity/RoleScreen.kt`'s
 * role rows), sized to sit side by side rather than stacked. */
@Composable
private fun QrActionButton(
    icon: @Composable (Color) -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    Box(
        modifier = modifier
            .border(1.5.dp, colors.border)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            icon(MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.size(7.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/**
 * Always visible, never blank — a card that only appears once checked in reads as
 * "nothing to report" when the honest state is "you haven't told your circle yet."
 * Two states, not one: checked in (green, matches the design artifact
 * `design/artboards/FamilyCheckin.dc.html`'s "Ligtas ka" card exactly) and not yet
 * (neutral grey, same tone [CircleMemberRow] already uses for a member who hasn't
 * checked in — claiming "Ligtas ka" here would be exactly the false-progress claim
 * this app is careful to avoid everywhere else).
 *
 * The accent is a left-edge stripe only, matching the artifact's own
 * `border-left: 4px solid` — a plain `Modifier.border(...)` draws all four sides, which
 * is what this looked like before. `Row(Modifier.height(IntrinsicSize.Min))` is the
 * standard way to make the stripe's `fillMaxHeight()` match its sibling's actual
 * (content-driven) height rather than collapsing to zero.
 */
@Composable
private fun MyStatusCard(lastCheckInMs: Long?) {
    val checkedIn = lastCheckInMs != null
    val accentColor = if (checkedIn) Color(0xFF2E7D4F) else Color(0xFF8A939B)
    val backgroundColor = if (checkedIn) Color(0xFFE4F1E9) else Color(0xFFF2F4F6)
    val headline = if (checkedIn) tr("Ligtas ka", "You are safe") else tr("Wala ka pang check-in", "You haven't checked in yet")
    val subtext = if (checkedIn) {
        checkInAgeLabel(lastCheckInMs)
    } else {
        tr("I-tap ang \"Ligtas ako\" sa ibaba para malaman ng iyong bilog", "Tap \"I'm safe\" below to let your circle know")
    }
    val subtextColor = if (checkedIn) Color(0xFF1E6B3F) else Color(0xFF5C666F)

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accentColor),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(backgroundColor)
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(backgroundColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (checkedIn) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                } else {
                    PersonOutlineIcon(tint = accentColor, modifier = Modifier.size(20.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(headline, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14171A))
                Text(subtext, fontSize = 14.sp, color = subtextColor)
            }
        }
    }
}

@Composable
private fun CircleMemberRow(status: CircleMemberStatus) {
    val avatarBg: Color
    val avatarFg: Color
    val statusText: String
    val statusColor: Color

    when {
        status.lastCheckInMs == null -> {
            // Never checked in - grey
            avatarBg = Color(0xFFF2F4F6)
            avatarFg = Color(0xFF8A939B)
            statusText = tr("Hindi pa nag-check in", "No check-in yet")
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
        (System.currentTimeMillis() - status.lastCheckInMs!!) < 60 * 60 * 1000 -> {
            // Recent check-in (< 1 hour) - green
            avatarBg = Color(0xFFE4F1E9)
            avatarFg = Color(0xFF2E7D4F)
            statusText = checkInAgeLabel(status.lastCheckInMs)
            statusColor = Color(0xFF1E6B3F)
        }
        else -> {
            // Stale check-in (> 1 hour) - amber
            avatarBg = Color(0xFFFFF8E8)
            avatarFg = Color(0xFFA3791A)
            statusText = checkInAgeLabel(status.lastCheckInMs)
            statusColor = Color(0xFFA3791A)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(avatarBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // Three distinct states, three distinct glyphs — "never checked in" and
            // "stale" used to both render Icons.Filled.Close (an X), which reads as
            // "not safe" for a household member who may well be fine and simply hasn't
            // opened the app. Matches the design artifact: check for recent, clock for
            // stale, an outline person for never.
            when {
                status.lastCheckInMs == null ->
                    PersonOutlineIcon(tint = avatarFg, modifier = Modifier.size(20.dp))
                (System.currentTimeMillis() - status.lastCheckInMs!!) < 60 * 60 * 1000 ->
                    Icon(Icons.Filled.Check, contentDescription = null, tint = avatarFg, modifier = Modifier.size(20.dp))
                else ->
                    ClockIcon(tint = avatarFg, modifier = Modifier.size(20.dp))
            }
        }

        // Name + status
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(status.displayName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(statusText, fontSize = 14.sp, color = statusColor)
        }

        // Delivery method badge — internet/SMS only, matching the design artifact
        // (design/artboards/FamilyCheckin.dc.html) exactly: its two badged rows are the
        // internet and SMS examples, and its stale and never-checked-in rows carry no
        // badge at all, not even a placeholder. MESH and UNKNOWN used to render one
        // anyway (a mesh badge, or a warning-triangle "—" for unknown) — an addition
        // beyond what the design ever specified, removed here rather than kept "just in
        // case," per the same reasoning that already keeps this app from inventing UI
        // the artboards don't call for.
        if (status.deliveryMethod == DeliveryMethod.INTERNET || status.deliveryMethod == DeliveryMethod.SMS) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DeliveryMethodIcon(
                    method = status.deliveryMethod,
                    tint = deliveryMethodIconColor(status.deliveryMethod),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    deliveryMethodLabel(status.deliveryMethod),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** "Ligtas ako · 18 min ago", or "no check-in yet" — never a bad-news state. */
@Composable
private fun checkInAgeLabel(lastCheckInMs: Long?): String {
    if (lastCheckInMs == null) return tr("wala pang check-in", "no check-in yet")
    val minutes = (System.currentTimeMillis() - lastCheckInMs) / 60_000
    val age = when {
        minutes < 1 -> tr("ngayon lang", "just now")
        minutes < 60 -> tr("$minutes min ang nakalipas", "$minutes min ago")
        else -> tr("${minutes / 60}h ${minutes % 60}m ang nakalipas", "${minutes / 60}h ${minutes % 60}m ago")
    }
    return "${tr("Ligtas ako", "I'm safe")} · $age"
}

@Composable
private fun DeliveryMethodIcon(method: DeliveryMethod, tint: Color, modifier: Modifier = Modifier) {
    when (method) {
        DeliveryMethod.INTERNET -> InternetIcon(tint, modifier)
        DeliveryMethod.SMS -> SmsIcon(tint, modifier)
        DeliveryMethod.MESH -> MeshIcon(tint, modifier)
        DeliveryMethod.UNKNOWN -> UnknownIcon(tint, modifier)
    }
}

private fun deliveryMethodLabel(method: DeliveryMethod): String = when (method) {
    DeliveryMethod.INTERNET -> "internet"
    DeliveryMethod.SMS -> "SMS"
    DeliveryMethod.MESH -> "mesh"
    DeliveryMethod.UNKNOWN -> "—"
}

private fun deliveryMethodIconColor(method: DeliveryMethod): Color = when (method) {
    DeliveryMethod.INTERNET -> Color(0xFF2F7FBF)
    DeliveryMethod.SMS -> Color(0xFF5C666F)
    DeliveryMethod.MESH -> Color(0xFF2F7FBF)
    DeliveryMethod.UNKNOWN -> Color(0xFF8A939B)
}

/** Delivery method icons drawn via Canvas (since material-icons-core is a curated set). */
@Composable
private fun InternetIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.11f, cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.86f, h * 0.24f)
            lineTo(w * 0.86f, h * 0.5f)
            Path().also { cubic ->
                cubic.cubicTo(w * 0.86f, h * 0.78f, w * 0.7f, h * 0.9f, w * 0.5f, h * 0.96f)
                cubic.cubicTo(w * 0.3f, h * 0.9f, w * 0.14f, h * 0.78f, w * 0.14f, h * 0.5f)
            }
            lineTo(w * 0.14f, h * 0.24f)
            close()
        }
        drawPath(path, color = tint, style = stroke)
    }
}

@Composable
private fun SmsIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.11f, cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(w * 0.1f, h * 0.1f)
            lineTo(w * 0.9f, h * 0.1f)
            lineTo(w * 0.9f, h * 0.9f)
            lineTo(w * 0.1f, h * 0.9f)
            close()
            moveTo(w * 0.3f, h * 0.4f)
            lineTo(w * 0.7f, h * 0.4f)
            moveTo(w * 0.3f, h * 0.5f)
            lineTo(w * 0.7f, h * 0.5f)
            moveTo(w * 0.3f, h * 0.6f)
            lineTo(w * 0.5f, h * 0.6f)
        }
        drawPath(path, color = tint, style = stroke)
    }
}

@Composable
private fun MeshIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = min(w, h) * 0.14f
        val left = Offset(w * 0.2f, h * 0.5f)
        val topRight = Offset(w * 0.8f, h * 0.22f)
        val bottomRight = Offset(w * 0.8f, h * 0.78f)
        val stroke = Stroke(width = min(w, h) * 0.09f)
        drawLine(tint, left, topRight, stroke.width, StrokeCap.Round)
        drawLine(tint, left, bottomRight, stroke.width, StrokeCap.Round)
        drawCircle(tint, radius = r, center = left, style = stroke)
        drawCircle(tint, radius = r, center = topRight, style = stroke)
        drawCircle(tint, radius = r, center = bottomRight, style = stroke)
    }
}

@Composable
private fun QrCodeIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.11f, cap = StrokeCap.Round)
        val path = Path().apply {
            // Top-left finder pattern
            moveTo(w * 0.1f, h * 0.1f)
            lineTo(w * 0.4f, h * 0.1f)
            lineTo(w * 0.4f, h * 0.4f)
            lineTo(w * 0.1f, h * 0.4f)
            close()
            // Top-right finder pattern
            moveTo(w * 0.6f, h * 0.1f)
            lineTo(w * 0.9f, h * 0.1f)
            lineTo(w * 0.9f, h * 0.4f)
            lineTo(w * 0.6f, h * 0.4f)
            close()
            // Bottom-left finder pattern
            moveTo(w * 0.1f, h * 0.6f)
            lineTo(w * 0.4f, h * 0.6f)
            lineTo(w * 0.4f, h * 0.9f)
            lineTo(w * 0.1f, h * 0.9f)
            close()
            // Center timing pattern
            moveTo(w * 0.45f, h * 0.45f)
            lineTo(w * 0.55f, h * 0.45f)
            moveTo(w * 0.45f, h * 0.55f)
            lineTo(w * 0.55f, h * 0.55f)
        }
        drawPath(path, color = tint, style = stroke)
    }
}

/** A stale check-in's avatar glyph — a plain clock face, matching the design artifact's
 * treatment of "time has passed since the last check-in" as distinct from "never
 * checked in" ([PersonOutlineIcon]) and "recently safe" (a plain checkmark). */
@Composable
private fun ClockIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.1f, cap = StrokeCap.Round)
        val center = Offset(w * 0.5f, h * 0.5f)
        val radius = min(w, h) * 0.4f
        drawCircle(tint, radius = radius, center = center, style = stroke)
        drawLine(tint, center, Offset(center.x, center.y - radius * 0.55f), stroke.width, StrokeCap.Round)
        drawLine(tint, center, Offset(center.x + radius * 0.4f, center.y + radius * 0.15f), stroke.width, StrokeCap.Round)
    }
}

/** A never-checked-in member's avatar glyph — a neutral outline person, not the X this
 * used to share with the stale state. Someone who hasn't opened the app is not the same
 * claim as someone whose last check-in has aged. */
@Composable
private fun PersonOutlineIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.1f, cap = StrokeCap.Round)
        drawCircle(tint, radius = min(w, h) * 0.18f, center = Offset(w * 0.5f, h * 0.32f), style = stroke)
        val shoulders = Path().apply {
            moveTo(w * 0.22f, h * 0.85f)
            cubicTo(w * 0.22f, h * 0.6f, w * 0.78f, h * 0.6f, w * 0.78f, h * 0.85f)
        }
        drawPath(shoulders, color = tint, style = stroke)
    }
}

/** A viewfinder-corners glyph — distinct from [QrCodeIcon]'s module-pattern glyph, so the
 * "show mine" and "scan theirs" buttons read apart from each other at a glance. */
@Composable
private fun ScanIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.11f, cap = StrokeCap.Round)
        val corner = min(w, h) * 0.28f
        val path = Path().apply {
            moveTo(w * 0.1f, h * 0.1f + corner)
            lineTo(w * 0.1f, h * 0.1f)
            lineTo(w * 0.1f + corner, h * 0.1f)

            moveTo(w * 0.9f - corner, h * 0.1f)
            lineTo(w * 0.9f, h * 0.1f)
            lineTo(w * 0.9f, h * 0.1f + corner)

            moveTo(w * 0.9f, h * 0.9f - corner)
            lineTo(w * 0.9f, h * 0.9f)
            lineTo(w * 0.9f - corner, h * 0.9f)

            moveTo(w * 0.1f + corner, h * 0.9f)
            lineTo(w * 0.1f, h * 0.9f)
            lineTo(w * 0.1f, h * 0.9f - corner)
        }
        drawPath(path, color = tint, style = stroke)
    }
}

@Composable
private fun UnknownIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = min(w, h) * 0.11f, cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.9f, h * 0.9f)
            lineTo(w * 0.1f, h * 0.9f)
            close()
        }
        drawPath(path, color = tint, style = stroke)
        drawLine(tint, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.5f, h * 0.56f), stroke.width, StrokeCap.Round)
        drawCircle(tint, radius = h * 0.045f, center = Offset(w * 0.5f, h * 0.7f))
    }
}