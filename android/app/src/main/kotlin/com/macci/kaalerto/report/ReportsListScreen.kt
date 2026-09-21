package com.macci.kaalerto.report

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.data.FeatureSummary
import com.macci.kaalerto.data.severityTextFor
import com.macci.kaalerto.detail.ageLabel
import com.macci.kaalerto.detail.bucketLabel
import com.macci.kaalerto.i18n.LocalAppLanguage
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.location.BundledPlaces
import com.macci.kaalerto.nav.HamburgerButton
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import com.macci.kaalerto.ui.theme.SeverityColors

/**
 * "Mga ulat": every flooded spot this phone knows about, as a list — so a report can be found without
 * hunting for its marker, overlapping markers can be told apart, and someone who cannot see the map has a way
 * in (docs review A3/A6). It is a read-only view of the same local fold the map draws, so it works offline.
 *
 * Nearest first when this phone has a position, otherwise newest first; expired spots always last. A tap
 * goes back to the map with that spot's sheet open, where Confirm, Dispute, Withdraw and Routes already live.
 */
@Composable
fun ReportsListScreen(
    summaries: List<FeatureSummary>,
    fromLat: Double?,
    fromLon: Double?,
    onOpen: (FeatureSummary) -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val colors = LocalKaAlertoColors.current
    var sort by remember { mutableStateOf(if (fromLat != null) ReportSort.NEAREST else ReportSort.NEWEST) }
    // Once a position turns up, nearest is the better default; a person's own choice is left alone after that.
    var chosen by remember { mutableStateOf(false) }
    LaunchedEffect(fromLat != null) { if (!chosen && fromLat != null) sort = ReportSort.NEAREST }

    // Street or landmark names come from the bundled demo-area gazetteer, so they work offline; elsewhere the
    // row shows coordinates rather than a name that would need the network.
    var gazetteer by remember { mutableStateOf<com.macci.kaalerto.location.Gazetteer?>(null) }
    LaunchedEffect(Unit) { gazetteer = BundledPlaces.get(context) }

    val rows = remember(summaries, fromLat, fromLon, sort) { sortReportRows(reportRows(summaries, fromLat, fromLon), sort) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            HamburgerButton(onClick = onOpenMenu, modifier = Modifier.padding(end = 12.dp, top = 3.dp))
            Column {
                Text(tr("Mga ulat", "Reports"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    tr("Nasa phone mo na ito · gumagana kahit offline", "Already on your phone · works offline"),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortChip(tr("Pinakamalapit", "Nearest"), sort == ReportSort.NEAREST, enabled = fromLat != null) {
                chosen = true
                sort = ReportSort.NEAREST
            }
            SortChip(tr("Pinakabago", "Newest"), sort == ReportSort.NEWEST) {
                chosen = true
                sort = ReportSort.NEWEST
            }
            Spacer(Modifier.weight(1f))
            Text(tr("${rows.size} lugar", "${rows.size} spot${if (rows.size == 1) "" else "s"}"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (rows.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Wala pang ulat sa lugar mo.", "No reports for your area yet."), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    tr(
                        "Lalabas dito ang mga ulat kapag dumating ang mga ito. Hindi ibig sabihin nito na walang baha.",
                        "Reports show up here as they arrive. This does not mean there is no flooding.",
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(rows, key = { it.summary.featureRef }) { row ->
                    val place = gazetteer?.describe(row.summary.lat, row.summary.lon, language)?.primary
                    ReportRowView(row, place, language) { onOpen(row.summary) }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                }
            }
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalKaAlertoColors.current
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.onBackground else colors.border)
            .selectable(selected = selected, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                selected -> MaterialTheme.colorScheme.background
                enabled -> MaterialTheme.colorScheme.onBackground
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ReportRowView(row: ReportRow, place: String?, language: com.macci.kaalerto.i18n.AppLanguage, onClick: () -> Unit) {
    val s = row.summary
    val stale = s.isStale
    val conflicted = s.isConflicted
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // An expired spot is grey, not its old tier: its severity is what someone once said, not what is true now.
    val block = if (stale) Color(android.graphics.Color.parseColor(SeverityColors.UNKNOWN)) else Color(android.graphics.Color.parseColor(SeverityColors.forSeverity(s.severity)))
    val tier = if (stale) "·" else if (conflicted) "!" else s.severity
    val title = when {
        stale -> tr("Luma na — kailangang tingnan", "Expired — needs a fresh look")
        conflicted -> tr("Magkasalungat ang mga ulat", "Reports disagree")
        else -> severityTextFor(s.severity).let { (fil, en) -> tr(fil, en) }
    }
    val sureness = if (conflicted) tr("Hindi pa malinaw", "Not settled") else bucketLabel(s.bucket, language)
    val reports = s.events.count { it.type == "flood_report" }
    val counts = buildString {
        append(tr("$reports ulat", "$reports report${if (reports == 1) "" else "s"}"))
        if (s.confirmCount > 0) append(" · ").append(tr("${s.confirmCount} kumpirma", "${s.confirmCount} confirmed"))
        if (s.disputeCount > 0) append(" · ").append(tr("${s.disputeCount} tumutol", "${s.disputeCount} disputed"))
    }
    val where = place ?: "%.4f, %.4f".format(s.lat, s.lon)
    val age = ageLabel(System.currentTimeMillis() - s.lastEventMs, language)
    val spoken = "$title. $where. ${row.distanceMeters?.let { shortDistance(it) }.orEmpty()}. $sureness. $counts. $age"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = spoken }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(44.dp).background(block), contentAlignment = Alignment.Center) {
            Text(tier, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (stale) muted else MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(where, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (stale) "$counts · $age" else "$sureness · $counts · $age",
                fontSize = 12.sp,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        row.distanceMeters?.let {
            Spacer(Modifier.width(8.dp))
            Text(shortDistance(it), fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = if (stale) muted else MaterialTheme.colorScheme.onBackground)
        }
    }
}
