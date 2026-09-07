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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.detail.CheckIcon
import com.macci.kaalerto.mesh.MeshPermissions
import com.macci.kaalerto.sos.SosColors
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * PRD §9's registration gate, from `design/artboards/Onboarding.dc.html`.
 *
 * **The first screen on an unregistered device**, per PRD §9's "required at first run".
 * The authoring gate in `KaAlertoApp.gated` stays as a second line of defence, so the
 * invariant "nothing is ever authored anonymously" holds even if routing changes. There
 * is no cancel and no hamburger here — a required gate cannot offer a way around itself,
 * and PRD §9 is explicit that this is not skippable.
 *
 * **SOS is never gated**, which is what makes a hard gate defensible rather than
 * hostile. With no way past this screen, the red banner is not a courtesy — it is the
 * only thing reachable, and somebody installing mid-flood depends on it.
 *
 * **This used to also be the profile-edit screen**, branching on
 * [LocalIdentity.isRegistered]. Split into this and [ProfileScreen] on 7 Sep — a
 * required gate and an editable, cancellable settings screen are different shapes, and
 * folding the second into the first for a few weeks of not building a second screen was
 * the wrong trade once the drawer gave editing its own real entry point. The field
 * blocks both screens use live in `ProfileFields.kt`.
 *
 * Two departures from the artboard, both deliberate:
 *
 * 1. **GPS gives a pin, not a barangay name.** The artboard's "Nakuha sa GPS mo" implies
 *    a place name, which needs reverse geocoding and therefore a network, on the one
 *    screen most likely to be used without one. A *coordinate* needs neither, and it is
 *    what the app actually consumes: the pin seeds day 5's home radius, which is what
 *    makes the "Abiso — baha malapit sa bahay mo" row true. It is found automatically,
 *    shown with its accuracy, and adjustable on the real map ([Screen.PickHome]) — the
 *    same tap-to-pick day 3 already built for reports. The barangay follows the pin
 *    until corrected — see `ProfileFields.kt`'s `HomeSection`/`BarangaySection`.
 * 2. **No SMS row.** The artboard offers four permissions; SMS is build day 12. A setup
 *    screen is where people expect every switch to work, so a dead one reads as broken
 *    rather than as scheduled — and §6.4.4's rule against implying a capability applies
 *    here as much as on the SOS status screen.
 */
@Composable
fun OnboardingScreen(
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    lastName: String,
    onLastNameChange: (String) -> Unit,
    barangay: String,
    onBarangayChange: (String) -> Unit,
    /** True once the barangay came from the geocoder rather than a default. */
    barangayFromLocation: Boolean,
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
    modifier: Modifier = Modifier,
) {
    var showError by remember { mutableStateOf(false) }
    val usable = isUsableName(firstName)

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
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp)) {
                Text(
                    "I-set up ang KaAlerto",
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
            SosEscapeHatch(onSos)

            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NameFields(
                    firstName = firstName,
                    onFirstNameChange = onFirstNameChange,
                    lastName = lastName,
                    onLastNameChange = onLastNameChange,
                    showError = showError,
                    onTypedFirstName = { showError = false },
                )
                HomeSection(
                    home = home,
                    accuracyMeters = accuracyMeters,
                    placeName = placeName,
                    locating = locating,
                    onLocate = onLocate,
                    onPickOnMap = onPickOnMap,
                )
                BarangaySection(
                    barangay = barangay,
                    onBarangayChange = onBarangayChange,
                    barangayFromLocation = barangayFromLocation,
                )
                NameVisibilityDisclosure()
                PermissionSection()
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
                        if (!usable) showError = true else onDone()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Magsimula",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            // No way out but forward — except SOS. PRD §9 makes this required at first
            // run, so a "skip" or a "cancel" here would be the decision table's "fully
            // skippable" option, which was considered and rejected.
        }
    }
}

@Composable
private fun SosEscapeHatch(onSos: () -> Unit) {
    val colors = LocalKaAlertoColors.current
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
            Text("SOS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SosColors.CardBackground)
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
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
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
                Text("Payagan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
