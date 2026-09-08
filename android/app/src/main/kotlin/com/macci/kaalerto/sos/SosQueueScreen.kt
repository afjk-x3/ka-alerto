package com.macci.kaalerto.sos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.data.haversineMeters
import com.macci.kaalerto.detail.CheckIcon
import com.macci.kaalerto.detail.MeshIcon
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import kotlin.math.roundToInt

/**
 * design/artboards/QueueVolunteer.dc.html — the responder's list.
 *
 * The tiering in the artboard's own footer is the rule this screen follows: "Rehistradong
 * volunteer: lokasyon at bilang ng tao. Ang detalyeng medikal ay hawak ng barangay
 * official." So a responder sees the exact position and the people count, and does not
 * see medical detail — which, for anything relayed in over the mesh, they could not see
 * anyway, because it was never sent (sos/SosMeshPolicy.kt).
 *
 * Two acknowledgement buttons, matching the artboard: "Nakita ko" and "Nakita ko —
 * papunta na". Both write a `sos_state` event that travels back over the mesh exactly
 * like the request came in, so the originator's screen updates with no server anywhere.
 */
@Composable
fun SosQueueScreen(
    incidents: List<SosIncident>,
    myLat: Double?,
    myLon: Double?,
    onAcknowledge: (String) -> Unit,
    onEnRoute: (String) -> Unit,
    /** True for the official tier — QueueOfficial.dc.html's extra column of judgement. */
    isOfficial: Boolean = false,
    onMarkFalseAlarm: ((SosIncident) -> Unit)? = null,
    onUndoFalseAlarm: ((SosIncident) -> Unit)? = null,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    val open = incidents.flatMap { it.all }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        // Only the title/count row is fixed chrome now. The mesh-delivery note and the
        // medical-privacy note are both genuinely short, but pinning them permanently
        // cost the same vertical space on a queue of 2 requests as a queue of 20 — they
        // scroll with the list instead, and "Isara" moved up here so closing the queue
        // doesn't require a fixed footer to hang it on.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 16.dp, end = 8.dp, top = 38.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.macci.kaalerto.nav.HamburgerButton(
                onClick = onOpenMenu,
                modifier = Modifier.padding(end = 10.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    tr("Humihingi ng tulong", "Requesting help"),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${tr(if (isOfficial) "Kagawad" else "Responder", if (isOfficial) "Official" else "Responder")} · ${com.macci.kaalerto.demo.DemoArea.BARANGAY_NAME}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier.background(SosColors.Critical).padding(horizontal = 13.dp, vertical = 7.dp),
            ) {
                Text(
                    open.size.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = SosColors.CardBackground,
                )
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = tr("Isara", "Close"))
            }
        }

        val viaMesh = open.count { it.arrivedByMesh }

        if (open.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        tr("Walang humihingi ng tulong ngayon.", "No one is requesting help right now."),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MedicalPrivacyNote()
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (viaMesh > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.recessedSurface)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeshIcon(SosColors.Mesh, Modifier.size(17.dp))
                        Spacer(Modifier.size(9.dp))
                        Text(
                            tr(
                                "$viaMesh sa mga ito ay dumating via mesh — walang internet sa pinanggalingan",
                                "$viaMesh of these arrived via mesh — no internet where they came from",
                            ),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                incidents.forEach { incident ->
                    val request = incident.primary
                    RequestCard(
                        request = request,
                        incident = incident,
                        isOfficial = isOfficial,
                        distanceMeters = if (myLat != null && myLon != null) {
                            haversineMeters(myLat, myLon, request.lat, request.lon)
                        } else {
                            null
                        },
                        onAcknowledge = { onAcknowledge(request.sosId) },
                        onEnRoute = { onEnRoute(request.sosId) },
                        onMarkFalseAlarm = onMarkFalseAlarm?.let { act -> { act(incident) } },
                        onUndoFalseAlarm = onUndoFalseAlarm?.let { act -> { act(incident) } },
                    )
                }
                Spacer(Modifier.size(8.dp))
                MedicalPrivacyNote()
            }
        }
    }
}

