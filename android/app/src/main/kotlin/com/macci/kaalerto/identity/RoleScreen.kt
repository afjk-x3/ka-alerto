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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BUILD_TASKS.md day 10's role screen, rebuilt as the real activation flow rather than
 * a settings switch.
 *
 * Three things a resident can do here, and they are deliberately not the same thing:
 * hold a barangay seat (claim it from the shipped roster), ask to be activated as a
 * responder (an application, answered by somebody else), or neither. An official
 * additionally sees the applications waiting on them. Nobody picks their own role from a
 * list any more — that list was the thing this screen's own banner used to apologise for.
 *
 * **The banner still exists, because the honest caveat changed rather than went away.**
 * It used to say "this switch is a demo". It now says what is actually true: the flow is
 * real and replicates, but ground rule 4 forbids signatures, so a claim is an assertion
 * under a name and not proof. Removing the caveat entirely would be the same mistake in
 * the other direction — a mechanism that looks like verification and is not.
 */
@Composable
fun RoleScreen(
    state: RoleState,
    myAuthorId: String,
    unclaimedSeats: List<BarangaySeat>,
    onClaimSeat: (BarangaySeat) -> Unit,
    onApply: () -> Unit,
    onGrant: (RoleApplication) -> Unit,
    onRevoke: (RoleGrantRecord) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    val myRole = state.roleOf(myAuthorId)
    val mySeat = state.seatOf(myAuthorId)
    val myGrant = state.grantOf(myAuthorId)
    val isOfficial = myRole == LocalIdentity.ROLE_OFFICIAL

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
            Text("Your role", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.warningBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "Totoong umaandar ito — kumakalat sa mesh at pareho ang nakikita ng lahat ng phone. " +
                        "Pero walang lagda: ang pag-angkin ng puwesto ay sinasabi lang, hindi napapatunayan.",
                    fontSize = 13.sp,
                    color = colors.warningFg,
                )
            }

            SectionLabel("ANG PAPEL MO NGAYON")
            CurrentRoleCard(myRole = myRole, seat = mySeat, grant = myGrant)

            // A resident's own path: apply, and then wait for a person to answer.
            if (!isOfficial && myGrant == null) {
                SectionLabel("MAGING RESPONDER")
                if (state.hasApplied(myAuthorId)) {
                    InfoCard(
                        title = "Naipadala na ang hiling mo",
                        detail = "Naghihintay ng opisyal ng barangay. Makikita nila ito kahit walang signal, " +
                            "sa oras na magkalapit ang inyong phone.",
                    )
                } else {
                    ActionCard(
                        title = "Humiling na maging responder",
                        detail = "Ang barangay ang nag-a-aktibo — hindi mo ito mabubuksan sa sarili mo.",
                        onClick = onApply,
                    )
                }
            }

            // The bootstrap. Authority enters the system here and nowhere else.
            SectionLabel("MGA PUWESTO SA BARANGAY")
            if (mySeat == null && unclaimedSeats.isNotEmpty()) {
                Text(
                    "Kung ikaw ang may hawak ng puwesto, angkinin mo ito. Ang pangalan mo ang kasama " +
                        "sa bawat ulat na ipo-post mo bilang opisyal.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.seats.forEach { held -> SeatHeldRow(held, isMine = held.authorId == myAuthorId) }
                if (mySeat == null) {
                    unclaimedSeats.forEach { seat ->
                        ActionCard(
                            title = seat.title,
                            detail = "Walang may hawak — angkinin",
                            onClick = { onClaimSeat(seat) },
                        )
                    }
                }
            }

            if (isOfficial) {
                SectionLabel("MGA HILING NA NAGHIHINTAY (${state.pending.size})")
                if (state.pending.isEmpty()) {
                    InfoCard(title = "Wala pang naghihintay", detail = null)
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.pending.forEach { application ->
                            ActionCard(
                                title = application.authorName,
                                detail = "Humiling ${timeOf(application.atMs)} · i-aktibo bilang responder",
                                onClick = { onGrant(application) },
                            )
                        }
                    }
                }

                if (state.grants.isNotEmpty()) {
                    SectionLabel("MGA AKTIBONG RESPONDER (${state.grants.size})")
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.grants.forEach { grant ->
                            ActionCard(
                                title = grant.subjectName,
                                detail = "In-aktibo ni ${grant.byName} · ${timeOf(grant.atMs)} — pindutin para itigil",
                                onClick = { onRevoke(grant) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.size(16.dp))
        }

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

/**
 * Says what this device is *and where that came from*. A role with no provenance is the
 * old switch wearing a new label — "Responder" alone is a claim, "activated by Kagawad —
 * Purok 3 at 3:02 PM" is something a person can check against their memory of the day.
 */
@Composable
private fun CurrentRoleCard(myRole: String, seat: SeatHolder?, grant: RoleGrantRecord?) {
    val colors = LocalKaAlertoColors.current
    val (title, _) = roleLabel(myRole)
    val provenance = when {
        seat != null -> "${seat.seatTitle} · inangkin ${timeOf(seat.sinceMs)}"
        grant != null -> "In-aktibo ni ${grant.byName} · ${timeOf(grant.atMs)}"
        else -> "Walang kailangang aktibasyon — ito ang simula ng lahat."
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(2.dp, MaterialTheme.colorScheme.onBackground)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.onBackground)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    roleBadge(myRole),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.background,
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(provenance, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (seat?.contested == true) {
            Spacer(Modifier.size(6.dp))
            Text(
                "May iba ring nag-angkin ng puwestong ito: ${seat.rivalNames.joinToString(", ")}. " +
                    "Ang naunang pag-angkin ang nananatili.",
                fontSize = 13.sp,
                color = colors.criticalFg,
            )
        }
    }
}

@Composable
private fun SeatHeldRow(held: SeatHolder, isMine: Boolean) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, if (held.contested) colors.criticalFg else colors.border)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                held.seatTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                if (held.contested) {
                    "${held.authorName} · pinagtatalunan ng ${held.rivalNames.size} pa"
                } else {
                    "${held.authorName} · ${timeOf(held.sinceMs)}"
                },
                fontSize = 13.sp,
                color = if (held.contested) colors.criticalFg else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isMine) {
            Spacer(Modifier.size(10.dp))
            CheckIcon(MaterialTheme.colorScheme.onBackground, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ActionCard(title: String, detail: String, onClick: () -> Unit) {
    val colors = LocalKaAlertoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, colors.borderEmphasis)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(title: String, detail: String?) {
    val colors = LocalKaAlertoColors.current
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, colors.border)
            .padding(14.dp),
    ) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (detail != null) {
            Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 2.dp),
    )
}

/**
 * Day 10's self-select toggle, kept alive only while [RoleMode.EVENT_SOURCED] is `false`.
 *
 * This is the screen the 6 September rebuild replaced, restored verbatim so that one
 * device can reach every role in two taps while the app is being exercised by hand. It
 * grants roles to itself, which is exactly what the real system forbids — hence the
 * banner, which is the original day-10 wording and still accurate *for this screen*.
 *
 * Delete this whole composable when the flag flips. It is not a fallback and not an
 * escape hatch for demo day; it is a bench tool.
 */
@Composable
fun ManualRoleScreen(
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
            Text("Your role", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.warningBg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "Pansamantalang mode para sa pagsubok. Sa totoong app, ang barangay ang " +
                    "nag-a-aktibo ng responder, at ang opisyal ay galing sa hawak na puwesto sa " +
                    "barangay — hindi ito pinipili ng user.",
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
                .border(
                    2.dp,
                    if (selected) MaterialTheme.colorScheme.onBackground else colors.borderEmphasis,
                    CircleShape,
                ),
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
        Spacer(Modifier.size(10.dp))
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

private fun timeOf(ms: Long): String =
    SimpleDateFormat("h:mm a", Locale.US).format(Date(ms))
