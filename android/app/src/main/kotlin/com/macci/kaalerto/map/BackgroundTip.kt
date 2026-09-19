package com.macci.kaalerto.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.i18n.tr

/**
 * One-time tip for Xiaomi phones (MIUI/HyperOS also sold as Redmi and POCO). Their battery
 * manager kills background apps aggressively and cancels WorkManager jobs, so a report made
 * offline may not upload until the app is next opened. Only the phone's own settings can fix
 * that; the app cannot grant itself Autostart, so the tip says where to look. Real-phone
 * finding, 19 Sep 2026: the app was not battery-exempt on a Redmi and the job ran late.
 */
object BackgroundTip {
    private const val PREFS = "kaalerto_tips"
    private const val KEY_DISMISSED = "background_tip_dismissed"

    fun isXiaomiFamily(manufacturer: String): Boolean =
        manufacturer.trim().lowercase() in setOf("xiaomi", "redmi", "poco")

    fun shouldShow(context: Context): Boolean =
        isXiaomiFamily(Build.MANUFACTURER) && !prefs(context).getBoolean(KEY_DISMISSED, false)

    fun dismiss(context: Context) {
        prefs(context).edit().putBoolean(KEY_DISMISSED, true).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

@Composable
fun BackgroundTipBanner(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            tr(
                "Para tuloy ang pag-upload ng ulat kahit sarado ang app: buksan ang Autostart at itakda ang Battery sa \"No restrictions\".",
                "To keep uploading reports when the app is closed: turn on Autostart and set Battery to \"No restrictions\".",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                tr("Buksan ang settings", "Open settings"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        runCatching { context.startActivity(intent) }
                    }
                    .padding(horizontal = 8.dp, vertical = 14.dp),
            )
            Text(
                tr("Tapos na", "Done"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 8.dp, vertical = 14.dp),
            )
        }
    }
}
