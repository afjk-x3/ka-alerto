package com.macci.kaalerto.identity

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.sos.SosColors
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * PRD §9's registration, from `design/artboards/Onboarding.dc.html`: a given name, an
 * optional surname, and a home barangay, once, at first run. No password, no email.
 *
 * Trimmed from feat/event-sourced-roles for this build.
 *
 * **Required at first run, with SOS as the only way past** — CLAUDE.md's decision table.
 * There is no skip: a name is what gives a report its social cost. What makes a hard gate
 * defensible rather than hostile is the red SOS strip at the top, so someone installing
 * mid-flood is never held behind a form. On this build that SOS is the local-only rescue
 * screen (sos/SosScreen.kt) — it sends nothing, and it needs no name.
 *
 * The same form in [IdentityFormMode.EDIT] is the profile screen: no SOS strip, a
 * Kanselahin, and a note that only reports filed from now on carry the change.
 *
 * Also not here, on purpose: the permission primers (the map already asks for location
 * and notifications on first launch), the SMS row (no SMS on this build), and the home
 * pin (the long-press home radius already exists on the map). The barangay starts blank:
 * there is no offline barangay boundary data to fill it from a location honestly, and a
 * guessed default is one tap away from being accepted unread.
 */
@Composable
fun OnboardingScreen(
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    lastName: String,
    onLastNameChange: (String) -> Unit,
    barangay: String,
    onBarangayChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    mode: IdentityFormMode = IdentityFormMode.FIRST_RUN,
    /** First run only: the escape hatch, never behind the form. */
    onSos: (() -> Unit)? = null,
    /** Edit only. */
    onCancel: (() -> Unit)? = null,
) {
    var showError by remember { mutableStateOf(false) }
    val usable = isUsableName(firstName) && isUsableBarangay(barangay)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(
                    if (mode == IdentityFormMode.EDIT) "Ang profile ko" else "I-set up ang KaAlerto",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    if (mode == IdentityFormMode.EDIT) {
                        // The log is append-only: reports already filed keep the name
                        // they were filed with, and the screen says so rather than imply
                        // an edit reaches back.
                        "Para lang ito sa mga susunod mong ulat. Hindi na mababago ang mga naipadala na."
                    } else {
                        "Pangalan at barangay lang. Walang password, walang email."
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (mode == IdentityFormMode.FIRST_RUN && onSos != null) SosEscapeHatch(onSos)
            NameFields(
                firstName = firstName,
                onFirstNameChange = {
                    onFirstNameChange(it)
                    showError = false
                },
                lastName = lastName,
                onLastNameChange = onLastNameChange,
                showError = showError,
            )
            BarangayField(
                barangay = barangay,
                onBarangayChange = {
                    onBarangayChange(it)
                    showError = false
                },
                showError = showError,
            )
            NameVisibilityDisclosure()
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { if (!usable) showError = true else onDone() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (mode == IdentityFormMode.EDIT) "I-save" else "Magsimula",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            // No skip on a first run — the only way past is forward, or SOS above.
            if (onCancel != null) {
                Text(
                    "Kanselahin",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCancel)
                        .padding(vertical = 14.dp),
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

/**
 * One bordered, label-above text field — every input in this app is a hard 1.5dp
 * rectangle with no fill and no floating label, which Material's TextField decoration
 * cannot be talked out of, hence BasicTextField.
 */
@Composable
private fun BoxedField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    description: String,
    borderColor: androidx.compose.ui.graphics.Color,
) {
    val colors = LocalKaAlertoColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor)
            .padding(12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
        )
        if (value.isEmpty()) {
            // A hint, never the label — the label above stays when someone starts typing.
            Text(hint, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.border)
        }
    }
}

/** Two fields, not one — see `NameFormat.kt`. */
@Composable
private fun NameFields(
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    lastName: String,
    onLastNameChange: (String) -> Unit,
    showError: Boolean,
) {
    val colors = LocalKaAlertoColors.current
    val usable = isUsableName(firstName)
    val display = displayFormOf(firstName, lastName)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel("PANGALAN")
        BoxedField(
            value = firstName,
            onValueChange = onFirstNameChange,
            hint = "Juan",
            description = "Pangalan mo",
            borderColor = if (showError && !usable) colors.criticalFg else MaterialTheme.colorScheme.onBackground,
        )
        if (showError && !usable) {
            Text("Kailangan ng pangalan para may pananagutan ang ulat.", fontSize = 12.sp, color = colors.criticalFg)
        }
        Row(Modifier.padding(top = 6.dp)) { FieldLabel("APELYIDO (HINDI KAILANGAN)") }
        BoxedField(
            value = lastName,
            onValueChange = onLastNameChange,
            hint = "Dela Cruz",
            description = "Apelyido mo",
            borderColor = colors.border,
        )
        Text(
            if (display.isBlank()) "Lalabas ang maikling anyo ng pangalan mo sa mga ulat." else "Lalabas bilang $display sa mga ulat mo",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BarangayField(barangay: String, onBarangayChange: (String) -> Unit, showError: Boolean) {
    val colors = LocalKaAlertoColors.current
    val missing = showError && !isUsableBarangay(barangay)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel("BARANGAY")
        BoxedField(
            value = barangay,
            onValueChange = onBarangayChange,
            hint = "Barangay kung saan ka nakatira",
            description = "Barangay mo",
            borderColor = if (missing) colors.criticalFg else MaterialTheme.colorScheme.onBackground,
        )
        if (missing) {
            Text("Kailangan ang barangay mo.", fontSize = 12.sp, color = colors.criticalFg)
        }
    }
}

/**
 * PRD §9: the name is public, and the replication limit is disclosed at the point of
 * collection rather than in a policy — "once it reaches another phone it can't be taken
 * back" is the true consequence of an offline replicated store.
 */
@Composable
private fun NameVisibilityDisclosure() {
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
            "Ito ang nagpapanagot sa bawat ulat. Kapag nakarating na ito sa ibang phone, hindi na ito mababawi.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

enum class IdentityFormMode { FIRST_RUN, EDIT }

/** Onboarding.dc.html's red strip: somebody installing mid-flood must never be held behind a form. */
@Composable
private fun SosEscapeHatch(onSos: () -> Unit) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier
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
