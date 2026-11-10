package com.example

import com.example.crypto.VaultExternalUiLockPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultExternalUiLockPolicyTest {
    @Test
    fun normalBackgroundAlwaysLocks() {
        val policy = VaultExternalUiLockPolicy()
        assertTrue(policy.shouldLockOnStop())
        assertFalse(policy.shouldLockOnResume())
    }

    @Test
    fun anExplicitSystemPickerRoundTripKeepsSessionOnlyUntilResult() {
        val policy = VaultExternalUiLockPolicy()
        policy.onLaunch()
        assertFalse(policy.shouldLockOnStop())
        policy.onResultOrFailure()
        assertTrue(policy.shouldLockOnStop())
        assertFalse(policy.shouldLockOnResume())
    }

    @Test
    fun cancellationOrLaunchFailureCannotLeaveAnExemption() {
        val policy = VaultExternalUiLockPolicy()
        policy.onLaunch()
        policy.onResultOrFailure()
        assertTrue(policy.shouldLockOnStop())
    }

    @Test
    fun returningWithoutPickerResultRequiresLock() {
        val policy = VaultExternalUiLockPolicy()
        policy.onLaunch()
        assertTrue(policy.shouldLockOnResume())
    }
}
