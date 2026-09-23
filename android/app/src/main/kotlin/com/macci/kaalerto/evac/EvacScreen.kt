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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import com.macci.kaalerto.i18n.tr
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
    /** The official's own municipality (their profile): they manage only its shelters. Blank until set. */
    municipality: String = "",
    onAddShelter: () -> Unit = {},
    onRemove: (EvacCentre) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onUpdate: (centreId: String, status: EvacStatus, occupancy: Int?) -> Unit,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    /**
     * A card tap. Real turn-by-turn routing is day 11 and not built (see the class doc
     * above); this is the buildable slice — jump to the map with the camera centered on
     * that centre's pin, same one-shot mechanism `Screen.PickHome` already uses for its
     * own starting camera position.
     */
    onCentreClick: (EvacCentre) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    var editing by remember { mutableStateOf<String?>(null) }

    // A resident sees only what they could actually walk to right now — an official
    // still sees every centre, open or not, because "not open" is exactly the state
    // they're here to change (OfficialControls below). Filtering this for officials
    // too would hide the one button that opens a closed centre.
    val visibleStates = if (isOfficial) states.filter { canManage(municipality, it.centre) } else states.filter { it.status != EvacStatus.NOT_OPEN }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Top,
        ) {
            com.macci.kaalerto.nav.HamburgerButton(
                onClick = onOpenMenu,
                modifier = Modifier.padding(end = 12.dp, top = 3.dp),
            )
            Column {
                Text(
                    tr("Mga silungan", "Evacuation centres"),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    tr("Pinakamalapit muna · nasa phone mo na ito", "Nearest first · already on your phone"),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isOfficial) {
                OfficialHeader(
                    municipality = municipality,
                    count = visibleStates.size,
                    onAddShelter = onAddShelter,
                    onOpenProfile = onOpenProfile,
                )
            }
            if (states.isEmpty()) {
                Text(
                    tr("Walang evacuation centre sa fixture.", "No evacuation centres in the fixture."),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (visibleStates.isEmpty() && !isOfficial) {
                // Not the same message as an empty fixture: centres exist, none are
                // open yet. A blank screen here would read as "nothing to see" when
                // it actually means "check back" — the failure the NOT_OPEN default
                // exists to avoid in the first place (see EvacCentre.kt's EvacState doc).
                Text(
                    tr("Wala pang bukas na silungan ngayon.", "No shelters are open yet."),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    tr(
                        "May ${states.size} silungan sa lugar mo, pero wala pang binuksan ang barangay. Susubaybayan ito at ipapakita agad kapag may nagbukas.",
                        "There ${if (states.size == 1) "is" else "are"} ${states.size} shelter${if (states.size == 1) "" else "s"} in your area, but the barangay hasn't opened one yet. This is watched and will show up as soon as one opens.",
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            visibleStates.forEach { state ->
                CentreCard(
                    state = state,
                    isOfficial = isOfficial,
                    editing = editing == state.centre.id,
                    onToggleEdit = { editing = if (editing == state.centre.id) null else state.centre.id },
                    onUpdate = { status, occupancy ->
                        onUpdate(state.centre.id, status, occupancy)
                        editing = null
                    },
                    onClick = { onCentreClick(state.centre) },
                    onRemove = { onRemove(state.centre) },
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
                tr(
                    "In-update ng barangay, kumakalat sa mesh. Tantiya lang ang kapasidad — hindi pa napapatunayan.",
                    "Updated by the barangay, spreads over the mesh. Capacity is only an estimate — not yet verified.",
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.clickable(onClick = onBack).padding(8.dp)) {
                Text(tr("Isara", "Close"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
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
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalKaAlertoColors.current
    val open = state.status != EvacStatus.NOT_OPEN
    val accent = statusAccent(state.status, colors.safeFg, colors.warningFg, colors.border)
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, colors.borderEmphasis)
            .clickable(onClick = onClick)
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
                    state.distanceMeters?.let { formatDistance(it) } ?: tr("Hindi alam ang layo", "Distance unknown"),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Where it is, in the words officials typed. A shelter an official added carries
                // its own municipality and barangay; the bundled four get the demo area's.
                val where = listOfNotNull(state.centre.barangay, state.centre.municipality).joinToString(", ")
                if (where.isNotEmpty()) {
                    Text(where, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(
                modifier = Modifier
                    .background(statusChipBackground(state.status, colors.safeBg, colors.warningBg, colors.recessedSurface))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    state.status.label(),
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
                tr("Kapasidad $it (tantiya)", "Capacity $it (estimate)"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (state.updatedAtMs != null) {
            Text(
                tr(
                    "In-update ni ${state.updatedByName.orEmpty()} · ${timeFormat.format(Date(state.updatedAtMs))}",
                    "Updated by ${state.updatedByName.orEmpty()} · ${timeFormat.format(Date(state.updatedAtMs))}",
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (isOfficial) {
            var confirmRemove by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // One tap to open or close; nearly-full and the head count stay under the button beside it.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(if (open) colors.recessedSurface else MaterialTheme.colorScheme.onBackground)
                        .clickable { onUpdate(if (open) EvacStatus.NOT_OPEN else EvacStatus.ACCEPTING, state.occupancy) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (open) tr("Isara", "Close it") else tr("Buksan", "Open it"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (open) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .border(1.5.dp, colors.borderEmphasis)
                        .clickable(onClick = onToggleEdit),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (editing) tr("Tapos na", "Done") else tr("I-update ang status", "Update the status"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            if (editing) OfficialControls(state, onUpdate)
            if (state.centre.custom) {
                Text(
                    tr("Alisin ang silungan", "Remove this shelter"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.criticalFg,
                    modifier = Modifier.clickable { confirmRemove = true }.padding(top = 10.dp, bottom = 2.dp),
                )
            }
            if (confirmRemove) {
                AlertDialog(
                    onDismissRequest = { confirmRemove = false },
                    title = { Text(tr("Alisin ang silungan?", "Remove this shelter?")) },
                    text = {
                        Text(
                            tr(
                                "Mawawala ito sa listahan ng lahat. Hindi ito maaalis sa phone ng iba kung hindi pa nila natatanggap ito.",
                                "It disappears from everyone's list. A phone that has not received this yet keeps showing it until it does.",
                            ),
                        )
                    },
                    confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text(tr("Alisin", "Remove")) } },
                    dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(tr("Kanselahin", "Cancel")) } },
                )
            }
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
                Text(tr("tao ngayon", "people now"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    status.label(),
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

/** Past this a walking time is not a real estimate (a shelter in another province is not "3,584 min lakad"). */
const val MAX_WALK_ESTIMATE_METERS = 5_000.0

/** The artboard's walking estimate at a deliberately slow 4 km/h, or null when the shelter is too far to walk to. */
fun walkingMinutes(meters: Double): Int? =
    if (meters > MAX_WALK_ESTIMATE_METERS) null else (meters / (4_000.0 / 60)).roundToInt()

/**
 * "650 m" / "1.1 km", plus [walkingMinutes] at a deliberately slow 4 km/h — an evacuation walk is carrying
 * children through water, not a stroll. Beyond a walkable distance only the distance is shown.
 */
@Composable
fun formatDistance(meters: Double): String {
    val distance = if (meters < 1_000) "${(meters / 10).roundToInt() * 10} m" else "%.1f km".format(meters / 1_000)
    val minutes = walkingMinutes(meters) ?: return distance
    return "$distance · " + tr("$minutes min lakad", "$minutes min walk")
}

/**
 * The top of an official's list: whose shelters these are, and how to add one. Without a municipality on
 * the profile an official manages none, so this points there instead.
 */
@Composable
private fun OfficialHeader(municipality: String, count: Int, onAddShelter: () -> Unit, onOpenProfile: () -> Unit) {
    val colors = LocalKaAlertoColors.current
    if (municipality.isBlank()) {
        Column(
            modifier = Modifier.fillMaxWidth().background(colors.warningBg).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                tr("Itakda muna ang bayan mo", "Set your municipality first"),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.warningFg,
            )
            Text(
                tr(
                    "Ang mga silungan sa bayan mo lang ang maaari mong idagdag at baguhin.",
                    "You can add and change only the shelters in your own municipality.",
                ),
                fontSize = 13.sp,
                color = colors.warningFg,
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(44.dp).border(1.5.dp, colors.warningFg).clickable(onClick = onOpenProfile),
                contentAlignment = Alignment.Center,
            ) {
                Text(tr("Buksan ang profile", "Open your profile"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.warningFg)
            }
        }
        return
    }
    Text(
        tr("Bayan mo: $municipality", "Your municipality: $municipality"),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (count == 0) {
        Text(
            tr("Wala pang silungan sa bayan mo. Magdagdag ng isa.", "There are no shelters in your municipality yet. Add one."),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.onBackground)
            .clickable(onClick = onAddShelter),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            tr("+ Magdagdag ng silungan", "+ Add a shelter"),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.background,
        )
    }
}
