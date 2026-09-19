package com.macci.kaalerto.sync

import android.content.Context

/**
 * When a FULL push (everything local, not just a fresh report) last succeeded. The purge
 * (`EventRepository.deleteExpired`) reads it so a report is never deleted before it has had
 * a chance to reach the cloud, however long the phone was offline. One long in
 * SharedPreferences rather than a per-event column: a Room schema change would wipe every
 * phone's local store, unsynced reports included.
 */
object PushState {
    private const val PREFS = "kaalerto_push"
    private const val KEY = "last_full_push_ok_ms"

    fun lastOkMs(context: Context): Long = prefs(context).getLong(KEY, 0L)

    fun markOk(context: Context, atMs: Long) {
        prefs(context).edit().putLong(KEY, atMs).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
