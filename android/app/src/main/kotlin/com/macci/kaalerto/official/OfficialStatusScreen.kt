package com.macci.kaalerto.official

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.FeatureSummary
import com.macci.kaalerto.data.severityTextFor
import com.macci.kaalerto.demo.DemoArea
import com.macci.kaalerto.detail.CheckIcon
import com.macci.kaalerto.detail.MeshIcon
import com.macci.kaalerto.detail.WarningTriangleIcon
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import com.macci.kaalerto.ui.theme.SeverityColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * design/artboards/OfficialVerify.dc.html and OfficialReverse.dc.html, which are the
 * same screen in two states.
 *
 * Three things it is careful about, all lifted from the artboards' own copy:
 *
 *  - **The crowd is shown, not replaced.** "Hindi binubura ng opisyal na status ang mga
 *    ito. Makikita pa rin ng residente ang dalawa." The resident reports stay listed
 *    above the ruling, including the one that disagrees.
 *  - **The action is attributed.** "Makikita ng lahat kung sino ang nag-post." An
 *    official act is never anonymous, so the signing strip names who it will go out as.
 *  - **Lowering a contradicted spot is gated.** See `data/Reducer.kt`'s
 *    `REQUIRED_OFFICIALS_TO_DEESCALATE`. The screen shows the gate *before* the post so
 *    the official knows it will wait, rather than discovering afterwards that nothing
 *    changed.
 *
 * One line of the artboard is deliberately **changed**: its footer says "nilalagdaan sa
 * phone" — signed on the phone — which is `docs/03-architecture.md` §2.5's signed
 * events. Ground rule 4 means nothing here is signed, so the copy says what is true: it
 * carries the official's name and role, and spreads over the mesh.
 */