@Composable
private fun MedicalPrivacyNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SafeShieldGlyph(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Text(
            tr(
                "Lokasyon at bilang ng tao. Hindi ipinapasa sa mesh ang detalyeng medikal.",
                "Location and headcount. Medical detail is not passed over the mesh.",
            ),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RequestCard(
    request: SosSnapshot,
    incident: SosIncident,
    isOfficial: Boolean,
    distanceMeters: Double?,
    onAcknowledge: () -> Unit,
    onEnRoute: () -> Unit,
    onMarkFalseAlarm: (() -> Unit)?,
    onUndoFalseAlarm: (() -> Unit)?,
) {
    val colors = LocalKaAlertoColors.current
    val claimed = request.state.rank >= SosState.ACKNOWLEDGED.rank
    val accent = if (claimed) colors.safeFg else SosColors.Critical

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (claimed) colors.safeBg else MaterialTheme.colorScheme.background)
            .border(1.dp, colors.border)
            .padding(start = 0.dp),
    ) {
        Row {
            Box(Modifier.size(width = 4.dp, height = 1.dp))
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, top = 10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "%.4f, %.4f".format(request.lat, request.lon),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            metaLine(request, distanceMeters),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!claimed) {
                        Box(Modifier.background(SosColors.Critical).padding(horizontal = 9.dp, vertical = 5.dp)) {
                            Text(tr("BAGO", "NEW"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SosColors.CardBackground)
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, top = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    // Separate chips rather than peopleSummary(): that helper puts the
                    // count and the companions in one string for the status screen,
                    // whose column is already labelled "Tao". Reusing it here produced
                    // "5-8 - Bata - Matanda tao", with the unit stranded at the end.
                    request.context.people?.let { Chip("${SosContext.peopleLabel(it)} " + tr("tao", "people")) }
                    request.context.companions.takeIf { it.isNotEmpty() }?.let { Chip(it.joinToString(" · ")) }
                    waterSummary(request.context)?.let { Chip(it) }
                    if (request.context.isEmpty) Chip(tr("Walang dagdag na detalye", "No further detail"))
                }

                if (incident.size > 1) NearbyReportsNote(incident)
                if (incident.falseAlarm != null) {
                    FalseAlarmBanner(incident, isOfficial, onUndoFalseAlarm)
                } else if (isOfficial && incident.priorFalseAlarms > 0) {
                    PriorMarksNote(incident.priorFalseAlarms)
                }

                if (claimed) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckIcon(accent, Modifier.size(18.dp))
                        Spacer(Modifier.size(9.dp))
                        Text(
                            claimedLabel(request),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                        )
                    }
                    // Acknowledged is not the end: the artboard's own next step is
                    // "papunta na", and a request that is seen but unattended is
                    // docs/03-architecture.md §6.5's worst failure mode.
                    if (request.state.rank < SosState.EN_ROUTE.rank) {
                        AckButton(tr("Papunta na ako", "I'm on my way"), filled = false, onClick = onEnRoute)
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(start = 13.dp, end = 13.dp, top = 11.dp, bottom = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AckButton(tr("Nakita ko — papunta na", "I've seen it — on my way"), filled = true, onClick = onEnRoute)
                        AckButton(tr("Nakita ko", "I've seen it"), filled = false, onClick = onAcknowledge)
                    }
                }

                // Last, and visually quietest, on purpose: the card's job is to get
                // someone dispatched. Marking is the exception, not the default read.
                if (isOfficial && incident.falseAlarm == null && onMarkFalseAlarm != null) {
                    Column(
                        modifier = Modifier.padding(start = 13.dp, end = 13.dp, bottom = 13.dp),
                    ) {
                        AckButton(tr("Markahan: walang emergency", "Mark: not an emergency"), filled = false, onClick = onMarkFalseAlarm)
                    }
                }
            }
        }
    }
}

/**
 * QueueOfficial.dc.html's "3 ulat mula sa iisang bahay — isang insidente", with the
 * claim it can actually support.
 *
 * The artboard says *house*; GPS cannot. Accuracy on this build has been seen between
 * ±5 m and ±100 m, so the note states the radius and the worst accuracy in the group and
 * lets the official decide. Every grouped request is listed underneath — a queue that
 * hid one because something nearby looked like it would be the worst bug this screen
 * could have.
 */
