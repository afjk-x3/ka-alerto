package com.macci.kaalerto.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.macci.kaalerto.MainActivity
import com.macci.kaalerto.R
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.i18n.LanguagePrefs
import com.macci.kaalerto.i18n.tr

/** Fires a local notification for one circle member's check-in — mirrors
 * `FloodNotifier.notify`'s shape exactly. */
object CircleCheckInNotification {
    fun notify(context: Context, event: Event) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val language = LanguagePrefs.get(context)
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(context, 0, openApp, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_FAMILY_CHECKIN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(tr(language, "Ligtas si ${event.authorName}", "${event.authorName} is safe"))
            .setContentText(tr(language, "Nag-check in gamit ang Aking Pamilya", "Checked in via My Family"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
    }
}
