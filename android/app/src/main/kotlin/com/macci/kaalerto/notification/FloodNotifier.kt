package com.macci.kaalerto.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.macci.kaalerto.MainActivity
import com.macci.kaalerto.R
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.data.severityTextFor
import com.macci.kaalerto.i18n.LanguagePrefs
import com.macci.kaalerto.i18n.tr

/**
 * Fires a local notification for one flood report — BUILD_TASKS.md day 5: "notifications
 * fire with no push server", since the check and the notify both happen entirely on
 * this device (geofence/GeofenceNotifier.kt decides *whether* to call this).
 */
/** Carries the reported feature's ref so the tap lands on its detail sheet, where confirming happens. */
const val EXTRA_FEATURE_REF = "com.macci.kaalerto.extra.FEATURE_REF"

object FloodNotifier {
    fun notify(context: Context, event: Event, distanceMeters: Double) {
        val severity = event.severity ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val language = LanguagePrefs.get(context)
        val (fil, en) = severityTextFor(severity)
        val severityLabel = tr(language, fil, en)
        val channel = if (severity == "S3") NotificationChannels.CHANNEL_CRITICAL else NotificationChannels.CHANNEL_NORMAL
        val priority = if (severity == "S3") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

        val openApp = Intent(context, MainActivity::class.java).apply {
            // SINGLE_TOP: the tap arrives as onNewIntent when the app is already open.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_FEATURE_REF, event.featureRef)
        }
        val contentIntent = android.app.PendingIntent.getActivity(
            context,
            event.id.hashCode(),
            openApp,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(severityLabel)
            .setContentText(tr(
                    language,
                    "${distanceMeters.toInt()} m mula sa bahay mo · Baha pa ba rito? I-tap para kumpirmahin",
                    "${distanceMeters.toInt()} m from your home · Still flooded? Tap to confirm",
                ))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
    }
}