@Composable
private fun NearbyReportsNote(incident: SosIncident) {
    val colors = LocalKaAlertoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 13.dp, end = 13.dp, top = 10.dp)
            .background(colors.recessedSurface)
            .padding(11.dp),
    ) {
        Text(
            tr(
                "${incident.size} ulat sa loob ng ${SAME_INCIDENT_RADIUS_M.toInt()} m — maaaring iisang insidente",
                "${incident.size} reports within ${SAME_INCIDENT_RADIUS_M.toInt()} m — could be one incident",
            ),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        incident.worstAccuracyM?.let {
            Text(
                tr(
                    "Hanggang ±${it.roundToInt()} m ang tiyak ng lokasyon — maaari itong magkahiwalay na bahay.",
                    "Location accuracy up to ±${it.roundToInt()} m — these could be separate houses.",
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(6.dp))
        incident.all.forEach { request ->
            Text(
                "· ${"%.4f, %.4f".format(request.lat, request.lon)} · ${request.context.people?.let(SosContext::peopleLabel) ?: "?"} " + tr("tao", "people"),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A standing mark, always named to whoever made it, and liftable by any official. */
@Composable
private fun FalseAlarmBanner(
    incident: SosIncident,
    isOfficial: Boolean,
    onUndo: (() -> Unit)?,
) {
    val colors = LocalKaAlertoColors.current
    val mark = incident.falseAlarm ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 13.dp, end = 13.dp, top = 10.dp)
            .background(colors.warningBg)
            .padding(11.dp),
    ) {
        Text(
            tr("Minarkahan: walang emergency", "Marked: not an emergency"),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.warningFg,
        )
        Text(
            tr(
                "${mark.byName} · ${timeFormat.format(java.util.Date(mark.atMs))}. " +
                    "Nasa ibaba ito ng listahan — hindi tinanggal.",
                "${mark.byName} · ${timeFormat.format(java.util.Date(mark.atMs))}. " +
                    "This sits lower in the list — it hasn't been removed.",
            ),
            fontSize = 12.sp,
            color = colors.warningFg,
        )
        if (isOfficial && onUndo != null) {
            Spacer(Modifier.size(8.dp))
            AckButton(tr("Bawiin ang marka", "Undo the mark"), filled = false, onClick = onUndo)
        }
    }
}

/**
 * The artboard's "bababa ang pagkakasunod ng susunod na request ng device na ito", shown
 * rather than applied silently. A responder looking at a demoted request is entitled to
 * know it is demoted, and that the demotion is about the device's history and not about
 * this request, which nobody has judged.
 */
@Composable
private fun PriorMarksNote(priorFalseAlarms: Int) {
    Text(
        tr(
            "$priorFalseAlarms naunang maling alarma mula sa device na ito. Nasa ibaba ito ng " +
                "listahan — pero hindi pa nasusuri ang request na ito.",
            "$priorFalseAlarms prior false alarm(s) from this device. This sits lower in the " +
                "list — but this request itself hasn't been reviewed yet.",
        ),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 13.dp, end = 13.dp, top = 10.dp),
    )
}

private val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)

@Composable
private fun AckButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
            .then(if (filled) Modifier else Modifier.border(1.5.dp, colors.borderEmphasis))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (filled) {
            CheckIcon(MaterialTheme.colorScheme.onPrimary, Modifier.size(19.dp))
            Spacer(Modifier.size(9.dp))
        }
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.SemiBold,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun Chip(text: String) {
    Box(
        modifier = Modifier
            .background(LocalKaAlertoColors.current.recessedSurface)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** "6 min · 340 m · 2 hops" — the artboard's own monospace meta line. */
private fun metaLine(request: SosSnapshot, distanceMeters: Double?): String = buildList {
    add(elapsedLabel(request.startedAtMs, System.currentTimeMillis()))
    distanceMeters?.let { add("${it.roundToInt()} m") }
    if (request.hopCount > 0) add("${request.hopCount} hops")
}.joinToString(" · ")

@Composable
private fun claimedLabel(request: SosSnapshot): String {
    val name = request.claimedByName ?: tr("isang responder", "a responder")
    return if (request.state.rank >= SosState.EN_ROUTE.rank) {
        tr("Papunta na si $name", "$name is on the way")
    } else {
        tr("Nakita ni $name", "Seen by $name")
    }
}
