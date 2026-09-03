package com.macci.kaalerto

import android.app.Application
import org.maplibre.android.MapLibre

/**
 * MapLibre must be initialised before any [org.maplibre.android.maps.MapView] is
 * constructed, so it happens here rather than in an activity.
 *
 * No API key: the style is self-hosted / keyless by design (docs/03-architecture.md §520).
 */
class KaAlertoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
