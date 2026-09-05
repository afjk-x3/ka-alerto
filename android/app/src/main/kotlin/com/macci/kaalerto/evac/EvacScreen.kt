package com.macci.kaalerto.evac

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.detail.MeshIcon
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * design/artboards/EvacCentres-Normal.dc.html — nearest first, and everything already on
 * the phone ("nasa phone mo na ito").
 *
 * Three things the artboard shows that this screen does **not**, because the data behind
 * them does not exist:
 *
 *  - **"Ituro ang daan"** (directions). Routing is build day 11; a button that cannot
 *    route is worse than no button.
 *  - **"1 baha sa ruta"**. Same — that is the day 11 route check.
 *  - **Facility chips** (Kuryente / Tubig / PWD access). Not in
 *    `assets/evacuation_centres.json`, and nobody has validated that those three are the
 *    right list for a real barangay, so inventing them here would be fiction on a screen
 *    people would walk somewhere because of.
 *
 * The capacity figure is shown but labelled: the fixture's own
 * `capacityEstimateSource` reads "PLACEHOLDER — not verified against any barangay or
 * DepEd figure", and a number nobody checked must not be rendered as if somebody had.
 */
@Composable
fun EvacScreen(
    states: List<EvacState>,
    isOfficial: Boolean,
    onUpdate: (centreId: String, status: EvacStatus, occupancy: Int?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    var editing by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 12.dp),
        ) {
            Text(
                "Evacuation centre",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Pinakamalapit muna · nasa phone mo na ito",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (states.isEmpty()) {
                Text(
                    "Walang evacuation centre sa fixture.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            states.forEach { state ->
                CentreCard(
                    state = state,
                    isOfficial = isOfficial,
                    editing = editing == state.centre.id,
                    onToggleEdit = { editing = if (editing == state.centre.id) null else state.centre.id },
                    onUpdate = { status, occupancy ->
                        onUpdate(state.centre.id, status, occupancy)
                        editing = null
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeshIcon(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(17.dp))
            Spacer(Modifier.size(10.dp))
            Text(
                "In-update ng barangay, kumakalat sa mesh. Tantiya lang ang kapasidad — hindi pa napapatunayan.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.clickable(onClick = onBack).padding(8.dp)) {
                Text("Isara", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
private fun CentreCard(
    state: EvacState,
    isOfficial: Boolean,
    editing: Boolean,
    onToggleEdit: () -> Unit,
    onUpdate: (EvacStatus, Int?) -> Unit,
) {
    val colors = LocalKaAlertoColors.current
    val open = state.status != EvacStatus.NOT_OPEN
    val accent = statusAccent(state.status, colors.safeFg, colors.warningFg, colors.border)
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, colors.border)
            .padding(15.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(width = 4.dp, height = 46.dp).background(accent))
            Spacer(Modifier.size(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.centre.name,
                    fontSize = 19.sp,
                    fontWeight = if (open) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (open) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    state.distanceMeters?.let { formatDistance(it) } ?: "Hindi alam ang layo",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .background(statusChipBackground(state.status, colors.safeBg, colors.warningBg, colors.recessedSurface))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    state.status.fil,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.status == EvacStatus.NOT_OPEN) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                )
            }
        }

        val fraction = state.occupancyFraction
        if (fraction != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(colors.recessedSurface),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .background(accent),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    "${state.occupancy} / ${state.centre.capacityEstimate}",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // The fixture flags this figure as unverified, so the screen does too — short
        // here because it repeats on every card, with the full caveat once in the footer.
        state.centre.capacityEstimate?.let {
            Text(
                "Kapasidad $it (tantiya)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (state.updatedAtMs != null) {
            Text(
                "In-update ni ${state.updatedByName.orEmpty()} · ${timeFormat.format(Date(state.updatedAtMs))}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (isOfficial) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.5.dp, colors.borderEmphasis)
                    .clickable(onClick = onToggleEdit),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (editing) "Isara" else "I-update ang status",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            if (editing) OfficialControls(state, onUpdate)
        }
    }
}

/**
 * The official's update. Occupancy steps rather than a keyboard: an official doing this
 * in a flood is standing in a doorway counting people, not typing a precise figure, and
 * a number field is one more thing to fumble.
 */
@Composable
private fun OfficialControls(state: EvacState, onUpdate: (EvacStatus, Int?) -> Unit) {
    val colors = LocalKaAlertoColors.current
    var occupancy by remember(state.centre.id) { mutableIntStateOf(state.occupancy ?: 0) }
    val step = 10

    Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("−$step") { occupancy = (occupancy - step).coerceAtLeast(0) }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    occupancy.toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text("tao ngayon", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(10.dp))
            StepButton("+$step") { occupancy += step }
        }

        EvacStatus.values().forEach { status ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(
                        if (status == state.status) colors.recessedSurface else MaterialTheme.colorScheme.background,
                    )
                    .border(1.5.dp, colors.borderEmphasis)
                    .clickable { onUpdate(status, occupancy.takeIf { status != EvacStatus.NOT_OPEN }) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    status.fil,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val colors = LocalKaAlertoColors.current
    Box(
        modifier = Modifier
            .size(52.dp)
            .border(1.5.dp, colors.borderEmphasis)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}

private fun statusAccent(status: EvacStatus, safe: Color, warning: Color, muted: Color): Color = when (status) {
    EvacStatus.ACCEPTING -> safe
    EvacStatus.NEARLY_FULL -> warning
    EvacStatus.NOT_OPEN -> muted
}

private fun statusChipBackground(status: EvacStatus, safeBg: Color, warningBg: Color, muted: Color): Color = when (status) {
    EvacStatus.ACCEPTING -> safeBg
    EvacStatus.NEARLY_FULL -> warningBg
    EvacStatus.NOT_OPEN -> muted
}

/**
 * "650 m" / "1.1 km", plus the artboard's walking estimate at a deliberately slow
 * 4 km/h — an evacuation walk is carrying children through water, not a stroll.
 */
fun formatDistance(meters: Double): String {
    val distance = if (meters < 1_000) "${(meters / 10).roundToInt() * 10} m" else "%.1f km".format(meters / 1_000)
    val minutes = (meters / (4_000.0 / 60)).roundToInt()
    return "$distance · $minutes min lakad"
}