@Composable
fun OfficialStatusScreen(
    summary: FeatureSummary,
    officialName: String,
    onPost: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    var chosen by remember { mutableStateOf(summary.officialSeverity ?: summary.severity.takeIf { it != "SX" }) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // The gate as the official will experience it: does the choice they have selected
    // lower a spot that people are currently reporting as worse?
    val wouldBeGated = chosen != null &&
        com.macci.kaalerto.data.severityOrdinal(chosen!!) < com.macci.kaalerto.data.severityOrdinal(summary.severity.takeIf { it != "SX" } ?: "S3") &&
        summary.contradictingCount >= 2

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 38.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Opisyal na status",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "%.4f, %.4f · %s".format(summary.lat, summary.lon, DemoArea.BARANGAY_NAME),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.onBackground)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text(
                    "KAGAWAD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.background,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NowOnMap(summary)

            if (summary.officialSeverity != null) ExistingOfficial(summary, timeFormat)

            if (summary.contradictingCount > 0) ContradictionNote(summary)

            if (wouldBeGated) SecondOfficialGate()

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("MANANATILI ANG ULAT NG RESIDENTE")
                summary.events
                    .filter { it.authorRole != "official" && it.severity != null }
                    .take(4)
                    .forEach { ResidentReportRow(it, timeFormat) }
                Text(
                    "Hindi binubura ng opisyal na status ang mga ito. Makikita pa rin ng residente ang lahat.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SectionLabel("ANO ANG OPISYAL NA SASABIHIN MO?")
                OFFICIAL_OPTIONS.forEach { severity ->
                    val (fil, en) = severityTextFor(severity)
                    OptionRow(
                        severity = severity,
                        fil = fil,
                        en = en,
                        selected = chosen == severity,
                        onClick = { chosen = severity },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 11.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onBackground)
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Ipo-post bilang $officialName",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.background,
                )
                Text(
                    "${DemoArea.BARANGAY_NAME} · makikita ng lahat kung sino ang nag-post",
                    fontSize = 11.sp,
                    color = colors.borderEmphasis,
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                .fillMaxWidth()
                .height(56.dp)
                .background(if (chosen == null) colors.borderEmphasis else MaterialTheme.colorScheme.primary)
                .clickable(enabled = chosen != null) { chosen?.let(onPost) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (wouldBeGated) "I-post — maghihintay ng pangalawa" else "I-post ang opisyal na status",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeshIcon(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(15.dp))
            Spacer(Modifier.size(7.dp))
            // The artboard says "nilalagdaan sa phone" — signed. Nothing is signed in
            // this build (ground rule 4), so this says only what is actually true.
            Text(
                "Gumagana kahit walang signal — dala ang pangalan at puwesto mo, kumakalat sa mesh",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("Kanselahin", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
    }
}

/**
 * The three rulings an official can post, from OfficialVerify.dc.html's own list.
 * S2 is absent for the same reason the artboard omits it: an official standing at a road
 * is making a passable / not-passable / cleared call, not grading a depth scale.
 */
private val OFFICIAL_OPTIONS = listOf("S3", "S1", "S0")

@Composable
private fun NowOnMap(summary: FeatureSummary) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.recessedSurface)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 10.dp, height = 40.dp).background(severityComposeColor(summary.severity)))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            SectionLabel("NGAYON SA MAPA")
            val (fil, _) = severityTextFor(summary.severity)
            Text(
                "${summary.severity} · $fil",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Galing sa residente · ${summary.confirmCount} nagkumpirma",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExistingOfficial(summary: FeatureSummary, timeFormat: SimpleDateFormat) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, colors.border)
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Box(Modifier.size(width = 4.dp, height = 46.dp).background(colors.safeFg))
        Spacer(Modifier.size(11.dp))
        Column {
            SectionLabel(
                if (summary.pendingSecondOfficial) "OPISYAL NA STATUS — NAKABINBIN" else "KASALUKUYANG OPISYAL NA STATUS",
            )
            val (fil, _) = severityTextFor(summary.officialSeverity!!)
            Text(fil, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "${summary.officialAuthorName.orEmpty()} · ${summary.officialAtMs?.let { timeFormat.format(Date(it)) }.orEmpty()}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** OfficialReverse.dc.html's "3 residente ang salungat dito". */
@Composable
private fun ContradictionNote(summary: FeatureSummary) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.criticalBg)
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        WarningTriangleIcon(colors.criticalFg, Modifier.size(22.dp))
        Spacer(Modifier.size(11.dp))
        Column {
            Text(
                "${summary.contradictingCount} residente ang salungat dito",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.criticalFg,
            )
            Text(
                "Mas malala pa rin daw ang lagay kaysa sa opisyal na status.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** OfficialReverse.dc.html's "Kailangan ng pangalawang opisyal" panel. */
@Composable
private fun SecondOfficialGate() {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warningBg)
            .border(1.5.dp, colors.warningFg.copy(alpha = 0.35f))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Column {
            Text(
                "Kailangan ng pangalawang opisyal",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.warningFg,
            )
            Text(
                "Magkasalungat ang lugar na ito, kaya hindi kayang ibaba ng iisang opisyal ang severity. Mananatili ang ulat ng residente hangga't walang pangalawang opisyal na sasang-ayon.",
                fontSize = 12.sp,
                color = colors.warningFg,
            )
        }
    }
}

@Composable
private fun ResidentReportRow(event: Event, timeFormat: SimpleDateFormat) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, colors.border)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 3.dp, height = 22.dp).background(severityComposeColor(event.severity ?: "S0")))
        Spacer(Modifier.size(10.dp))
        Text(
            timeFormat.format(Date(event.timestampMs)),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            severityTextFor(event.severity ?: "S0").first,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun OptionRow(severity: String, fil: String, en: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalKaAlertoColors.current
    val accent = severityComposeColor(severity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) accent else colors.borderEmphasis)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(if (selected) accent else MaterialTheme.colorScheme.background, CircleShape)
                .border(2.dp, if (selected) accent else colors.borderEmphasis, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) CheckIcon(MaterialTheme.colorScheme.background, Modifier.size(13.dp))
        }
        Spacer(Modifier.size(11.dp))
        Column {
            Text(
                fil,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(en, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Bridges [SeverityColors]' hex strings to a Compose colour. */
private fun severityComposeColor(severity: String): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(SeverityColors.forSeverity(severity)))

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
