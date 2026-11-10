package com.example.ui.vault

/** User-entered sequences are calculator expressions, not numeric-only PINs. */
internal object VaultWizardRules {
    private const val MAX_ALIAS_LENGTH = 36

    fun validAlias(value: String): Boolean {
        val alias = value.trim()
        return alias.length in 1..MAX_ALIAS_LENGTH && alias.none(Char::isISOControl)
    }

    fun validSequence(value: String): Boolean {
        val sequence = value.trim()
        return sequence.length in 4..16 && sequence.none(Char::isISOControl)
    }

    fun confirmedSequence(value: String, confirmation: String): Boolean =
        validSequence(value) && value.trim() == confirmation.trim()

    fun changedSequence(current: String, next: String, confirmation: String): Boolean =
        validSequence(current) && confirmedSequence(next, confirmation) &&
            current.trim() != next.trim()
}
