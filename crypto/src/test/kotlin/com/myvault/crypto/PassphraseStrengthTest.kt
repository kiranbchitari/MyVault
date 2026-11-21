package com.myvault.crypto

import com.myvault.crypto.PassphraseStrength.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassphraseStrengthTest {
    @Test
    fun `short passphrases are refused`() {
        val result = PassphraseStrength.assess("Tr0ub4dor&3")
        assertEquals(Level.TOO_SHORT, result.level)
        assertFalse(result.acceptable)
    }

    @Test
    fun `common and repetitive choices are weak`() {
        for (weak in listOf("password123456", "qwertyuiop[]\\", "aaaaaaaaaaaaaaaa", "abababababababab", "abcdefghijklmnop")) {
            val result = PassphraseStrength.assess(weak)
            assertEquals(weak, Level.WEAK, result.level)
            assertFalse(weak, result.acceptable)
        }
    }

    @Test
    fun `word passphrases are judged per word`() {
        assertEquals(Level.WEAK, PassphraseStrength.assess("my secret vault").level)
        assertEquals(Level.FAIR, PassphraseStrength.assess("correct horse battery staple").level)
        assertTrue(PassphraseStrength.assess("correct horse battery staple").acceptable)
        assertTrue(PassphraseStrength.assess("tidal orchid lantern copper violin mosaic").level >= Level.STRONG)
    }

    @Test
    fun `long mixed passphrases are strong`() {
        assertTrue(PassphraseStrength.assess("tidal-Orchid-lantern-42").level >= Level.STRONG)
        assertEquals(Level.EXCELLENT, PassphraseStrength.assess("v9#Lq2!xT7@pZ4\$mK8&n").level)
    }
}
