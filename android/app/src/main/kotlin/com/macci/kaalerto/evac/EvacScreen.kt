package com.macci.kaalerto.evac

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

    // A resident sees every centre, nearest first (evacStates sorts by distance), closed
    // ones greyed: hiding closed ones left a fresh install with an empty list until an
    // official opened something, when knowing where the shelters are is useful before
    // they open. The province and municipality filters narrow it; unset, they show all.
    // An official sees the ones their municipality manages, open or not, because "not
    // open" is exactly the state they're here to change (OfficialControls below).
    var province by remember { mutableStateOf<String?>(null) }
    var town by remember { mutableStateOf<String?>(null) }
    val visibleStates = if (isOfficial) {
        states.filter { canManage(municipality, it.centre) }
    } else {
        states.filter { (province == null || provinceOf(it.centre) == province) && (town == null || it.centre.municipality == town) }
    }
    val noneOpen = states.isNotEmpty() && states.all { it.status == EvacStatus.NOT_OPEN }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
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
            if (!isOfficial && states.isNotEmpty()) {
                val provinces = states.mapNotNull { provinceOf(it.centre) }.distinct().sorted()
                val towns = states.filter { province == null || provinceOf(it.centre) == province }
                    .mapNotNull { it.centre.municipality }.distinct().sorted()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterDropdown(
                        label = province ?: tr("Lahat ng probinsya", "All provinces"),
                        active = province != null,
                        options = provinces,
                        optionLabel = { it },
                        allLabel = tr("Lahat ng probinsya", "All provinces"),
                        onPick = { province = it; town = null },
                        modifier = Modifier.weight(1f),
                    )
                    FilterDropdown(
                        label = town?.substringBefore(",") ?: tr("Lahat ng bayan", "All towns"),
                        active = town != null,
                        options = towns,
                        optionLabel = { it.substringBefore(",") },
                        allLabel = tr("Lahat ng bayan", "All towns"),
                        onPick = { town = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (visibleStates.isEmpty()) {
                    Text(tr("Walang silungan dito.", "No shelters here."), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (states.isEmpty()) {
                Text(
                    tr("Walang evacuation centre sa fixture.", "No evacuation centres in the fixture."),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (noneOpen && !isOfficial) {
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
                        "Nakalista pa rin sa ibaba para malaman mo kung saan. Makikita agad dito kapag may binuksan ang barangay.",
                        "They're still listed below so you know where they are. You'll see it here as soon as the barangay opens one.",
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
            Box(Modifier.minimumInteractiveComponentSize().clickable(onClick = onBack).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text(tr("Isara", "Close"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

/** "Mapandan, Pangasinan" → "Pangasinan". Municipalities are stored PSGC-style as "Town, Province". */
internal fun provinceOf(centre: EvacCentre): String? =
    centre.municipality?.substringAfter(", ", "")?.takeIf { it.isNotBlank() }

/** A filter chip that opens a list; the first entry clears it. */
@Composable
private fun FilterDropdown(
    label: String,
    active: Boolean,
    options: List<String>,
    optionLabel: (String) -> String,
    allLabel: String,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background)
                .border(1.5.dp, if (active) MaterialTheme.colorScheme.onBackground else colors.borderEmphasis)
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "$label  ▾",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = if (active) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
            )
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            androidx.compose.material3.DropdownMenuItem(text = { Text(allLabel) }, onClick = { open = false; onPick(null) })
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { open = false; onPick(option) })
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
                // The whole card is tappable; this says so.
                Text(
                    tr("Tingnan sa mapa ›", "See on the map ›"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
            var showQr by remember { mutableStateOf(false) }
            // One control for the status: Update opens the four statuses, the head count and
            // Save together. The old separate Open/Close toggle contradicted it.
            Row(
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(if (editing) colors.recessedSurface else MaterialTheme.colorScheme.onBackground)
                        .clickable(onClick = onToggleEdit),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (editing) tr("Kanselahin", "Cancel") else tr("I-update ang status", "Update the status"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (editing) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background,
                    )
                }
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .border(1.5.dp, colors.borderEmphasis)
                        .clickable { showQr = true }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("QR", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            if (showQr) ShelterQrDialog(state.centre.name) { showQr = false }
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
 * The official's update: pick one of the four statuses, type how many people are there
 * now, then Save. Nothing is sent until Save, so a mis-tap on a status costs nothing.
 */
@Composable
private fun OfficialControls(state: EvacState, onUpdate: (EvacStatus, Int?) -> Unit) {
    val colors = LocalKaAlertoColors.current
    var status by remember(state.centre.id) { mutableStateOf(state.status) }
    var count by remember(state.centre.id) { mutableStateOf(state.occupancy?.toString().orEmpty()) }

    Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EvacStatus.values().toList().chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val selected = option == status
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background)
                            .border(1.5.dp, if (selected) MaterialTheme.colorScheme.onBackground else colors.borderEmphasis)
                            .clickable { status = option },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option.label(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
        if (status != EvacStatus.NOT_OPEN) {
            androidx.compose.material3.OutlinedTextField(
                value = count,
                onValueChange = { count = it.filter(Char::isDigit).take(5) },
                label = { Text(tr("Ilang tao ngayon", "People there now")) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(MaterialTheme.colorScheme.onBackground)
                .clickable { onUpdate(status, count.toIntOrNull().takeIf { status != EvacStatus.NOT_OPEN }) },
            contentAlignment = Alignment.Center,
        ) {
            Text(tr("I-save", "Save"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
        }
    }
}

/**
 * The shelter's QR, for printing at its door. It points at a fixed placeholder link for now
 * (the user's choice, 24 Sep 2026); head counts stay typed by the official.
 */
private const val SHELTER_QR_LINK = "https://www.youtube.com/watch?v=OUjprWAg1A8&list=RDOUjprWAg1A8&start_radio=1"

@Composable
private fun ShelterQrDialog(name: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White).padding(12.dp), contentAlignment = Alignment.Center) {
                com.macci.kaalerto.sos.QrCode(content = SHELTER_QR_LINK, modifier = Modifier.size(240.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Isara", "Close")) } },
    )
}

private fun statusAccent(status: EvacStatus, safe: Color, warning: Color, muted: Color): Color = when (status) {
    EvacStatus.ACCEPTING -> safe
    EvacStatus.NEARLY_FULL -> warning
    EvacStatus.FULL -> warning
    EvacStatus.NOT_OPEN -> muted
}

private fun statusChipBackground(status: EvacStatus, safeBg: Color, warningBg: Color, muted: Color): Color = when (status) {
    EvacStatus.ACCEPTING -> safeBg
    EvacStatus.NEARLY_FULL -> warningBg
    EvacStatus.FULL -> warningBg
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
