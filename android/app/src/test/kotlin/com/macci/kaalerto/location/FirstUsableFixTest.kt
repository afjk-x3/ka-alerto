package com.macci.kaalerto.location

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bug this guards: a fresh-fix request that never answers used to suspend the caller
 * forever, leaving "Mag-ulat" stuck on "Kinukuha ang lokasyon…". Every case here must
 * return, and quickly — a hang shows up as a test that never finishes.
 */
class FirstUsableFixTest {

    private val timeoutMs = 50L

    private fun <T> timed(block: suspend () -> T): Pair<T, Long> = runBlocking {
        val start = System.nanoTime()
        val result = block()
        result to (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun `a fresh fix wins and last known is never asked`() {
        var lastKnownAsked = false
        val (fix, _) = timed {
            firstUsableFix(
                fresh = { "fresh" },
                lastKnown = { lastKnownAsked = true; "old" },
                isRecent = { true },
                timeoutMs = timeoutMs,
            )
        }
        assertEquals("fresh", fix)
        assertTrue(!lastKnownAsked)
    }

    @Test
    fun `a fresh fix that never arrives falls back to a recent last known, within the bound`() {
        val (fix, elapsedMs) = timed {
            firstUsableFix(
                fresh = { awaitCancellation() },
                lastKnown = { "four minutes ago" },
                isRecent = { true },
                timeoutMs = timeoutMs,
            )
        }
        assertEquals("four minutes ago", fix)
        assertTrue("took $elapsedMs ms", elapsedMs < 1_000)
    }

    @Test
    fun `a failed fresh request falls back to last known without waiting out the timeout`() {
        val (fix, elapsedMs) = timed {
            firstUsableFix(
                fresh = { null },
                lastKnown = { "cached" },
                isRecent = { true },
                timeoutMs = 5_000,
            )
        }
        assertEquals("cached", fix)
        assertTrue("took $elapsedMs ms", elapsedMs < 1_000)
    }

    @Test
    fun `a stale last known is not used as a position`() {
        val (fix, _) = timed {
            firstUsableFix(
                fresh = { awaitCancellation() },
                lastKnown = { "an hour ago" },
                isRecent = { false },
                timeoutMs = timeoutMs,
            )
        }
        assertNull(fix)
    }

    @Test
    fun `when neither source ever answers it still returns null, bounded`() {
        val (fix, elapsedMs) = timed {
            firstUsableFix<String>(
                fresh = { awaitCancellation() },
                lastKnown = { awaitCancellation() },
                isRecent = { true },
                timeoutMs = timeoutMs,
            )
        }
        assertNull(fix)
        assertTrue("took $elapsedMs ms", elapsedMs < 1_000)
    }
}
