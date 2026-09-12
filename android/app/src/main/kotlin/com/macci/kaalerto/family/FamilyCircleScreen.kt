package com.macci.kaalerto.family

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    statuses: List<CircleMemberStatus>,
    onCheckIn: () -> Unit,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenScanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Find "my" status (the current device's authorId) - assume first in list or match by some logic
    // For now, we'll show the first status as "my status" if it has a check-in, otherwise show default
    val myStatus = statuses.firstOrNull { it.lastCheckInMs != null }
        ?: statuses.firstOrNull()
        ?: CircleMemberStatus("", "", null, DeliveryMethod.UNKNOWN)

    val otherStatuses = statuses.filter { it != myStatus }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(tr("Pamilya", "Family"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    tr("${statuses.size} tao · nasa phone lang, walang server", "${statuses.size} people · phone only, no server"),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Box(Modifier.clickable(onClick = onBack).padding(8.dp)) {
                Text(tr("Isara", "Close"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Spacer(Modifier.height(14.dp))

        // My Status Card
        if (myStatus.lastCheckInMs != null) {
            MyStatusCard(myStatus = myStatus)
            Spacer(Modifier.height(14.dp))
        }

        // Members header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("ANG BILIG MO", "YOUR CIRCLE"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(11.dp))

        // Members list
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(otherStatuses, key = { it.authorId }) { status ->
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

            // QR prompt text - clickable to open scanner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenScanner() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    QrCodeIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(7.dp))
                    Text(
                        tr("Magdagdag ng tao gamit ang QR — walang internet", "Add people using QR — no internet"),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MyStatusCard(myStatus: CircleMemberStatus) {
    val ageLabel = checkInAgeLabel(myStatus.lastCheckInMs)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE4F1E9))
            .padding(15.dp)
            .border(BorderStroke(4.dp, Color(0xFF2E7D4F))),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            // Green check circle icon
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(0xFFE4F1E9), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF2E7D4F), modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tr("Ligtas ka", "You are safe"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14171A))
                Text(ageLabel, fontSize = 14.sp, color = Color(0xFF1E6B3F))
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

    val deliveryLabel = deliveryMethodLabel(status.deliveryMethod)

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
            // Avatar icon based on status
            val avatarIcon = when {
                status.lastCheckInMs == null -> Icons.Filled.Close
                (System.currentTimeMillis() - status.lastCheckInMs!!) < 60 * 60 * 1000 -> Icons.Filled.Check
                else -> Icons.Filled.Close
            }
            Icon(avatarIcon, contentDescription = null, tint = avatarFg, modifier = Modifier.size(20.dp))
        }

        // Name + status
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(status.displayName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(statusText, fontSize = 14.sp, color = statusColor)
        }

        // Delivery method badge
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DeliveryMethodIcon(
                method = status.deliveryMethod,
                tint = deliveryMethodIconColor(status.deliveryMethod),
                modifier = Modifier.size(14.dp)
            )
            Text(deliveryLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
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