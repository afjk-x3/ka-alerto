package com.macci.kaalerto.identity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * Municipality and barangay are free text (people spell them differently), so the fields suggest
 * the spellings already in use (evac/EvacPlaces.kt) and a tap fills one in. Anything else typed is
 * accepted as it is.
 */
@Composable
internal fun SuggestionList(suggestions: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (suggestions.isEmpty()) return
    val colors = LocalKaAlertoColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, colors.border),
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Text(
                suggestion,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * A hard-rectangle text field like the name fields, with suggestions listed underneath while it has focus.
 * The field and its suggestions are scrolled into view together: with the keyboard up the list would
 * otherwise sit below the visible area of a scrolling form.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SuggestTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    hint: String,
    description: String,
    helper: String? = null,
) {
    val colors = LocalKaAlertoColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val visible = remember { BringIntoViewRequester() }
    LaunchedEffect(focused, suggestions) {
        if (focused && suggestions.isNotEmpty()) visible.bringIntoView()
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(label)
        Column(modifier = Modifier.bringIntoViewRequester(visible), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, MaterialTheme.colorScheme.onBackground)
                .padding(horizontal = 12.dp, vertical = 12.dp),
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
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = description },
            )
            if (value.isEmpty()) Text(hint, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        }
        if (focused) SuggestionList(suggestions, onPick = { onValueChange(it); keyboard?.hide() })
        }
        if (helper != null) {
            Text(helper, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Optional for everyone; for an official it decides which shelters they may add and change. */
@Composable
internal fun MunicipalitySection(
    municipality: String,
    onMunicipalityChange: (String) -> Unit,
    suggestions: List<String>,
    /** True when the value was filled in from the detected location rather than typed. */
    fromLocation: Boolean = false,
) {
    SuggestTextField(
        label = tr("BAYAN / LUNGSOD", "MUNICIPALITY / CITY"),
        value = municipality,
        onValueChange = onMunicipalityChange,
        suggestions = suggestions,
        hint = "Mapandan, Pangasinan",
        description = tr("Bayan o lungsod mo", "Your municipality or city"),
        helper = if (fromLocation) {
            tr("Nakuha sa lokasyon mo — baguhin kung mali.", "Taken from your location — change it if it's wrong.")
        } else {
            tr(
                "Para sa mga opisyal: dito nakabatay ang mga silungang maaari mong idagdag at baguhin. Opsyonal para sa iba.",
                "For officials: the shelters you can add and change are the ones in this municipality. Optional for everyone else.",
            )
        },
    )
}
