package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.VaultUnlockThrottle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultUnlockThrottleTest {
    @Test fun repeatedGuessesAreBlockedAndCooldownSurvivesRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("throttle-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        var now = 100_000L
        val throttle = VaultUnlockThrottle(prefs) { now }
        repeat(5) { assertTrue(throttle.tryAcquire()) }
        assertFalse(throttle.tryAcquire())
        val recreated = VaultUnlockThrottle(prefs) { now }
        assertFalse(recreated.tryAcquire())
        now += 30_000L
        assertTrue(recreated.tryAcquire())
        assertFalse(recreated.tryAcquire())
        recreated.onSuccess()
        assertTrue(recreated.tryAcquire())
    }

    @Test fun rollingBackClockDoesNotRemoveCooldown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("throttle-clock-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        var now = 100_000L
        val throttle = VaultUnlockThrottle(prefs) { now }
        repeat(5) { assertTrue(throttle.tryAcquire()) }
        now = 1L
        assertFalse(throttle.tryAcquire())
    }
}
