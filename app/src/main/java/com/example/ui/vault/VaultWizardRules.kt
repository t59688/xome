package com.example.ui.vault

import com.example.crypto.VaultSequencePolicy

/** User-entered sequences are calculator expressions, not numeric-only PINs. */
internal object VaultWizardRules {
    private const val MAX_ALIAS_LENGTH = 36

    fun validAlias(value: String): Boolean {
        val alias = value.trim()
        return alias.length in 1..MAX_ALIAS_LENGTH && alias.none(Char::isISOControl)
    }

    fun validSequence(value: String): Boolean = VaultSequencePolicy.isValidNewSequence(value)

    fun validUnlockSequence(value: String): Boolean = VaultSequencePolicy.isValidUnlockCandidate(value)

    fun confirmedSequence(value: String, confirmation: String): Boolean =
        validSequence(value) &&
            VaultSequencePolicy.normalize(value) == VaultSequencePolicy.normalize(confirmation)

    fun changedSequence(current: String, next: String, confirmation: String): Boolean =
        validUnlockSequence(current) &&
            confirmedSequence(next, confirmation) &&
            VaultSequencePolicy.normalize(current) != VaultSequencePolicy.normalize(next)
}
