package com.macci.kaalerto.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Uploads everything local to Supabase when a connection exists, even with the app closed.
 * [SupabaseSyncLoop]'s 30 s loop only runs while the process is alive, so a report made
 * offline used to wait for the next launch if the phone rebooted or the app was
 * force-stopped. WorkManager survives both and runs once the network constraint is met
 * (15 min is Android's minimum period; the OS may batch it later under Doze).
 *
 * Push only, by design: nothing on this path needs the app open, and duplicates are
 * harmless because Supabase upserts on the event id.
 */
class SupabaseSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!SupabaseConfig.isConfigured) return Result.success()
        return runCatching { SupabaseSyncLoop(applicationContext).pushNow() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val NAME = "supabase-background-push"

        fun schedule(context: Context) {
            if (!SupabaseConfig.isConfigured) return
            val request = PeriodicWorkRequestBuilder<SupabaseSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // KEEP: re-scheduling on every launch must not reset the period.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
