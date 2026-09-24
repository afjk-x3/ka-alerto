package com.macci.kaalerto.advisory

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.macci.kaalerto.MainActivity
import com.macci.kaalerto.R
import com.macci.kaalerto.notification.NotificationChannels

/**
 * One notification per new PAGASA alert for the home province, raised locally when the
 * event lands — fetched here or carried in by another phone. The text is PAGASA's own,
 * with PAGASA named as the sender so it is never mistaken for a resident report.
 */
object AdvisoryNotifier {
    fun notify(context: Context, advisory: AdvisoryPayload) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = android.app.PendingIntent.getActivity(
            context,
            advisory.capId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_NORMAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("PAGASA: " + advisory.event.ifBlank { advisory.headline })
            .setContentText(advisory.areas.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(advisory.description))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(advisory.capId.hashCode(), notification)
    }
}
