package com.example.crypto

import android.content.SharedPreferences

/** Best-effort local throttling; copied vault files remain subject to offline guessing. */
class VaultUnlockThrottle(
    private val preferences: SharedPreferences,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    @Synchronized
    fun tryAcquire(): Boolean {
        val now = nowMillis()
        if (now < preferences.getLong("blocked_until", 0L)) return false
        val failures = (preferences.getInt("attempts", 0).coerceIn(0, 20) + 1).coerceAtMost(20)
        val delay = if (failures >= 5) {
            (30_000L shl (failures - 5).coerceAtMost(7)).coerceAtMost(3_600_000L)
        } else 0L
        // Reserve before doing expensive work so cancellation/restart cannot avoid counting a guess.
        return preferences.edit()
            .putInt("attempts", failures)
            .putLong("blocked_until", now + delay)
            .commit()
    }

    @Synchronized
    fun onSuccess() {
        preferences.edit().clear().commit()
    }
}
