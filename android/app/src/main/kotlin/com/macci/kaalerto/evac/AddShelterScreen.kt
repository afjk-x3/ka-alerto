package com.macci.kaalerto.evac

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.identity.FieldLabel
import com.macci.kaalerto.identity.SuggestTextField
import com.macci.kaalerto.nav.HamburgerButton
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/** What an official has filled in so far. Lives above the screen so it survives the trip to the map picker. */
data class ShelterDraft(
    val name: String = "",
    val kind: String = "school",
    val barangay: String = "",
    val capacity: Int = 0,
    val lat: Double? = null,
    val lon: Double? = null,
)

/** Shared with the map's shelter-focus card, so the two screens never drift on wording. */
@Composable
internal fun kindLabel(kind: String): String = when (kind) {
    "school" -> tr("Paaralan", "School")
    "gym" -> tr("Gym", "Gym")
    "barangay_hall" -> tr("Barangay hall", "Barangay hall")
    "church" -> tr("Simbahan", "Church")
    else -> tr("Iba pa", "Other")
}

/**
 * An official adding a shelter in their own municipality. The municipality is not editable here — it
 * is the one on their profile, which is what scopes the shelters they may manage. A new shelter starts
 * "not open yet"; opening it is a deliberate second step on the shelter list.
 */
@Composable
fun AddShelterScreen(
    municipality: String,
    draft: ShelterDraft,
    onDraftChange: (ShelterDraft) -> Unit,
    barangaySuggestions: List<String>,
    locating: Boolean,
    onUseMyLocation: () -> Unit,
    onPickOnMap: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val ready = draft.name.isNotBlank() && draft.lat != null && draft.lon != null
    val nameDescription = tr("Pangalan ng silungan", "Shelter name")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            HamburgerButton(onClick = onOpenMenu, modifier = Modifier.padding(end = 12.dp, top = 3.dp))
            Column {
                Text(
                    tr("Magdagdag ng silungan", "Add a shelter"),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    tr("Sa bayan mo lang", "In your municipality only"),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel(tr("PANGALAN NG SILUNGAN", "SHELTER NAME"))
                Box(
                    modifier = Modifier.fillMaxWidth().border(1.5.dp, MaterialTheme.colorScheme.onBackground).padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = draft.name,
                        onValueChange = { onDraftChange(draft.copy(name = it)) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = nameDescription },
                    )
                    if (draft.name.isEmpty()) {
                        Text("Mapandan National High School", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel(tr("URI", "KIND"))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EVAC_KINDS.forEach { kind ->
                        val selected = draft.kind == kind
                        Box(
                            modifier = Modifier
                                .background(if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background)
                                .border(1.5.dp, if (selected) MaterialTheme.colorScheme.onBackground else colors.borderEmphasis)
                                .clickable { onDraftChange(draft.copy(kind = kind)) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                kindLabel(kind),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel(tr("BAYAN / LUNGSOD", "MUNICIPALITY / CITY"))
                Box(Modifier.fillMaxWidth().border(1.5.dp, colors.borderEmphasis).padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Text(municipality, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    tr("Galing ito sa profile mo.", "This comes from your profile."),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SuggestTextField(
                label = tr("BARANGAY", "BARANGAY"),
                value = draft.barangay,
                onValueChange = { onDraftChange(draft.copy(barangay = it)) },
                suggestions = barangaySuggestions,
                hint = "Brgy. San Juan Bautista",
                description = tr("Barangay ng silungan", "The shelter's barangay"),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(tr("KAPASIDAD (TANTIYA, OPSYONAL)", "CAPACITY (ESTIMATE, OPTIONAL)"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepBox("−10") { onDraftChange(draft.copy(capacity = (draft.capacity - 10).coerceAtLeast(0))) }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        if (draft.capacity == 0) "—" else draft.capacity.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    StepBox("+10") { onDraftChange(draft.copy(capacity = draft.capacity + 10)) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(tr("LOKASYON", "LOCATION"))
                val at = if (draft.lat != null && draft.lon != null) "%.5f, %.5f".format(draft.lat, draft.lon) else null
                Text(
                    at ?: tr("Wala pang lokasyon", "No location yet"),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (at != null) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(
                        if (locating) tr("Hinahanap…", "Finding…") else tr("Gamitin ang lokasyon ko", "Use my location"),
                        Modifier.weight(1f),
                        onUseMyLocation,
                    )
                    OutlineButton(tr("Ituro sa mapa", "Point on the map"), Modifier.weight(1f), onPickOnMap)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(if (ready) MaterialTheme.colorScheme.onBackground else colors.border)
                    .clickable(enabled = ready, onClick = onSave),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tr("I-save ang silungan", "Save the shelter"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.background,
                )
            }
            if (!ready) {
                Text(
                    tr("Kailangan ng pangalan at lokasyon.", "A name and a location are needed."),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.fillMaxWidth().clickable(onClick = onCancel).padding(8.dp), contentAlignment = Alignment.Center) {
                Text(tr("Kanselahin", "Cancel"), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

    }
}

@Composable
private fun StepBox(label: String, onClick: () -> Unit) {
    val colors = LocalKaAlertoColors.current
    Box(
        modifier = Modifier.size(width = 64.dp, height = 48.dp).border(1.5.dp, colors.borderEmphasis).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun OutlineButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalKaAlertoColors.current
    Box(
        modifier = modifier.height(48.dp).border(1.5.dp, colors.borderEmphasis).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}
