package com.macci.kaalerto.identity

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.nav.HamburgerButton

/**
 * The drawer's "Ang profile ko" — editing an already-registered identity. Split out of
 * [OnboardingScreen] on 7 Sep: that screen used to serve both purposes, branching on
 * [LocalIdentity.isRegistered], because it already handled name and home-pin editing
 * correctly and a second screen looked like it would just duplicate that logic. The
 * user asked for a genuinely separate screen instead, which this is — the two no longer
 * share a composable, only the field blocks in `ProfileFields.kt` that both need.
 *
 * Unlike [OnboardingScreen], this is never a gate: it always has a hamburger (only
 * reachable once registered, so the drawer is never a way around anything), always a
 * cancel (an edit that cannot be abandoned would be a worse trade than duplicating a
 * few lines), and no SOS banner or permission primers — those belong to a first run,
 * not a settings screen a resident opens on a normal day.
 */
@Composable
fun ProfileScreen(
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    lastName: String,
    onLastNameChange: (String) -> Unit,
    /** Optional, unvalidated — see LocalIdentity.KEY_PHONE for why. */
    phone: String,
    onPhoneChange: (String) -> Unit,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    lastSyncedAtMs: Long?,
    serverSearching: Boolean,
    serverAutoDetected: Boolean,
    onSearchServer: () -> Unit,
    barangay: String,
    onBarangayChange: (String) -> Unit,
    barangayFromLocation: Boolean,
    home: Pair<Double, Double>?,
    accuracyMeters: Float?,
    placeName: String?,
    locating: Boolean,
    onLocate: () -> Unit,
    onPickOnMap: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onOpenMenu: () -> Unit,
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
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                HamburgerButton(onClick = onOpenMenu, modifier = Modifier.padding(end = 12.dp, top = 3.dp))
                Column {
                    Text(
                        tr("Profile mo", "Your profile"),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        tr("Pangalan, numero, at bahay mo.", "Your name, number, and home."),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

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
                PhoneField(phone = phone, onPhoneChange = onPhoneChange)
                ServerUrlField(
                    serverUrl = serverUrl,
                    onServerUrlChange = onServerUrlChange,
                    lastSyncedAtMs = lastSyncedAtMs,
                    searching = serverSearching,
                    autoDetected = serverAutoDetected,
                    onSearch = onSearchServer,
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
                        if (!usable) showError = true else onSave()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(tr("I-save", "Save"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(tr("Kanselahin", "Cancel"), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
