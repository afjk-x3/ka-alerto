package com.macci.kaalerto.family

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
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
 * The invite screen for an existing circle: its join code as a QR
 * (`CircleJoinCard(circleId, circleName)`, see `family/CircleQr.kt`), plus a share
 * button that hands the same code to Android's own share sheet — one join mechanism,
 * two delivery paths, per `specs/2026-09-23-circle-create-join-redesign.md`.
 *
 * [circleName] is null while this device's own `circle_create` event hasn't arrived
 * yet (see `family/CircleStore.kt`'s `resolveCircle`) — the QR still encodes
 * [circleId] correctly in that window, just with an empty name until it does.
 */
@Composable
fun MyCircleQrScreen(
    circleId: String,
    circleName: String?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    val qrContent = remember(circleId, circleName) { CircleJoinCard(circleId = circleId, name = circleName.orEmpty()).encode() }
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
                tr("Mag-imbita", "Invite"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Box(Modifier.minimumInteractiveComponentSize().clickable(onClick = onBack).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
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
            Text(circleName ?: tr("Circle", "Circle"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.size(12.dp))
            // Short enough to read out over a call (family/CircleSubmit.kt's shortCircleCode).
            val code = remember(circleId) { shortCircleCode(circleId) }
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    code,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.size(12.dp))
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .border(1.5.dp, colors.borderEmphasis)
                        .clickable { clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr("Kopyahin", "Copy"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            Spacer(Modifier.size(6.dp))
            Text(
                tr(
                    "Ipakita ito, o ibahagi ang code, sa taong sasali",
                    "Show this, or share the code, with the person joining",
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
                .border(1.5.dp, colors.borderEmphasis)
                .clickable(onClick = onShare)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tr("Ibahagi", "Share"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
