package com.example.crypto

/**
 * Unlock sequences are reproducible calculator expressions (6..64 chars).
 * Extended length lets users combine multi-step expressions alongside the Keyfile pepper.
 */
object VaultSequencePolicy {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 64
    private const val SYMBOLS = "+-*/().%^!−×÷"

    fun normalize(value: String): String = value.trim()
        .replace('−', '-').replace('×', '*').replace('÷', '/')

    fun isValidNewSequence(value: CharSequence): Boolean {
        val sequence = value.trim()
        return sequence.length in MIN_LENGTH..MAX_LENGTH &&
            sequence.any { it in '0'..'9' } &&
            sequence.any { it in SYMBOLS } &&
            sequence.all { it in '0'..'9' || it in SYMBOLS }
    }

    fun isValidUnlockCandidate(value: CharSequence): Boolean =
        value.trim().let { it.length in MIN_LENGTH..MAX_LENGTH && it.none(Char::isISOControl) }
}
