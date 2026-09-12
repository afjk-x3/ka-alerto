package com.macci.kaalerto.family

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.sos.QrCode
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * The other half of family-circle pairing: [family/QrScannerScreen.kt] scans someone
 * else's QR, this screen shows this device's own so a second phone can scan *it* instead
 * — pairing was always meant to work from either side (see the circle-unification
 * redesign: scanning either device's QR joins both to the same circle). `myQrContent`
 * (a `CircleCard.encode()` string) was already computed in `ui/KaAlertoApp.kt` for
 * [FamilyCircleScreen] and had nowhere to render until this screen existed.
 *
 * [displayName] is shown under the code so the person scanning it can confirm they are
 * looking at the right phone before they scan — the QR payload itself carries the same
 * name, but nobody can read a QR by eye.
 */
@Composable
fun MyCircleQrScreen(
    qrContent: String,
    displayName: String,
    onBack: () -> Unit,
    onSwitchToScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                tr("Ang QR ko", "My QR"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Box(Modifier.clickable(onClick = onBack).padding(8.dp)) {
                Text(tr("Isara", "Close"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .border(1.5.dp, colors.border)
                    .padding(16.dp),
            ) {
                QrCode(content = qrContent, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.size(18.dp))
            Text(displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.size(6.dp))
            Text(
                tr(
                    "Ipakita ito sa taong magdadagdag sa iyo sa Aking Pamilya",
                    "Show this to the person adding you to My Family",
                ),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSwitchToScan)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tr("I-scan ng QR sa halip", "Scan a QR instead"),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
