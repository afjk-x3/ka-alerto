package com.macci.kaalerto.identity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.demo.DemoArea
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors
import org.maplibre.android.geometry.LatLng

/**
 * The field blocks [OnboardingScreen] and [ProfileScreen] both need — name, phone, home
 * pin, barangay, and the name-visibility disclosure. Split out on 7 Sep when the two
 * became genuinely separate screens rather than one screen branching on
 * [LocalIdentity.isRegistered]: a first run is a required gate with no way out but SOS,
 * and an edit is neither required nor gate-shaped, but both still collect the same
 * fields the same way, and duplicating ~300 lines of field-rendering code to keep two
 * screens "separate" would only make the next field change a two-file job.
 */
@Composable
internal fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Two fields, not one. See `NameFormat.kt`: no rule over a single string can tell
 * "Juan Carlos Santos" from "Juan Dela Cruz", and guessing wrong prints an initial that
 * belongs to the person's own given name.
 */
@Composable
internal fun NameFields(
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    lastName: String,
    onLastNameChange: (String) -> Unit,
    showError: Boolean,
    onTypedFirstName: () -> Unit = {},
) {
    val colors = LocalKaAlertoColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val usable = isUsableName(firstName)
    val display = displayFormOf(firstName, lastName)
    val firstNameDescription = tr("Pangalan mo", "Your given name")
    val lastNameDescription = tr("Apelyido mo", "Your surname")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(tr("PANGALAN", "GIVEN NAME"))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.5.dp,
                    if (showError && !usable) colors.criticalFg else MaterialTheme.colorScheme.onBackground,
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            // BasicTextField rather than a Material TextField: every input in this app
            // is a hard 1.5dp rectangle with no fill and no floating label, and
            // Material's own decoration box cannot be talked out of its container.
            BasicTextField(
                value = firstName,
                onValueChange = {
                    onFirstNameChange(it)
                    onTypedFirstName()
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentDescription = firstNameDescription },
            )
            if (firstName.isEmpty()) {
                // A hint, never the label — the label above is the real one, so it does
                // not vanish the moment somebody starts typing.
                Text("Juan", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.border)
            }
        }
        if (showError && !usable) {
            Text(
                tr("Kailangan ng pangalan para may pananagutan ang ulat.", "A name is needed so a report has someone accountable for it."),
                fontSize = 12.sp,
                color = colors.criticalFg,
            )
        }

        FieldLabel(tr("APELYIDO", "SURNAME"))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, colors.border)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = lastName,
                onValueChange = onLastNameChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = lastNameDescription },
            )
            if (lastName.isEmpty()) {
                Text("Dela Cruz", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.border)
            }
        }
        Text(
            if (display.isBlank()) {
                tr("Lalabas ang maikling anyo ng pangalan mo sa mga ulat.", "The short form of your name will appear on reports.")
            } else {
                tr("Lalabas bilang $display sa mga ulat mo", "Will appear as $display on your reports")
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Optional, unvalidated — nothing reads this yet. Day 12's SMS fallback is the eventual
 * consumer, and it is not built, so this cannot be required or validated against a rule
 * that has no enforcer today. See [LocalIdentity]'s `KEY_PHONE` doc for the full reasoning.
 */
@Composable
internal fun PhoneField(phone: String, onPhoneChange: (String) -> Unit) {
    val colors = LocalKaAlertoColors.current
    val phoneDescription = tr("Numero ng cellphone", "Cellphone number")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(tr("NUMERO NG CELLPHONE (OPSYONAL)", "CELLPHONE NUMBER (OPTIONAL)"))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, colors.border)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = phone,
                onValueChange = onPhoneChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = phoneDescription },
            )
            if (phone.isEmpty()) {
                Text(
                    "09171234567",
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.border,
                )
            }
        }
        Text(
            // Says plainly that nothing reads it yet, rather than leaving an optional
            // field with no reason given for existing.
            tr("Gagamitin sa SMS kapag wala nang data — hindi pa gawa.", "Will be used for SMS when there's no data — not built yet."),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Build day 13 — the server-sync address (`sync/ServerSyncLoop.kt` reads it fresh every
 * cycle, no caching, so a changed address takes effect on the very next tick with no
 * restart). Optional and unvalidated, same posture as [PhoneField]: empty means sync is
 * simply off, not an error state.
 */
@Composable
internal fun ServerUrlField(serverUrl: String, onServerUrlChange: (String) -> Unit, lastSyncedAtMs: Long?) {
    val colors = LocalKaAlertoColors.current
    val serverUrlDescription = tr("Address ng server", "Server address")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(tr("ADDRESS NG SERVER (OPSYONAL)", "SERVER ADDRESS (OPTIONAL)"))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, colors.border)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = serverUrlDescription },
            )
            if (serverUrl.isEmpty()) {
                Text(
                    "192.168.1.42:3000",
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.border,
                )
            }
        }
        Text(
            serverSyncStatusLabel(lastSyncedAtMs),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "Huling nag-sync: 18 min ago", or "no sync yet" — never a bad-news state, matching this
 * app's general rule for anything that only ever reports presence or absence, not
 * failure. Deliberately duplicated age-formatting rather than shared with
 * `family/FamilyCircleScreen.kt`'s `checkInAgeLabel` — that function's own doc comment
 * already accepts this same small duplication against `sos/SosShared.kt`'s
 * `elapsedLabel`, which uses an incompatible mm:ss format.
 */
@Composable
private fun serverSyncStatusLabel(lastSyncedAtMs: Long?): String {
    if (lastSyncedAtMs == null) return tr("Wala pang sync", "No sync yet")
    val minutes = (System.currentTimeMillis() - lastSyncedAtMs) / 60_000
    val age = when {
        minutes < 1 -> tr("ngayon lang", "just now")
        minutes < 60 -> tr("$minutes min ang nakalipas", "$minutes min ago")
        else -> tr("${minutes / 60}h ${minutes % 60}m ang nakalipas", "${minutes / 60}h ${minutes % 60}m ago")
    }
    return tr("Huling nag-sync: $age", "Last synced: $age")
}

/**
 * The artboard's "Nakuha sa GPS mo" honoured as a *pin*, not a place name. Turning a fix
 * into "Brgy. San Juan Bautista" needs reverse geocoding and therefore a network, on a
 * screen that must work with neither — but a coordinate needs neither, and a coordinate
 * is what the app actually uses.
 */
@Composable
internal fun HomeSection(
    home: Pair<Double, Double>?,
    accuracyMeters: Float?,
    placeName: String?,
    locating: Boolean,
    onLocate: () -> Unit,
    onPickOnMap: () -> Unit,
) {
    val colors = LocalKaAlertoColors.current
    // Every fixture in this build — seeds, evacuation centres, routes, and the 8-tile
    // offline pack — is frozen to one barangay. A pin outside it is not wrong, and is
    // kept exactly as found; what would be wrong is letting somebody set a home there
    // and discover only during a flood that the map is blank and no report will ever be
    // near them.
    val outsideDemoArea = home != null && !DemoArea.bounds.contains(LatLng(home.first, home.second))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(tr("BAHAY MO", "YOUR HOME"))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, colors.border)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // Name on top, coordinates beneath. The name is the thing a person can
                // actually check against what they see out of the window; the
                // coordinates are what the app stores and acts on, and stay visible so
                // the label is never the only evidence.
                Text(
                    when {
                        placeName != null -> placeName
                        home != null -> tr("Nakuha ang lokasyon", "Location found")
                        locating -> tr("Hinahanap ang lokasyon mo…", "Finding your location…")
                        else -> tr("Hindi makuha ang lokasyon", "Couldn't get the location")
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    when {
                        home != null && accuracyMeters != null ->
                            "%.5f, %.5f · GPS ±%d m".format(home.first, home.second, accuracyMeters.toInt())
                        home != null -> tr("%.5f, %.5f · nakatakda".format(home.first, home.second), "%.5f, %.5f · set".format(home.first, home.second))
                        locating -> tr("Sandali lang", "Just a moment")
                        else -> tr("Ituro na lang sa mapa", "Just point to it on the map")
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (home != null) {
                    Text(
                        if (placeName != null) {
                            tr("Tingnan kung tama", "Check that this is right")
                        } else {
                            // Offline the geocoder returns nothing. Say that, rather
                            // than leaving a blank where a name was.
                            tr("Walang pangalan ng lugar offline — tingnan sa mapa", "No place name offline — check on the map")
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Text(
                if (home == null && !locating) tr("Subukan ulit", "Try again") else tr("Ituro sa mapa", "Point on the map"),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clickable { if (home == null && !locating) onLocate() else onPickOnMap() }
                    .padding(vertical = 8.dp),
            )
        }
        Text(
            tr("Dito ka aabisuhan kapag may baha malapit sa bahay mo.", "You'll be notified here when flooding is near your home."),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (outsideDemoArea) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.warningBg)
                    .padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    tr("Nasa labas ka ng saklaw ng demo na ito", "You're outside this demo's coverage area"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.warningFg,
                )
                Text(
                    // Corrected once the home pack existed: there *will* be a map now,
                    // so saying there will not be would be the same false statement in
                    // the other direction.
                    tr(
                        "Ida-download ang mapa ng lugar mo pagpindot mo ng Magsimula, " +
                            "habang may signal ka pa. Pero ang mga ulat ng baha ay para sa " +
                            "${DemoArea.BARANGAY_NAME} lang — walang ulat sa lugar mo. " +
                            "Gumagana pa rin ang SOS.",
                        "The map of your area downloads once you tap Start, while you still " +
                            "have signal. But flood reports are for ${DemoArea.BARANGAY_NAME} " +
                            "only — none for your area. SOS still works.",
                    ),
                    fontSize = 12.sp,
                    color = colors.warningFg,
                )
            }
        }
    }
}

@Composable
internal fun BarangaySection(
    barangay: String,
    onBarangayChange: (String) -> Unit,
    barangayFromLocation: Boolean,
) {
    val colors = LocalKaAlertoColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    var editingBarangay by remember { mutableStateOf(false) }
    val barangayDescription = tr("Barangay mo", "Your barangay")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel("BARANGAY")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, colors.border)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editingBarangay) {
                BasicTextField(
                    value = barangay,
                    onValueChange = onBarangayChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        editingBarangay = false
                        keyboard?.hide()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = barangayDescription },
                )
            } else {
                Text(
                    barangay,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    tr("Baguhin", "Change"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .clickable { editingBarangay = true }
                        .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        }
        Text(
            // Says where the value came from, because the two cases deserve different
            // amounts of trust: one was read off the location, the other is just this
            // build's demo area standing in.
            if (barangayFromLocation) {
                tr("Nakuha sa lokasyon mo — pindutin ang Baguhin kung mali.", "Taken from your location — tap Change if it's wrong.")
            } else {
                tr("Hindi ito nakuha sa lokasyon mo — pakitama kung iba ang sa iyo.", "This wasn't taken from your location — please correct it if yours is different.")
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun NameVisibilityDisclosure() {
    val colors = LocalKaAlertoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.recessedSurface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            tr("Makikita ng lahat ang pangalan mo sa mga ulat mo", "Everyone will see your name on your reports"),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            tr(
                "Ito ang nagpapanagot sa bawat ulat. Kapag nakarating na ito sa ibang phone, " +
                    "hindi na ito mababawi.",
                "This is what holds each report accountable. Once it has reached another " +
                    "phone, it can no longer be taken back.",
            ),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
