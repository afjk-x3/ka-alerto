package com.macci.kaalerto.map

import android.content.Context
import android.util.Log
import com.macci.kaalerto.demo.DemoArea
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

private const val TAG = "OfflineMapPack"

/**
 * State of the offline tile pack for [DemoArea].
 *
 * [Ready] is the only state in which the map is trustworthy with the network off, so it
 * is the one the UI must be honest about. Never show a map that looks complete while the
 * pack is still downloading — the whole product claim is that what you see offline is
 * what you would have seen online.
 */
sealed interface PackState {
    /** Haven't looked yet. */
    data object Unknown : PackState

    /** No pack on this device. The map will only render tiles it can fetch live. */
    data object Absent : PackState

    data class Downloading(
        /** Tiles alone — shown to the user, because "tiles" is legible and "resources" is not. */
        val completedTiles: Long,
        /** Tiles *plus* style, fonts and sprites. Only this is comparable to [requiredResources]. */
        val completedResources: Long,
        val completedBytes: Long,
        /** Null until MapLibre knows the total; it is an estimate until [isPrecise]. */
        val requiredResources: Long?,
        val isPrecise: Boolean,
    ) : PackState {
        /**
         * Resources completed over resources required.
         *
         * Both sides must be resource counts. Dividing tiles by resources understates
         * progress, because the denominator also covers the style, glyphs and sprites.
         */
        val fraction: Float?
            get() = requiredResources
                ?.takeIf { it > 0 }
                ?.let { (completedResources.toFloat() / it).coerceIn(0f, 1f) }
    }

    /** Tiles are on disk. This device can render the demo area with no network. */
    data class Ready(val tileCount: Long, val bytes: Long) : PackState

    data class Failed(val reason: String) : PackState
}

/**
 * Downloads and tracks the offline tile pack.
 *
 * This is build day 1 and the project's hard gate: if tiles cannot be made to render in
 * airplane mode, the premise fails, and it is worth finding that out now (BUILD_TASKS.md).
 *
 * **The download needs a network exactly once.** Pre-load every demo device beforehand;
 * never trigger this on stage. If [OfflineManager] proves unreliable, the documented
 * fallback is to bundle tiles in `assets/` and point a local style at them — the
 * bulletproof path, at the cost of a larger APK.
 */
class OfflineMapPack(context: Context) {

    // MapLibre holds this for the process lifetime; use the application context so a
    // rotated activity cannot leak through it.
    private val manager = OfflineManager.getInstance(context.applicationContext)

    private val _state = MutableStateFlow<PackState>(PackState.Unknown)
    val state: StateFlow<PackState> = _state.asStateFlow()

    private var region: OfflineRegion? = null

    /**
     * Finds an existing pack, or creates and downloads one.
     *
     * Safe to call on every launch: an already-complete region reports [PackState.Ready]
     * without re-downloading anything.
     */
    fun ensureDownloaded() {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val existing = offlineRegions?.firstOrNull { it.isOurs() }
                if (existing != null) {
                    Log.i(TAG, "Found existing pack (id=${existing.id})")
                    adopt(existing)
                } else {
                    Log.i(TAG, "No pack on this device; creating one")
                    _state.value = PackState.Absent
                    create()
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "listOfflineRegions failed: $error")
                _state.value = PackState.Failed(error)
            }
        })
    }

    private fun create() {
        val definition = OfflineTilePyramidRegionDefinition(
            DemoArea.STYLE_URL,
            DemoArea.bounds,
            DemoArea.MIN_ZOOM,
            DemoArea.MAX_ZOOM,
            // Pixel ratio is baked into the pack. Use the densest we expect to demo on:
            // a pack built for 1x looks soft on a 3x screen, and re-downloading on stage
            // is not an option.
            3f,
        )

        manager.createOfflineRegion(
            definition,
            DemoArea.REGION_NAME.toByteArray(Charsets.UTF_8),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) = adopt(offlineRegion)

                override fun onError(error: String) {
                    Log.e(TAG, "createOfflineRegion failed: $error")
                    _state.value = PackState.Failed(error)
                }
            },
        )
    }

    /** Attaches an observer and starts (or resumes) the download. */
    private fun adopt(offlineRegion: OfflineRegion) {
        region = offlineRegion

        offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                _state.value = status.toPackState()
                if (status.isComplete) {
                    // Stop the region observing once it is done, or it keeps a callback
                    // alive for a download that will never progress again.
                    offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    Log.i(TAG, "Pack complete: ${status.completedTileCount} tiles, ${status.completedTileSize} bytes")
                }
            }

            override fun onError(error: OfflineRegionError) {
                // Reaching the device's storage limit or losing the network mid-download
                // is recoverable — whatever arrived stays on disk and resumes next launch.
                Log.w(TAG, "Pack download error: ${error.reason} ${error.message}")
                _state.value = PackState.Failed("${error.reason}: ${error.message}")
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                Log.e(TAG, "Tile count limit exceeded ($limit) — narrow DemoArea or lower MAX_ZOOM")
                _state.value = PackState.Failed("Tile limit exceeded ($limit)")
            }
        })

        // Ask for current status too: a region completed on a previous launch emits no
        // status change, so without this it would sit at Unknown forever.
        // This callback is declared without nullability annotations, so the overrides
        // must accept platform nulls even though MapLibre always passes a value.
        offlineRegion.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status == null) {
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    return
                }
                _state.value = status.toPackState()
                if (!status.isComplete) {
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }
            }

            override fun onError(error: String?) {
                Log.w(TAG, "getStatus failed: $error; starting download anyway")
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }
        })
    }

    /** Detaches observers. The download itself continues in MapLibre's own thread. */
    fun release() {
        region?.setObserver(null)
        region = null
    }

    private fun OfflineRegion.isOurs(): Boolean =
        runCatching { String(metadata, Charsets.UTF_8) }.getOrNull() == DemoArea.REGION_NAME

    private fun OfflineRegionStatus.toPackState(): PackState = when {
        isComplete -> PackState.Ready(completedTileCount, completedTileSize)
        else -> PackState.Downloading(
            completedTiles = completedTileCount,
            completedResources = completedResourceCount,
            completedBytes = completedResourceSize,
            requiredResources = requiredResourceCount.takeIf { it > 0 },
            isPrecise = isRequiredResourceCountPrecise,
        )
    }
}
