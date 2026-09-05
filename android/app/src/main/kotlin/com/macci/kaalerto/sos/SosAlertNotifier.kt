package com.macci.kaalerto.sos

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.macci.kaalerto.MainActivity
import com.macci.kaalerto.R
import com.macci.kaalerto.data.haversineMeters
import com.macci.kaalerto.notification.NotificationChannels
import kotlin.math.roundToInt

/** Extra on [MainActivity]'s intent naming the SOS to open on launch. */
const val EXTRA_SOS_ID = "com.macci.kaalerto.extra.SOS_ID"

/**
 * BUILD_TASKS.md day 9: "receiving device raises critical full-screen alert (distinct
 * from flood notifications: sound, vibration, red)".
 *
 * **The full-screen part is not ours to guarantee.** Since API 34, `USE_FULL_SCREEN_INTENT`
 * is auto-granted only to apps that declare themselves as calling or alarm apps;
 * everyone else has to be granted it by the user in settings, and
 * `NotificationManager.canUseFullScreenIntent()` reports which side of that line we are
 * on. So the notification is built with a full-screen intent *and* with everything that
 * makes a heads-up alert impossible to miss on its own — [NotificationChannels.CHANNEL_SOS]
 * is IMPORTANCE_HIGH, bypasses DND, vibrates on its own pattern and rings on the alarm
 * stream. If the permission is there, the request takes over the screen; if it is not,
 * it still shouts. What it never does is fail silently.
 */
object SosAlertNotifier {

    fun notify(context: Context, snapshot: SosSnapshot, fromLat: Double?, fromLon: Double?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val open = PendingIntent.getActivity(
            context,
            snapshot.sosId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                // SINGLE_TOP pairs with the activity's launchMode: together they make
                // the tap arrive as onNewIntent instead of being swallowed as a plain
                // resume when the app is already open.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_SOS_ID, snapshot.sosId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val distance = if (fromLat != null && fromLon != null) {
            haversineMeters(fromLat, fromLon, snapshot.lat, snapshot.lon)
        } else {
            null
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_SOS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("May humihingi ng tulong")
            .setContentText(subtitle(distance, snapshot))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(0xFFC42B2B.toInt())
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(open)

        // Only ask for the screen if we are actually allowed to take it. Attaching a
        // full-screen intent we cannot use makes the notification behave unpredictably
        // across OEM skins rather than degrading cleanly to a heads-up.
        if (canUseFullScreen(context)) {
            builder.setFullScreenIntent(open, true)
        }

        NotificationManagerCompat.from(context).notify(snapshot.sosId.hashCode(), builder.build())
    }

    /**
     * Coarse on purpose. SOSNearby.dc.html shows a plain resident an approximate
     * distance and explicitly not "kung sino sila" — this string is the first thing they
     * see, so it must not be the first place the exact position leaks.
     */
    private fun subtitle(distanceMeters: Double?, snapshot: SosSnapshot): String {
        val where = distanceMeters?.let { "Humigit-kumulang ${roundDistance(it)} m ang layo" } ?: "Malapit sa iyo"
        val people = snapshot.context.people?.let { " · $it tao" }.orEmpty()
        return where + people
    }

    /** Rounded to 50 m, so the number itself cannot be trilaterated back to a doorstep. */
    fun roundDistance(meters: Double): Int = ((meters / 50.0).roundToInt() * 50).coerceAtLeast(50)

    fun canUseFullScreen(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        } else {
            true
        }
}
