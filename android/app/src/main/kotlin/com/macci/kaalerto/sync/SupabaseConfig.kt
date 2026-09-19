package com.macci.kaalerto.sync

/**
 * Baked into the app — the whole point of using Supabase is one fixed URL every install
 * already knows: no typing, no LAN discovery.
 *
 * **Fill these in after running `supabase/schema.sql` in your project's SQL editor:**
 * Project Settings → API → Project URL and `anon` `public` key. The anon key is safe to
 * ship in the app — it is a public, rate-limited key, not a secret; write access is
 * controlled by the RLS policy in `schema.sql`, which — matching ground rule 4, no auth —
 * allows anyone holding it to read and write, the same posture as the rest of the
 * sync layer (no auth).
 *
 * Blank means Supabase sync is off — [SupabaseSyncLoop] no-ops every cycle rather than failing.
 */
object SupabaseConfig {
    const val URL = "https://ahzqfpwyrzzrtyskudtw.supabase.co"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFoenFmcHd5cnp6cnR5c2t1ZHR3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODk3MjA4NTQsImV4cCI6MjEwNTI5Njg1NH0.ljj1tTgR7-dowXYi-99I9vrIqAc-FnFC8AclYGt5Iow"

    val isConfigured: Boolean get() = URL.isNotBlank() && ANON_KEY.isNotBlank()
}
