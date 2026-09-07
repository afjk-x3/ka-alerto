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
 * **Not the first screen.** The decision (7 Sep) is that the map is readable by anyone
 * immediately and this appears the first time somebody tries to *author* something — a
 * report, a confirm, a dispute, an official ruling. Blocking a stranger from seeing where
 * the water is in order to collect a name they could type as "x" would be hostile in the
 * exact moment the app exists for, and it would produce worse data, not better: a form in
 * the way of an emergency gets filled with anything. Asking at the point of authorship is
 * where the social cost the name exists for actually lives. **SOS is never gated**, here
 * or anywhere.
 *
 * Two departures from the artboard, both deliberate:
 *
 * 1. **The barangay is not filled from GPS.** The artboard says "Nakuha sa GPS mo",
 *    which needs reverse geocoding, which needs a network — on the one screen most
 *    likely to be used without one. It is pre-filled from [DemoArea] instead and the
 *    label says so rather than claiming a fix found it.
 * 2. **No SMS row.** The artboard offers four permissions; SMS is build day 12. A setup
 *    screen is where people expect every switch to work, so a dead one reads as broken
 *    rather than as scheduled — and §6.4.4's rule against implying a capability applies
 *    here as much as on the SOS status screen.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    onSos: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalKaAlertoColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    // Pre-filled when this is a correction rather than a first registration.
    var fullName by remember { mutableStateOf(LocalIdentity.registeredFullName(context)) }
    var barangay by remember {
        mutableStateOf(LocalIdentity.homeBarangay(context).ifBlank { DemoArea.BARANGAY_NAME })
    }
    var editingBarangay by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val alreadyRegistered = remember { LocalIdentity.isRegistered(context) }
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
                            onValueChange = { fullName = it; showError = false },
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
                                onValueChange = { barangay = it },
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
                        // Not "Nakuha sa GPS mo": nothing here read a fix. Saying so
                        // would be the same false claim the rest of the build avoids.
                        "Ito ang demo area ng app. Pindutin ang Baguhin kung iba ang sa iyo.",
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
                            LocalIdentity.register(context, fullName, barangay)
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Bumalik sa mapa",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
