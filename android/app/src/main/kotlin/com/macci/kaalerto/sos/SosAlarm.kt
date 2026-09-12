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
 * periodic audible SOS pattern (· · · — — — · · ·), duty-cycled to conserve battery.
 *
 * Morse rather than a siren: it is the one distress pattern a stranger might recognise,
 * and its silences are most of its duration, so it can run for hours.
 *
 * Deliberately **not** started automatically — §6.4.2 makes sound user-controllable,
 * because "a family hiding from a hazard may need silence".
 *
 * [ToneGenerator] on the alarm stream, so it is audible through a silenced ringer. Ported
 * verbatim from feat/event-sourced-roles.
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
                    // The duty cycle: a long silence between repetitions, so this can run
                    // for hours and so a listener can call back into the gap.
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
