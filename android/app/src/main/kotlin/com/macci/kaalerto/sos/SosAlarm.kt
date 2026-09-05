package com.macci.kaalerto.sos

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "SosAlarm"

private const val DIT_MS = 180L
private const val DAH_MS = 540L
private const val GAP_MS = 140L
private const val LETTER_GAP_MS = 380L

/**
 * The rescue card's "Patunugin" button — `docs/03-architecture.md` §6.4.2: a loud
 * periodic audible SOS pattern (· · · — — — · · ·), duty-cycled to conserve battery,
 * with a manual button "for when someone is heard nearby".
 *
 * Morse rather than a siren for two reasons. It is the one distress pattern a stranger
 * might actually recognise, and its silences are most of its duration — a continuous
 * tone would drain a battery that §6.4.1 needs to last "hours to days".
 *
 * Deliberately **not** started automatically. §6.4.2 says both the sound and the strobe
 * are user-controllable, because "a family hiding from a hazard may need silence" — a
 * phone that starts screaming on its own could be the thing that gets someone hurt.
 *
 * Uses [ToneGenerator] on the alarm stream so it is audible through a silenced ringer,
 * and because a bundled audio asset would be one more thing to ship for a square wave.
 */
class SosAlarm {

    private var tone: ToneGenerator? = null
    private var job: Job? = null

    val isSounding: Boolean get() = job?.isActive == true

    fun start(scope: CoroutineScope, onStopped: () -> Unit) {
        if (isSounding) return
        val generator = runCatching {
            ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
        }.getOrElse { error ->
            // Some devices refuse a second generator while another app holds one.
            // Failing to make a noise must never take down the rescue card.
            Log.w(TAG, "ToneGenerator unavailable", error)
            onStopped()
            return
        }
        tone = generator

        job = scope.launch {
            try {
                while (isActive) {
                    repeat(3) { beep(generator, DIT_MS) }
                    delay(LETTER_GAP_MS)
                    repeat(3) { beep(generator, DAH_MS) }
                    delay(LETTER_GAP_MS)
                    repeat(3) { beep(generator, DIT_MS) }
                    // The duty cycle: a long silence between repetitions, so this can
                    // run for hours and so a listener can call back into the gap.
                    delay(4_000)
                }
            } finally {
                release()
                onStopped()
            }
        }
    }

    private suspend fun beep(generator: ToneGenerator, durationMs: Long) {
        generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, durationMs.toInt())
        delay(durationMs + GAP_MS)
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun release() {
        runCatching { tone?.stopTone() }
        runCatching { tone?.release() }
        tone = null
    }
}
