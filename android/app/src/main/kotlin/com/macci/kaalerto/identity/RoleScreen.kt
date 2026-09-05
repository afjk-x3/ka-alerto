package com.macci.kaalerto.identity

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.detail.CheckIcon
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * BUILD_TASKS.md day 10's "Role toggle (settings): Resident / Responder / Barangay
 * Official". It replaces day 9's single responder switch, which lived on the nearby-SOS
 * screen because that was the only role that existed then.
 *
 * The banner at the top is the whole point of this screen existing as a *screen* rather
 * than a hidden gesture: in the real product none of these are self-granted — a
 * volunteer applies and the barangay activates them, and an official's authority comes
 * from holding barangay office. Presenting that as a settings switch without saying so
 * would misrepresent the trust model the confidence rules are built on, and the official
 * role in particular changes what every other device sees.
 */
@Composable
fun RoleScreen(
    current: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 12.dp),
        ) {
            Text(
                "Papel mo sa barangay",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Your role",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.warningBg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "Demo lang ang switch na ito. Sa totoong app, ang barangay ang nag-a-aktibo ng responder, at ang opisyal ay galing sa hawak na puwesto sa barangay — hindi ito pinipili ng user.",
                fontSize = 13.sp,
                color = colors.warningFg,
            )
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LocalIdentity.ALL_ROLES.forEach { role ->
                val (title, detail) = roleLabel(role)
                RoleOption(
                    title = title,
                    detail = detail,
                    badge = roleBadge(role),
                    selected = role == current,
                    onClick = { onSelect(role) },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(52.dp)
                .border(1.5.dp, colors.borderEmphasis)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Bumalik sa mapa",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun RoleOption(
    title: String,
    detail: String,
    badge: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.onBackground else colors.border,
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background,
                    CircleShape,
                )
                .border(2.dp, if (selected) MaterialTheme.colorScheme.onBackground else colors.borderEmphasis, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) CheckIcon(MaterialTheme.colorScheme.background, Modifier.size(13.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .background(if (selected) MaterialTheme.colorScheme.onBackground else colors.recessedSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                badge,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
