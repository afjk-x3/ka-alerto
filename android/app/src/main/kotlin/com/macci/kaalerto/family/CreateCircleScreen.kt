package com.macci.kaalerto.family

import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * Names a new circle and writes its one [TYPE_CIRCLE_CREATE] event on submit
 * ([submitCreateCircle]). The creator's own device resolves to a circle of one
 * immediately afterward — no round trip needed, same as any other self-authored event
 * in this app. See `specs/2026-09-23-circle-create-join-redesign.md`.
 */
@Composable
fun CreateCircleScreen(
    name: String,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(tr("Gumawa ng Circle", "Create a circle"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Box(Modifier.minimumInteractiveComponentSize().clickable(onClick = onBack).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text(tr("Isara", "Close"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Spacer(Modifier.size(18.dp))
        Text(
            tr("Ibigay ang pangalan ng inyong pamilya o grupo.", "Give your family or group a name."),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(14.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text(tr("Hal. Pamilya Reyes", "e.g. Reyes Family")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, colors.borderEmphasis)
                .clickable(enabled = name.isNotBlank(), onClick = onCreate)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tr("Gumawa", "Create"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
