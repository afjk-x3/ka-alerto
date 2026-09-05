package com.macci.kaalerto.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * Two channels: everything is minSdk 26 (Android 8+), so channels always apply — no
 * legacy pre-O path needed. Critical (S3) bypasses Do Not Disturb, per BUILD_TASKS.md
 * day 5 ("critical breaks DND") — a chest-deep road near home is exactly the kind of
 * thing quiet hours must not silence (docs/03-architecture.md's anti-fatigue rules
 * carve out the same exception for S3/SOS/official red warnings).
 */
object NotificationChannels {
    const val CHANNEL_NORMAL = "flood_normal"
    const val CHANNEL_CRITICAL = "flood_critical"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NORMAL, "Mga ulat ng baha", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Flood reports (S0-S2) inside your home radius"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CRITICAL, "Kritikal na babala", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "S3 flood reports inside your home radius"
                setBypassDnd(true)
                enableVibration(true)
            },
        )
    }
}
