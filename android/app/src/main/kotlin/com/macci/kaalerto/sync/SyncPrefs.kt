package com.macci.kaalerto.sync

import android.content.Context

/**
 * Local-only sync state — same small-SharedPreferences-file shape as
 * `geofence/HomeLocationStore.kt`. The server address lives here rather than alongside
 * `identity/LocalIdentity.kt`'s own prefs, even though `identity/ProfileScreen.kt` is
 * what edits it: `HomeLocationStore` already sets this precedent, living next to its
 * actual consumer (`geofence/GeofenceNotifier.kt`) rather than the screen that writes to
 * it.
 */
object SyncPrefs {
    private const val PREFS = "kaalerto_sync"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_CURSOR = "cursor"
    private const val KEY_LAST_SYNCED_AT_MS = "last_synced_at_ms"

    /**
     * Null (the default — nothing has ever been saved, or an empty string was saved)
     * means sync is off. `sync/ServerSyncLoop.kt` skips its whole cycle body rather than
     * trying an empty URL.
     */
    fun getServerUrl(context: Context): String? = prefs(context).getString(KEY_SERVER_URL, null)?.ifBlank { null }

    /**
     * Changing the server address resets the pull cursor to 0. A cursor is meaningless
     * outside the server that issued it (`server/src/db.js`'s `seq` is a per-database
     * monotonic counter, not a global one) — carrying an old cursor over to a new address
     * would make `pullDelta` request `since=<a value the new server never issued>` and
     * silently get nothing back forever. This covers a changed address; a *same* address
     * whose underlying database was reset is covered separately by
     * `ServerSync.cursorIsStale` comparing against the server's own `/health` cursor at the
     * start of every pull cycle.
     */
    fun setServerUrl(context: Context, url: String?) {
        val newValue = url?.trim()?.ifBlank { null }
        val previous = getServerUrl(context)
        prefs(context).edit().putString(KEY_SERVER_URL, url).apply()
        if (newValue != previous) {
            setCursor(context, 0L)
        }
    }

    /**
     * The pull cursor — a server-assigned `seq`, never a client timestamp. `0` is the
     * correct starting value: `server/src/db.js`'s `selectEventsSince` treats `since=0`
     * as "everything," same as a device that has never synced before.
     */
    fun getCursor(context: Context): Long = prefs(context).getLong(KEY_CURSOR, 0L)

    fun setCursor(context: Context, cursor: Long) {
        prefs(context).edit().putLong(KEY_CURSOR, cursor).apply()
    }

    /** Null means "no sync has ever succeeded" — rendered by `identity/ProfileFields.kt`'s
     * `ServerUrlField` as "no sync yet," never a bad-news state. */
    fun getLastSyncedAtMs(context: Context): Long? =
        prefs(context).getLong(KEY_LAST_SYNCED_AT_MS, -1L).takeIf { it >= 0 }

    fun setLastSyncedAtMs(context: Context, atMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNCED_AT_MS, atMs).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
