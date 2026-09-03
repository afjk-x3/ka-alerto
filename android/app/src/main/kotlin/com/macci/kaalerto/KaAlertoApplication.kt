package com.macci.kaalerto

import android.app.Application
import org.maplibre.android.MapLibre

/**
 * MapLibre must be initialised before any [org.maplibre.android.maps.MapView] is
 * constructed, so it happens here rather than in an activity.
 *
 * No API key: the style is self-hosted / keyless by design (docs/03-architecture.md §520).
 *
 * The dependency itself is `android-sdk-opengl`, not the default `android-sdk` — see the
 * comment in `gradle/libs.versions.toml` on `maplibre-android-sdk`. The default artifact
 * is Vulkan-only, and Vulkan produced a `SurfaceView` that self-reported as a loaded map
 * (confirmed via the on-screen view hierarchy) but painted every pixel black on this dev
 * machine's x86_64 emulator. No explicit `RenderingEngine.Type` selection needed here —
 * this artifact only supports OpenGL.
 */
class KaAlertoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
