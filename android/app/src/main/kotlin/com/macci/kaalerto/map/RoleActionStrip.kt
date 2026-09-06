package com.macci.kaalerto.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.sos.SosColors
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * What this device can do that a resident cannot — on the map, where the role badge is.
 *
 * Added 6 September because switching role changed nothing anyone could see. The rescue
 * queue had **no inbound link at all** except tapping an incoming SOS alert, so a
 * responder with no live emergency could not reach the one screen their role exists for;
 * and an official's ruling screen sat behind tapping a marker with nothing to say so.
 * That is `docs/05-routing-matrix.md` §8's "role landing screens with no inbound links",
 * and it made both roles look unimplemented.
 *
 * The queue entry is shown **even when the queue is empty**, saying so. A control that
 * appears only when there is something in it teaches a responder that its absence means
 * "not my job today", when it actually means "nothing has arrived yet" — and on this
 * app's own terms those are different claims, one of which it cannot make.
 */
@Composable
fun RoleActionStrip(
    role: String,
    openRequestCount: Int,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (role == LocalIdentity.ROLE_RESIDENT) return

    val colors = LocalKaAlertoColors.current
    val urgent = openRequestCount > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(if (urgent) SosColors.Critical else MaterialTheme.colorScheme.background)
                .border(1.5.dp, if (urgent) SosColors.Critical else colors.borderEmphasis)
                .clickable(onClick = onOpenQueue)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (urgent) "Mga humihingi ng tulong" else "Walang humihingi ng tulong ngayon",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (urgent) SosColors.CardBackground else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (urgent) {
                Box(
                    modifier = Modifier
                        .background(SosColors.CardBackground)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        openRequestCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SosColors.Critical,
                    )
                }
            }
        }

        // An official's other two powers are already on the map, just not signposted:
        // the ruling screen opens from a marker's detail sheet, and evacuation centres
        // from the shelter button. One line rather than two more buttons — the header
        // was already crowded enough that day 10 moved the evac entry off it.
        if (role == LocalIdentity.ROLE_OFFICIAL) {
            Spacer(Modifier.size(6.dp))
            Text(
                "Kagawad: pindutin ang isang marker para mag-post ng opisyal na status, " +
                    "o ang bahay-bubong para sa mga evacuation centre.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
