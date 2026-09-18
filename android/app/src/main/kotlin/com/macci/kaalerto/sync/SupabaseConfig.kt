package com.macci.kaalerto.sync

/**
 * Baked into the app, unlike [SyncPrefs]'s Node-server address — the whole point of using
 * Supabase is one fixed URL every install already knows, no typing, no LAN discovery.
 *
 * **Fill these in after running `supabase/schema.sql` in your project's SQL editor:**
 * Project Settings → API → Project URL and `anon` `public` key. The anon key is safe to
 * ship in the app — it is a public, rate-limited key, not a secret; write access is
 * controlled by the RLS policy in `schema.sql`, which — matching ground rule 4, no auth —
 * allows anyone holding it to read and write, the same posture the Node server already
 * has with zero access control.
 *
 * Blank means Supabase sync is off, same as [SyncPrefs.getServerUrl] being null for the
 * Node server — [SupabaseSyncLoop] no-ops every cycle rather than failing.
 */
object SupabaseConfig {
    const val URL = ""
    const val ANON_KEY = ""

    val isConfigured: Boolean get() = URL.isNotBlank() && ANON_KEY.isNotBlank()
}
