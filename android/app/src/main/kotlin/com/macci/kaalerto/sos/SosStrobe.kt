package com.macci.kaalerto.sos

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The rescue card's "Kumurap": the camera flashlight and the screen flash together, so a
 * boat or a rooftop neighbour can find the phone in the dark (PRD FR-4.5).
 *
 * Two flashes a second — 250 ms on, 250 ms off — stays under the 3-per-second line
 * photosensitive-seizure guidance draws. The flashlight needs no camera permission
 * (`setTorchMode`, API 23+); a phone without one still gets the screen flash.
 */
class SosStrobe(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val torchId: String? = runCatching {
        cameras?.cameraIdList?.firstOrNull { id ->
            cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()
    private var job: Job? = null

    /** [onLit] is told each phase so the screen can flash in step with the flashlight. */
    fun start(scope: CoroutineScope, onLit: (Boolean) -> Unit) {
        stop()
        job = scope.launch {
            var lit = true
            while (isActive) {
                setTorch(lit)
                onLit(lit)
                delay(HALF_PERIOD_MS)
                lit = !lit
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        setTorch(false)
    }

    private fun setTorch(on: Boolean) {
        val id = torchId ?: return
        runCatching { cameras?.setTorchMode(id, on) }
    }

    private companion object {
        const val HALF_PERIOD_MS = 250L
    }
}
