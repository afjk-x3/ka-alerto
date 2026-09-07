package com.macci.kaalerto.identity

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.demo.DemoArea
import com.macci.kaalerto.detail.CheckIcon
import com.macci.kaalerto.mesh.MeshPermissions
import com.macci.kaalerto.sos.SosColors
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * PRD §9's registration, from `design/artboards/Onboarding.dc.html`.
 *
 * **The first screen on an unregistered device**, per PRD §9's "required at first run".
 * The authoring gate in `KaAlertoApp.gated` stays as a second line of defence, so the
 * invariant "nothing is ever authored anonymously" holds even if routing changes.
 *
 * **SOS is never gated**, which is what makes a hard gate defensible rather than
 * hostile. With no way past this screen, the red banner is not a courtesy — it is the
 * only thing reachable, and somebody installing mid-flood depends on it.
 *
 * Two departures from the artboard, both deliberate:
 *
 * 1. **GPS gives a pin, not a barangay name.** The artboard's "Nakuha sa GPS mo" implies
 *    a place name, which needs reverse geocoding and therefore a network, on the one
 *    screen most likely to be used without one. A *coordinate* needs neither, and it is
 *    what the app actually consumes: the pin seeds day 5's home radius, which is what
 *    makes the "Abiso — baha malapit sa bahay mo" row true. It is found automatically,
 *    shown with its accuracy, and adjustable on the real map ([Screen.PickHome]) — the
 *    same tap-to-pick day 3 already built for reports. The barangay *name* beside it is
 *    pre-filled and editable, and says plainly that GPS did not choose it.
 * 2. **No SMS row.** The artboard offers four permissions; SMS is build day 12. A setup
 *    screen is where people expect every switch to work, so a dead one reads as broken
 *    rather than as scheduled — and §6.4.4's rule against implying a capability applies
 *    here as much as on the SOS status screen.
 */
