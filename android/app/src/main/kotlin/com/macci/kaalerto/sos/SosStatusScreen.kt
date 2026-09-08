package com.macci.kaalerto.sos

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.mesh.MeshStatus

/**
 * design/artboards/SOSStatus.dc.html — what the requester watches while the request is
 * out.
 *
 * The headline is [SosState]'s own requester-facing text from
 * `docs/03-architecture.md` §6.2's table, not a spinner and not a claim of success.
 * Below it, one row per escalation channel, each saying what is actually true — which
 * for two of the three is "there is no code for this yet". See [sosChannelRows].
 */
@Composable
fun SosStatusScreen(
    snapshot: SosSnapshot,
    meshStatus: MeshStatus,
    elapsedLabel: String,
    onMarkSafe: () -> Unit,
    onShowRescueCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SosColors.Background),
    ) {
        SosLiveBanner(
            title = if (snapshot.isActive) tr("Aktibo ang SOS mo", "Your SOS is active") else tr("Sarado na ang SOS mo", "Your SOS is closed"),
            subtitle = elapsedLabel,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            val channels = sosChannelRows(meshStatus)
            HeadlineRow(snapshot.state, channels.anyBroadcasting())

            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                SectionLabel(tr("BAWAT DAAN", "EVERY CHANNEL"))
                Spacer(Modifier.size(11.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    channels.forEach { ChannelRow(it) }
                }
            }

            PayloadPanel(
                snapshot = snapshot,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )

            // §6.4.3: the rescue card is a state, not a tap — it opens on its own at
            // UNREACHABLE. This button exists anyway, because a rescuer may be at the
            // window before the threshold elapses and "wait 30 seconds" is not an
            // acceptable answer to that.
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(1.5.dp, SosColors.Border)
                    .clickable(onClick = onShowRescueCard),
                contentAlignment = Alignment.Center,
            ) {
                Text(tr("Ipakita ang rescue card", "Show the rescue card"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SosColors.PrimaryText)
            }

            // Lets the last control scroll clear of the fixed footer. Without it the
            // scroll area ends exactly at the button, so at rest the button is sliced
            // through its own label — which reads as a rendering fault rather than as
            // "there is more below", on the one screen that must not look broken.
            Spacer(Modifier.size(16.dp))
        }

        if (snapshot.isActive) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(SosColors.Surface)
                    .border(1.5.dp, SosColors.Border)
                    .clickable(onClick = onMarkSafe),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SafeShieldGlyph(SosColors.PrimaryText, Modifier.size(20.dp))
                Spacer(Modifier.size(9.dp))
                Text(tr("Ligtas na ako", "I'm safe"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SosColors.PrimaryText)
            }
            Text(
                tr("Patuloy ang pag-broadcast hangga't hindi mo ito isinasara.", "Broadcasting continues until you close this."),
                fontSize = 13.sp,
                color = SosColors.MutedText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
            )
        } else {
            Text(
                tr("Isinara mo ito. Hindi na ito ipinapadala.", "You closed this. It's no longer being sent."),
                fontSize = 13.sp,
                color = SosColors.MutedText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

/** §6.2's requester-facing text, verbatim, for the state this request is actually in. */
@Composable
private fun HeadlineRow(state: SosState, broadcasting: Boolean) {
    val (fil, en) = requesterText(state, broadcasting)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SosColors.Surface)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        BroadcastGlyph(SosColors.Mesh, Modifier.size(30.dp))
        Spacer(Modifier.size(13.dp))
        Text(tr(fil, en), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SosColors.PrimaryText)
    }
}

/**
 * The state table from `docs/03-architecture.md` §6.2, which is careful about a
 * specific thing: `UNREACHABLE` is never worded as a failure — never "failed".
 *
 * [broadcasting] splits that state in two, because it covers two situations a requester
 * needs to tell apart. With a live channel, the phone genuinely is calling out and
 * nobody has answered yet. With none — radios off, and SMS and server unbuilt — nothing
 * is transmitting, and saying "patuloy ang pag-broadcast" over three rows that all read
 * "hindi magamit" is the false progress §6.4.4 forbids. Neither wording is a failure;
 * only one of them is a claim the app cannot support.
 */
