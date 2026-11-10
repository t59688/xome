package com.example

import com.example.update.pollForAppUpdates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodicUpdateChecksTest {
    @Test fun checksImmediatelyAndEveryFifteenMinutesUntilStopped() = runTest {
        var checks = 0
        val job = launch { pollForAppUpdates { checks++ } }
        runCurrent()
        assertEquals(1, checks)
        advanceTimeBy(15 * 60_000L - 1)
        runCurrent()
        assertEquals(1, checks)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, checks)
        job.cancelAndJoin()
        advanceTimeBy(30 * 60_000L)
        runCurrent()
        assertEquals(2, checks)
        val resumed = launch { pollForAppUpdates { checks++ } }
        runCurrent()
        assertEquals(3, checks)
        resumed.cancelAndJoin()
    }

    @Test fun stoppingAlsoCancelsAnInFlightCancellableCheck() = runTest {
        var cancelled = false
        val job = launch {
            pollForAppUpdates {
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(cancelled)
    }
}
