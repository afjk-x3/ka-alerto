package com.macci.kaalerto.family

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
 * Enters a join code (pasted, or filled in by the caller after [onScanQr] returns from
 * `family/QrScannerScreen.kt`) and writes one [TYPE_CIRCLE_JOIN] event on submit
 * ([submitJoinCircle]). No existence check against the code before writing — same as
 * every other event this app writes optimistically; a wrong code just resolves to a
 * circle of one until proven otherwise (`family/CircleStore.kt`'s `resolveCircle`).
 */
@Composable
fun JoinCircleScreen(
    code: String,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onScanQr: () -> Unit,
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
            Text(tr("Sumali sa Circle", "Join a circle"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Box(Modifier.clickable(onClick = onBack).padding(8.dp)) {
                Text(tr("Isara", "Close"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Spacer(Modifier.size(18.dp))
        Text(
            tr("I-paste ang code na ibinigay sa iyo.", "Paste the code you were given."),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(14.dp))
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            placeholder = { Text(tr("Code", "Code")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, colors.borderEmphasis)
                .clickable(enabled = code.isNotBlank(), onClick = onJoin)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(tr("Sumali", "Join"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.size(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onScanQr)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tr("I-scan ang QR sa halip", "Scan a QR instead"),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
