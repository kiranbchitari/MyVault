package com.myvault.app.security

import kotlinx.coroutines.delay

/**
 * Tracks the time since the last user interaction. [now] must be a monotonic clock in
 * milliseconds; the wall clock is never used, so changing the device time cannot extend a session.
 */
class InactivityTimer(private val now: () -> Long) {
    @Volatile
    private var lastActivity = now()

    fun touch() {
        lastActivity = now()
    }

    /** Suspends until [timeoutMs] has passed without a call to [touch]. */
    suspend fun awaitTimeout(timeoutMs: Long) {
        touch()
        while (true) {
            val remaining = lastActivity + timeoutMs - now()
            if (remaining <= 0) return
            delay(remaining)
        }
    }
}
