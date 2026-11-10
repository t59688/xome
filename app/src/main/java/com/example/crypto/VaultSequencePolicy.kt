package com.example.crypto

/** New secrets must be reproducible using the calculator keypad; old vaults remain unlockable. */
object VaultSequencePolicy {
    private const val SYMBOLS = "+-*/().%^!−×÷"

    fun normalize(value: String): String = value.trim()
        .replace('−', '-').replace('×', '*').replace('÷', '/')

    fun isValidNewSequence(value: CharSequence): Boolean {
        val sequence = value.trim()
        return sequence.length in 6..16 &&
            sequence.any { it in '0'..'9' } && sequence.any { it in SYMBOLS } &&
            sequence.all { it in '0'..'9' || it in SYMBOLS }
    }

    fun isValidLegacySequence(value: CharSequence): Boolean =
        value.trim().let { it.length in 4..16 && it.none(Char::isISOControl) }
}
