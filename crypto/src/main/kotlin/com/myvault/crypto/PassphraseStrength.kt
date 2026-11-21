package com.myvault.crypto

import kotlin.math.ln
import kotlin.math.min

/**
 * A deliberately conservative strength heuristic for the master passphrase. It is an estimate,
 * not a guarantee: it catches short, repetitive and obviously common choices, and nudges the
 * user towards length. The real protection against guessing is Argon2id plus a long passphrase.
 */
object PassphraseStrength {
    const val MIN_LENGTH = 12

    enum class Level { TOO_SHORT, WEAK, FAIR, STRONG, EXCELLENT }

    data class Assessment(val level: Level, val estimatedBits: Int, val hint: String?) {
        /** Whether the passphrase may be used as a master passphrase. */
        val acceptable: Boolean get() = level >= Level.FAIR
    }

    fun assess(passphrase: CharSequence): Assessment {
        if (passphrase.length < MIN_LENGTH) {
            return Assessment(Level.TOO_SHORT, 0, "Use at least $MIN_LENGTH characters.")
        }
        val lowered = passphrase.toString().lowercase()
        if (COMMON_FRAGMENTS.any { it in lowered }) {
            return Assessment(Level.WEAK, 0, "Avoid common words and patterns like \"password\" or \"12345\".")
        }
        if (passphrase.toSet().size < 6) {
            return Assessment(Level.WEAK, 0, "Too repetitive. Mix more different characters or words.")
        }

        val bits = min(characterBits(passphrase), wordBits(passphrase)).toInt()
        val level = when {
            bits < 45 -> Level.WEAK
            bits < 65 -> Level.FAIR
            bits < 90 -> Level.STRONG
            else -> Level.EXCELLENT
        }
        val hint = when (level) {
            Level.WEAK -> "Make it longer, or add unrelated words."
            Level.FAIR -> "Good start. A few more words or characters would make it much stronger."
            else -> null
        }
        return Assessment(level, bits, hint)
    }

    /** Length times alphabet size, discounting repeated and sequential characters. */
    private fun characterBits(passphrase: CharSequence): Double {
        var pool = 0
        if (passphrase.any { it in 'a'..'z' }) pool += 26
        if (passphrase.any { it in 'A'..'Z' }) pool += 26
        if (passphrase.any { it in '0'..'9' }) pool += 10
        if (passphrase.any { it.code < 128 && !it.isLetterOrDigit() }) pool += 33
        if (passphrase.any { it.code >= 128 }) pool += 100

        var effectiveLength = 0.0
        var previous: Char? = null
        for (c in passphrase) {
            val delta = previous?.let { c.code - it.code }
            effectiveLength += if (delta != null && delta in -1..1) 0.25 else 1.0
            previous = c
        }
        return effectiveLength * ln(pool.toDouble()) / ln(2.0)
    }

    /**
     * Passphrases made of plain words are judged per word (about 12 bits each, roughly a
     * diceware word) rather than per character, since their letters are far from random.
     */
    private fun wordBits(passphrase: CharSequence): Double {
        val words = passphrase.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        val allPlainWords = words.size >= 3 && words.all { word -> word.all(Char::isLetter) }
        return if (allPlainWords) words.toSet().size * 12.0 else Double.MAX_VALUE
    }

    private val COMMON_FRAGMENTS = listOf(
        "password", "passphrase", "qwerty", "asdfgh", "12345", "letmein", "iloveyou", "welcome", "myvault", "admin123",
    )
}
