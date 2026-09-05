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

/**
 * Fires a local notification for one flood report — BUILD_TASKS.md day 5: "notifications
 * fire with no push server", since the check and the notify both happen entirely on
 * this device (geofence/GeofenceNotifier.kt decides *whether* to call this).
 */
object FloodNotifier {
    fun notify(context: Context, event: Event, distanceMeters: Double) {
        val severity = event.severity ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val (fil, en) = severityTextFor(severity)
        val channel = if (severity == "S3") NotificationChannels.CHANNEL_CRITICAL else NotificationChannels.CHANNEL_NORMAL
        val priority = if (severity == "S3") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            openApp,
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$severity — $fil")
            .setContentText("${distanceMeters.toInt()} m mula sa bahay mo · $en")
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
    }
}
