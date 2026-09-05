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

    /**
     * The mesh foreground service's own channel (mesh/MeshService.kt). IMPORTANCE_LOW
     * on purpose: this notification exists because Android requires one for a
     * long-running service, not because it has news — it must never make a sound, and
     * it must never compete with an actual flood warning.
     */
    const val CHANNEL_MESH = "mesh_status"

    /**
     * Someone nearby has asked for rescue. Deliberately its own channel, louder than
     * `CHANNEL_CRITICAL` and separately silenceable: BUILD_TASKS.md day 9 wants this
     * "distinct from flood notifications", and a resident who mutes flood chatter must
     * not thereby mute a neighbour calling for help.
     */
    const val CHANNEL_SOS = "sos_nearby"

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
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SOS, "Humihingi ng tulong sa malapit", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Someone nearby has requested rescue"
                setBypassDnd(true)
                enableVibration(true)
                // A long, irregular pattern — it has to be distinguishable from a flood
                // alert through a pocket, in the rain, at night.
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 900)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESH, "Mesh sa mga kalapit na phone", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Status of the background relay to nearby phones"
                setShowBadge(false)
            },
        )
    }
}
