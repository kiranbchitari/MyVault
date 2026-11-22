package com.myvault.crypto

import com.myvault.crypto.PasswordGenerator.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PasswordGeneratorTest {
    private val generator = PasswordGenerator(SecureRandom())

    @Test
    fun `honours the requested length`() {
        for (length in listOf(8, 12, 20, 33, 64)) assertEquals(length, generator.generate(Options(length = length)).length)
    }

    @Test
    fun `every enabled class appears and disabled classes never do`() {
        repeat(300) {
            val all = generator.generate(Options(length = 8))
            assertTrue(all.any { it in PasswordGenerator.UPPERCASE })
            assertTrue(all.any { it in PasswordGenerator.LOWERCASE })
            assertTrue(all.any { it in PasswordGenerator.DIGITS })
            assertTrue(all.any { it in PasswordGenerator.SYMBOLS })

            val digitsOnly = generator.generate(Options(length = 12, uppercase = false, lowercase = false, symbols = false))
            assertTrue(digitsOnly.all { it in PasswordGenerator.DIGITS })

            val lettersOnly = generator.generate(Options(length = 16, digits = false, symbols = false))
            assertTrue(lettersOnly.all(Char::isLetter))
        }
    }

    @Test
    fun `ambiguous characters can be excluded`() {
        repeat(300) {
            val password = generator.generate(Options(length = 40, excludeAmbiguous = true))
            assertFalse(password, password.any { it in PasswordGenerator.AMBIGUOUS })
        }
    }

    @Test
    fun `passwords do not repeat and use the whole alphabet`() {
        val passwords = List(2_000) { generator.generate(Options(length = 24)) }
        assertEquals(passwords.size, passwords.toSet().size)
        val seen = passwords.joinToString("").toSet()
        val alphabet = (PasswordGenerator.UPPERCASE + PasswordGenerator.LOWERCASE + PasswordGenerator.DIGITS + PasswordGenerator.SYMBOLS).toSet()
        assertEquals(alphabet, seen)
    }

    @Test
    fun `class characters are not stuck at the start`() {
        val firstCharIsUpper = (1..2_000).count { generator.generate(Options(length = 16))[0] in PasswordGenerator.UPPERCASE }
        // Unshuffled output would be upper-case first every time; uniform would be about 30%.
        assertTrue("$firstCharIsUpper", firstCharIsUpper in 400..900)
    }

    @Test
    fun `invalid options are refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(Options(uppercase = false, lowercase = false, digits = false, symbols = false))
        }
        assertThrows(IllegalArgumentException::class.java) { generator.generate(Options(length = 4)) }
        assertThrows(IllegalArgumentException::class.java) { generator.generate(Options(length = 500)) }
    }
}
