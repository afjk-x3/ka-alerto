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

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel("PANGALAN")
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
                    .semantics { contentDescription = "Pangalan mo" },
            )
            if (firstName.isEmpty()) {
                // A hint, never the label — the label above is the real one, so it does
                // not vanish the moment somebody starts typing.
                Text("Juan", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.border)
            }
        }
        if (showError && !usable) {
            Text(
                "Kailangan ng pangalan para may pananagutan ang ulat.",
                fontSize = 12.sp,
                color = colors.criticalFg,
            )
        }

        FieldLabel("APELYIDO")
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
                    .semantics { contentDescription = "Apelyido mo" },
            )
            if (lastName.isEmpty()) {
                Text("Dela Cruz", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.border)
            }
        }
        Text(
            if (display.isBlank()) {
                "Lalabas ang maikling anyo ng pangalan mo sa mga ulat."
            } else {
                "Lalabas bilang $display sa mga ulat mo"
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel("NUMERO NG CELLPHONE (OPSYONAL)")
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
                    .semantics { contentDescription = "Numero ng cellphone" },
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
            "Gagamitin sa SMS kapag wala nang data — hindi pa gawa.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
        FieldLabel("BAHAY MO")
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
                        home != null -> "Nakuha ang lokasyon"
                        locating -> "Hinahanap ang lokasyon mo…"
                        else -> "Hindi makuha ang lokasyon"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    when {
                        home != null && accuracyMeters != null ->
                            "%.5f, %.5f · GPS ±%d m".format(home.first, home.second, accuracyMeters.toInt())
                        home != null -> "%.5f, %.5f · nakatakda".format(home.first, home.second)
                        locating -> "Sandali lang"
                        else -> "Ituro na lang sa mapa"
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (home != null) {
                    Text(
                        if (placeName != null) {
                            "Tingnan kung tama"
                        } else {
                            // Offline the geocoder returns nothing. Say that, rather
                            // than leaving a blank where a name was.
                            "Walang pangalan ng lugar offline — tingnan sa mapa"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Text(
                if (home == null && !locating) "Subukan ulit" else "Ituro sa mapa",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clickable { if (home == null && !locating) onLocate() else onPickOnMap() }
                    .padding(vertical = 8.dp),
            )
        }
        Text(
            "Dito ka aabisuhan kapag may baha malapit sa bahay mo.",
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
                    "Nasa labas ka ng saklaw ng demo na ito",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.warningFg,
                )
                Text(
                    // Corrected once the home pack existed: there *will* be a map now,
                    // so saying there will not be would be the same false statement in
                    // the other direction.
                    "Ida-download ang mapa ng lugar mo pagpindot mo ng Magsimula, " +
                        "habang may signal ka pa. Pero ang mga ulat ng baha ay para sa " +
                        "${DemoArea.BARANGAY_NAME} lang — walang ulat sa lugar mo. " +
                        "Gumagana pa rin ang SOS.",
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
                        .semantics { contentDescription = "Barangay mo" },
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
                    "Baguhin",
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
                "Nakuha sa lokasyon mo — pindutin ang Baguhin kung mali."
            } else {
                "Hindi ito nakuha sa lokasyon mo — pakitama kung iba ang sa iyo."
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
            "Makikita ng lahat ang pangalan mo sa mga ulat mo",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Ito ang nagpapanagot sa bawat ulat. Kapag nakarating na ito sa ibang phone, " +
                "hindi na ito mababawi.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
