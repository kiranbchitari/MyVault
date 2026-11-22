package com.myvault.crypto

import java.security.SecureRandom

/** Random password generation backed by [SecureRandom]. */
class PasswordGenerator(private val random: SecureRandom) {

    data class Options(
        val length: Int = 20,
        val uppercase: Boolean = true,
        val lowercase: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true,
        val excludeAmbiguous: Boolean = false,
    ) {
        /** Character classes in use, after removing look-alike characters if requested. */
        val classes: List<String>
            get() = listOfNotNull(
                UPPERCASE.takeIf { uppercase },
                LOWERCASE.takeIf { lowercase },
                DIGITS.takeIf { digits },
                SYMBOLS.takeIf { symbols },
            ).map { set -> if (excludeAmbiguous) set.filterNot { it in AMBIGUOUS } else set }

        val isValid: Boolean get() = classes.isNotEmpty() && length in MIN_LENGTH..MAX_LENGTH
    }

    /**
     * Every enabled class appears at least once, the remaining characters are drawn uniformly
     * from the combined alphabet, and the result is shuffled so class positions are not predictable.
     */
    fun generate(options: Options): String {
        require(options.isValid) { "Select at least one character type and a length of $MIN_LENGTH to $MAX_LENGTH" }
        val classes = options.classes
        val alphabet = classes.joinToString("")
        val chars = CharArray(options.length)
        classes.forEachIndexed { index, set -> chars[index] = set[random.nextInt(set.length)] }
        for (i in classes.size until options.length) chars[i] = alphabet[random.nextInt(alphabet.length)]
        for (i in chars.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val swap = chars[i]
            chars[i] = chars[j]
            chars[j] = swap
        }
        return String(chars).also { chars.fill('\u0000') }
    }

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 64
        const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        const val DIGITS = "0123456789"
        const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"
        const val AMBIGUOUS = "0O1lI|5S2Z8B"
    }
}