fun requesterText(state: SosState, broadcasting: Boolean = true): Pair<String, String> = when (state) {
    SosState.DRAFT, SosState.QUEUED ->
        "Naka-save. Sinusubukang ipadala…" to "Saved. Trying to send…"
    SosState.BEACONING ->
        "Walang signal. Tumatawag ang phone mo sa mga kalapit na phone." to "No signal. Your phone is calling out to nearby phones."
    SosState.UNREACHABLE ->
        if (broadcasting) {
            "Sinusubukan pa rin. Patuloy ang pag-broadcast ng phone mo." to
                "Still trying. Your phone keeps broadcasting."
        } else {
            "Walang maabot ngayon. Awtomatikong susubok ulit ang phone mo." to
                "Nothing to reach right now. Your phone will keep retrying."
        }
    SosState.RELAYED ->
        "May mga kalapit na phone na dala ang hiling mo." to "Nearby phones are carrying your request."
    SosState.DELIVERED ->
        "Nakarating sa rescue centre ang hiling mo." to "Your request reached the rescue centre."
    SosState.ACKNOWLEDGED ->
        "Nakita na ng barangay responder ang hiling mo." to "Barangay responder has seen your request."
    SosState.EN_ROUTE ->
        "Papunta na ang tulong." to "Help is on the way."
    SosState.ON_SCENE ->
        "Nandiyan na raw ang rescuer." to "Rescuers report they have arrived."
    SosState.RESCUED ->
        "Nasagip na." to "Marked as rescued."
    SosState.CANCELLED ->
        "Kinansela mo ito." to "You cancelled this."
    SosState.SAFE_SELF_RESOLVED ->
        "Sinabi mong ligtas ka na." to "You marked yourself safe."
}

@Composable
private fun ChannelRow(row: SosChannelRow) {
    val status = row.status
    val accent: Color = when (status) {
        is ChannelStatus.Broadcasting -> SosColors.Mesh
        is ChannelStatus.Unavailable -> SosColors.Warning
        is ChannelStatus.NotBuilt -> SosColors.MutedText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SosColors.Surface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (row.channel) {
            SosChannel.SERVER -> ServerGlyph(accent, Modifier.size(22.dp))
            SosChannel.SMS -> EnvelopeGlyph(accent, Modifier.size(22.dp))
            SosChannel.MESH -> com.macci.kaalerto.detail.MeshIcon(accent, Modifier.size(22.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.channel.label(),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                // A channel with no code behind it is dimmed as well as labelled, so it
                // reads as absent at a glance rather than only on close reading.
                color = if (status is ChannelStatus.NotBuilt) SosColors.MutedText else SosColors.PrimaryText,
            )
            Text(status.detail(), fontSize = 13.sp, color = SosColors.SecondaryText)
        }
        if (status is ChannelStatus.Broadcasting && status.peerCount > 0) {
            Text(
                status.peerCount.toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = SosColors.Mesh,
            )
        } else {
            Text(status.shortLabel(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent)
        }
    }
}

/** SOSStatus.dc.html's "Ipinadala" grid — exactly what went out, so nothing is a surprise. */
@Composable
private fun PayloadPanel(snapshot: SosSnapshot, modifier: Modifier = Modifier) {
    val context = snapshot.context
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SosColors.Surface)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        SectionLabel(tr("IPINADALA", "SENT"))
        PayloadPair(
            leftLabel = tr("Tao", "People"),
            leftValue = peopleSummary(context) ?: tr("Hindi sinabi", "Not stated"),
            rightLabel = tr("Tubig", "Water"),
            rightValue = waterSummary(context) ?: tr("Hindi sinabi", "Not stated"),
        )
        PayloadPair(
            leftLabel = tr("Medikal", "Medical"),
            leftValue = context.medical.joinToString(" · ").ifEmpty { tr("Hindi sinabi", "Not stated") },
            leftColor = if (context.hasMedicalNeed) SosColors.CriticalSoft else SosColors.PrimaryText,
            rightLabel = tr("Lokasyon", "Location"),
            rightValue = "%.4f\n%.4f".format(snapshot.lat, snapshot.lon),
            rightMono = true,
        )
    }
}

@Composable
private fun PayloadPair(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    leftColor: Color = SosColors.PrimaryText,
    rightMono: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        PayloadCell(leftLabel, leftValue, leftColor, false, Modifier.weight(1f))
        PayloadCell(rightLabel, rightValue, SosColors.PrimaryText, rightMono, Modifier.weight(1f))
    }
}

@Composable
private fun PayloadCell(label: String, value: String, color: Color, mono: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 13.sp, color = SosColors.SecondaryText)
        Text(
            value,
            fontSize = if (mono) 14.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = SosColors.MutedText,
    )
}

/** "5 · 2 bata" — the artboard's own compact form. */
fun peopleSummary(context: SosContext): String? {
    val people = context.people?.let(SosContext::peopleLabel)
        ?: return context.companions.joinToString(" · ").ifEmpty { null }
    val companions = context.companions.joinToString(" · ")
    return if (companions.isEmpty()) people else "$people · $companions"
}

/** "Dibdib, tumataas". */
fun waterSummary(context: SosContext): String? {
    val water = context.water ?: return context.trend
    val trend = context.trend ?: return water
    return "$water, ${trend.lowercase()}"
}
