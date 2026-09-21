package com.macci.kaalerto.identity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.macci.kaalerto.detail.CheckIcon
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.mesh.MeshPermissions
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/**
 * The four things this app can ask the system for, each with the reason a person can read before the system's
 * own dialog appears. None of them is a gate: declining any leaves the app usable, and SOS is never behind one
 * (PRD §9). The rows are shared by registration and the profile screen, so "later" is a real answer.
 */
enum class Perm {
    LOCATION, NOTIFICATIONS, NEARBY, CAMERA;

    /** What the system must be asked for. Empty when this Android version needs nothing (notifications before 13). */
    fun permissions(): Array<String> = when (this) {
        LOCATION -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        NEARBY -> MeshPermissions.required()
        CAMERA -> arrayOf(Manifest.permission.CAMERA)
    }

    fun isGranted(context: Context): Boolean = when (this) {
        // Either precision is enough to place something on a map; fine is asked for first.
        LOCATION -> permissions().any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        NEARBY -> MeshPermissions.allGranted(context)
        else -> permissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    @Composable
    fun title(): String = when (this) {
        LOCATION -> tr("Lokasyon", "Location")
        NOTIFICATIONS -> tr("Abiso", "Notifications")
        NEARBY -> tr("Mga kalapit na device", "Nearby devices")
        CAMERA -> tr("Camera", "Camera")
    }

    /** One line, why. Says what the permission does today, never what it might one day do. */
    @Composable
    fun reason(): String = when (this) {
        LOCATION -> tr("Para mailagay ang bahay mo, ang ulat at ang SOS sa mapa", "To place your home, reports and SOS on the map")
        NOTIFICATIONS -> tr("Baha malapit sa bahay mo — kahit offline", "Flooding near your home — even offline")
        NEARBY -> tr("Dito dumadaan ang ulat kapag walang cell site", "This is how reports travel when there's no cell site")
        // Gates only the Family Circle QR scanner (zxing's own CaptureActivity, which draws its live preview). Report
        // photos use an external camera intent and need no permission of this app's own.
        CAMERA -> tr("Para sa pag-scan ng QR ng Aking Pamilya", "For scanning your Family Circle's QR")
    }
}

/** What the map's one-time explanation covers: everything the app uses on its own, but not the camera (only the QR scanner). */
val PRIMER_PERMS = listOf(Perm.LOCATION, Perm.NOTIFICATIONS, Perm.NEARBY)

fun missingPrimerPerms(context: Context): List<Perm> = PRIMER_PERMS.filter { it.permissions().isNotEmpty() && !it.isGranted(context) }

/** The explanation is offered once, and only when something is missing. Either answer counts as answered. */
fun shouldShowPrimer(missing: List<Perm>, alreadyAnswered: Boolean): Boolean = missing.isNotEmpty() && !alreadyAnswered

object PermissionPrefs {
    private const val PREFS = "kaalerto_permissions"
    private const val KEY_PRIMER_ANSWERED = "map_primer_answered"

    fun primerAnswered(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PRIMER_ANSWERED, false)

    fun markPrimerAnswered(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PRIMER_ANSWERED, true).apply()
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }
}

/**
 * The permission rows. A denied row's button becomes "Buksan ang settings", because after a refusal the system may
 * not show its dialog again and a button that does nothing looks broken. Rows re-read the system's state when the
 * app comes back to the front, so turning something on in Settings shows up here.
 */
@Composable
internal fun PermissionSection(label: String = tr("PAPAYAGAN MO BA", "WILL YOU ALLOW")) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var denied by remember { mutableStateOf(setOf<Perm>()) }
    var asking by remember { mutableStateOf<Perm?>(null) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val perm = asking
        if (perm != null && !perm.isGranted(context)) denied = denied + perm
        asking = null
        tick++
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(label)
        Perm.values().forEach { perm ->
            val granted = remember(tick) { perm.permissions().isEmpty() || perm.isGranted(context) }
            PermissionRow(
                title = perm.title(),
                detail = perm.reason(),
                granted = granted,
                needsSettings = perm in denied,
            ) {
                if (perm in denied) {
                    openAppSettings(context)
                } else {
                    asking = perm
                    launcher.launch(perm.permissions())
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, detail: String, granted: Boolean, needsSettings: Boolean, onRequest: () -> Unit) {
    val colors = LocalKaAlertoColors.current
    Row(
        modifier = Modifier.fillMaxWidth().border(1.5.dp, colors.border).padding(horizontal = 12.dp, vertical = 10.dp),
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
                Text(tr("Bukas", "On"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.safeFg)
            }
        } else {
            Box(
                modifier = Modifier
                    .border(1.5.dp, colors.borderEmphasis)
                    .clickable(onClick = onRequest)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    if (needsSettings) tr("Buksan ang settings", "Open settings") else tr("Payagan", "Allow"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
