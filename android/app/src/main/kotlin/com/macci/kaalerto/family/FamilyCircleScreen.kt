package com.macci.kaalerto.family

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.nav.HamburgerButton
import com.macci.kaalerto.sos.QrCode

/**
 * "Aking Pamilya" — a household circle joined by QR, plus a one-tap "Ligtas ako".
 * Deliberately no ViewModel: [statuses] and [myQrContent] are computed by the caller
 * (`ui/KaAlertoApp.kt`) from the shared event stream, the same pattern
 * `Screen.EvacCentres` already uses — this screen is presentation only.
 */
@Composable
fun FamilyCircleScreen(
    myQrContent: String,
    statuses: List<CircleMemberStatus>,
    onCheckIn: () -> Unit,
    onMemberScanned: (authorId: String, authorName: String) -> Unit,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = result.contents ?: return@rememberLauncherForActivityResult
        val card = decodeCircleCard(scanned) ?: return@rememberLauncherForActivityResult
        onMemberScanned(card.authorId, card.authorName)
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HamburgerButton(onClick = onOpenMenu)
                Spacer(Modifier.size(8.dp))
                Text(tr("Aking Pamilya", "My Family"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            // "Isara" (Close), same text-button-not-icon convention `evac/EvacScreen.kt`
            // already uses for onBack alongside its own onOpenMenu hamburger.
            Box(Modifier.clickable(onClick = onBack).padding(8.dp)) {
                Text(tr("Isara", "Close"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onCheckIn)
                .padding(vertical = 20.dp),
        ) {
            Text(
                tr("Ligtas ako", "I'm safe"),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(tr("MGA KASAPI", "MEMBERS"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(statuses, key = { it.authorId }) { status -> CircleMemberRow(status) }
        }

        Spacer(Modifier.height(16.dp))
        Text(tr("I-SCAN PARA MAGDAGDAG", "SCAN TO ADD"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        QrCode(content = myQrContent, modifier = Modifier.size(150.dp))
        Spacer(Modifier.height(12.dp))
        // Prompt text is read here, in composable context, and only its resulting String
        // is captured by the clickable lambda below — `tr()` is itself @Composable and
        // cannot be invoked from inside a plain onClick lambda.
        val scanPrompt = tr("Itapat sa QR", "Point at the QR")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable {
                    scanLauncher.launch(
                        ScanOptions().setPrompt(scanPrompt).setBeepEnabled(false),
                    )
                }
                .padding(vertical = 14.dp),
        ) {
            Text(
                tr("Mag-scan ng QR", "Scan a QR"),
                modifier = Modifier.align(Alignment.Center),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CircleMemberRow(status: CircleMemberStatus) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(status.displayName, fontWeight = FontWeight.Medium)
        Text(
            checkInAgeLabel(status.lastCheckInMs),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Ligtas ako · 18 min ago", or "no check-in yet" — never a bad-news state, since the
 * only status this feature can carry at all is "safe". Deliberately duplicated rather
 * than reusing `detail/DetailSheet.kt`'s private `ageLabel` (not exported) — the same
 * small duplication already exists between that function and `sos/SosShared.kt`'s
 * `elapsedLabel`, which uses a different mm:ss format unsuited to this screen. */
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
