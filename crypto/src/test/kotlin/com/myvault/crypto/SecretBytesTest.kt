package com.myvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class SecretBytesTest {
    @Test
    fun `close zeroes the underlying array and blocks further access`() {
        val raw = ByteArray(32) { (it + 1).toByte() }
        val secret = SecretBytes.wrap(raw)
        secret.close()
        assertTrue(raw.all { it == 0.toByte() })
        assertTrue(secret.isDestroyed)
        assertThrows(IllegalStateException::class.java) { secret.access { it.size } }
    }

    @Test
    fun `toString never reveals key bytes`() {
        val secret = SecretBytes.wrap(hex("deadbeefdeadbeef"))
        assertEquals("SecretBytes(REDACTED)", secret.toString())
        assertFalse("$secret".contains("dead", ignoreCase = true))
    }

    @Test
    fun `copy is independent of the original`() {
        val original = SecretBytes.random(32, SecureRandom())
        val copy = original.copy()
        assertTrue(original.contentEquals(copy))
        original.close()
        assertFalse(copy.isDestroyed)
        assertEquals(32, copy.size)
    }

    @Test
    fun `random secrets differ`() {
        val random = SecureRandom()
        assertFalse(SecretBytes.random(32, random).contentEquals(SecretBytes.random(32, random)))
    }
}
