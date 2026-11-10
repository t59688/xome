package com.example.update

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal const val UPDATE_POLL_INTERVAL_MS = 15 * 60_000L

/** The caller owns this coroutine's foreground lifecycle; repository checks retain their throttle. */
internal suspend fun pollForAppUpdates(check: suspend () -> Unit) {
    while (currentCoroutineContext().isActive) {
        check()
        delay(UPDATE_POLL_INTERVAL_MS)
    }
}
