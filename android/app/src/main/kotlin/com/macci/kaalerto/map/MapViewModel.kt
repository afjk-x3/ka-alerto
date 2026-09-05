package com.macci.kaalerto.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.FeatureState
import com.macci.kaalerto.data.FeatureSummary
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.data.Reducer
import com.macci.kaalerto.data.SeedLoader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val database = KaAlertoDatabase.getInstance(application)
    private val repository = EventRepository(database.eventDao())

    /**
     * One [FeatureSummary] per feature, not one marker per event — day 4's reducer
     * output, recomputed fresh on every event change rather than read back from
     * `feature_state` (docs/03-architecture.md §5.1: "cheap enough to recompute").
     */
    val featureSummaries: StateFlow<List<FeatureSummary>> = repository.observeAll()
        .map { events -> Reducer.summarizeAll(events, System.currentTimeMillis()) }
        .onEach { summaries -> persist(summaries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            SeedLoader(application, repository).loadIfEmpty()
        }
    }

    /**
     * Populates `feature_state` as a side effect — day 2 left this table schema-only.
     * Nothing reads it back for correctness; it exists so the table isn't dead weight
     * and so it can be inspected directly (e.g. for the LGU dashboard sync, day 13).
     */
    private fun persist(summaries: List<FeatureSummary>) {
        viewModelScope.launch {
            val dao = database.featureStateDao()
            summaries.forEach { summary ->
                dao.upsert(
                    FeatureState(
                        featureRef = summary.featureRef,
                        severity = summary.severity,
                        confidence = summary.confidence,
                        bucket = summary.bucket,
                        isConflicted = summary.isConflicted,
                        lastReportMs = summary.lastEventMs,
                    ),
                )
            }
        }
    }
}
