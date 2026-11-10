package com.example.crypto

/**
 * Limits the unlocked-session exception to one explicit Android system UI round trip.
 * A missing result must not leave the app unlocked when its own activity resumes.
 */
internal class VaultExternalUiLockPolicy {
    var awaitingResult: Boolean = false
        private set

    fun onLaunch() {
        awaitingResult = true
    }

    fun onResultOrFailure() {
        awaitingResult = false
    }

    fun shouldLockOnStop(): Boolean = !awaitingResult

    fun shouldLockOnResume(): Boolean = awaitingResult
}