@Composable
fun OnboardingScreen(
    fullName: String,
    onNameChange: (String) -> Unit,
    barangay: String,
    onBarangayChange: (String) -> Unit,
    /** The home pin, found by GPS on entry. Null while looking, or if nothing came. */
    home: Pair<Double, Double>?,
    accuracyMeters: Float?,
    /** Readable label for [home], when one could be resolved. Null offline. */
    placeName: String?,
    locating: Boolean,
    onLocate: () -> Unit,
    onPickOnMap: () -> Unit,
    onDone: () -> Unit,
    onSos: () -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalKaAlertoColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    var editingBarangay by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val alreadyRegistered = remember { LocalIdentity.isRegistered(context) }

    // Every fixture in this build — seeds, evacuation centres, routes, and the 8-tile
    // offline pack — is frozen to one barangay. A pin outside it is not wrong, and is
    // kept exactly as found; what would be wrong is letting somebody set a home there
    // and discover only during a flood that the map is blank and no report will ever
    // be near them. The app knows this at registration, so it says it then.
    val outsideDemoArea = home != null &&
        !DemoArea.bounds.contains(org.maplibre.android.geometry.LatLng(home.first, home.second))
    val display = displayFormOf(fullName)
    val usable = isUsableName(fullName)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp)) {
                Text(
                    if (alreadyRegistered) "Pangalan mo" else "I-set up ang KaAlerto",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Pangalan at barangay lang. Walang password, walang email.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // The artboard's escape hatch, and the reason this screen can exist at all:
            // somebody installing mid-flood must never be held behind a form.
            if (!alreadyRegistered) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .background(colors.criticalBg)
                        .border(1.5.dp, colors.criticalFg)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "May emergency ka na ngayon? Huwag mo nang tapusin ito.",
                        fontSize = 12.sp,
                        color = colors.criticalFg,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(9.dp))
                    Box(
                        modifier = Modifier
                            .background(SosColors.Critical)
                            .clickable(onClick = onSos)
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "SOS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SosColors.CardBackground,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ---- name ----
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
                        // BasicTextField rather than a Material TextField: every input in
                        // this app is a hard 1.5dp rectangle with no fill and no floating
                        // label, and Material's own decoration box cannot be talked out of
                        // its container.
                        BasicTextField(
                            value = fullName,
                            onValueChange = { onNameChange(it); showError = false },
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
                        if (fullName.isEmpty()) {
                            // A hint, never the label — the label above is the real one, so
                            // it does not vanish the moment somebody starts typing.
                            Text(
                                "Juan Dela Cruz",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.border,
                            )
                        }
                    }
                    if (showError && !usable) {
                        Text(
                            "Kailangan ng pangalan para may pananagutan ang ulat.",
                            fontSize = 12.sp,
                            color = colors.criticalFg,
                        )
                    } else {
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

                // ---- where you live ----
                //
                // The artboard's "Nakuha sa GPS mo" is honoured here as a *pin*, not
                // as a place name. Turning a fix into "Brgy. San Juan Bautista" needs
                // reverse geocoding and therefore a network, on the one screen most
                // likely to be used without one — but a coordinate needs neither, and
                // a coordinate is what the app actually uses: it seeds day 5's home
                // radius, which is what makes the "Abiso" row below true.
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
                            // Name on top, coordinates beneath. The name is the thing a
                            // person can actually check against what they see out of the
                            // window; the coordinates are what the app stores and acts on,
                            // and stay visible so the label is never the only evidence.
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
                                        "%.5f, %.5f · GPS ±%d m".format(
                                            home.first,
                                            home.second,
                                            accuracyMeters.toInt(),
                                        )
                                    home != null -> "%.5f, %.5f · nakatakda".format(home.first, home.second)
                                    locating -> "Sandali lang"
                                    else -> "Ituro na lang sa mapa"
                                },
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (home != null) {
                                Text(
                                    if (placeName != null) {
                                        "Tingnan kung tama"
                                    } else {
                                        // Offline the geocoder returns nothing. Say that,
                                        // rather than leaving a blank where a name was.
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
                                .clickable {
                                    if (home == null && !locating) onLocate() else onPickOnMap()
                                }
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
                                // Corrected once the home pack existed: there *will*
                                // be a map now, so saying there will not be would be
                                // the same false statement in the other direction.
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

                // ---- barangay ----
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
                        // The pin above is the real datum; this is the readable label
                        // beside it. The app cannot name a barangay from a coordinate
                        // offline, so this is pre-filled rather than derived, and says
                        // so instead of implying GPS chose it.
                        "Hindi ito nakukuha sa GPS — pakitama kung iba ang sa iyo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ---- the disclosure ----
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
                        "Ito ang nagpapanagot sa bawat ulat. Kapag nakarating na ito sa ibang " +
                            "phone, hindi na ito mababawi.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!alreadyRegistered) PermissionSection()

                Spacer(Modifier.size(8.dp))
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        if (!usable) {
                            showError = true
                        } else {
                            onDone()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (alreadyRegistered) "I-save" else "Magsimula",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            // No way out but forward — except SOS. PRD §9 makes this required at
            // first run, so a "skip" here would be the decision table's "fully
            // skippable" option, which was considered and rejected. A cancel appears
            // only when this is an edit rather than a first registration.
            if (onCancel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Kanselahin",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The artboard's permission primers, minus SMS.
 *
 * Each row asks for something that does something *today*. They are primers, not gates:
 * declining any of them leaves the app fully usable, which is why the button says
 * "Payagan" rather than being a switch that implies state the OS actually owns.
 */
@Composable
private fun PermissionSection() {
    val context = LocalContext.current
    var notified by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    var meshed by remember { mutableStateOf(MeshPermissions.allGranted(context)) }

    val notifyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notified = granted }
    val meshLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { meshed = MeshPermissions.allGranted(context) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel("PAPAYAGAN MO BA")
        PermissionRow(
            title = "Abiso",
            detail = "Baha malapit sa bahay mo — kahit offline",
            granted = notified,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        PermissionRow(
            title = "Mga kalapit na device",
            detail = "Dito dumadaan ang ulat kapag walang cell site",
            granted = meshed,
        ) {
            meshLauncher.launch(MeshPermissions.required())
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, colors.border)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(10.dp))
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckIcon(colors.safeFg, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Bukas", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.safeFg)
            }
        } else {
            Box(
                modifier = Modifier
                    .border(1.5.dp, colors.borderEmphasis)
                    .clickable(onClick = onRequest)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    "Payagan",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
