package com.myvault.app.security

import com.myvault.crypto.MalformedDataException
import com.myvault.crypto.UnsupportedFormatException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BiometricSlotTest {
    private val iv = ByteArray(12) { it.toByte() }
    private val ciphertext = ByteArray(48) { (it + 100).toByte() }

    @Test
    fun `encode then decode preserves IV and ciphertext`() {
        val decoded = BiometricSlot.decode(BiometricSlot(iv, ciphertext).encode())
        assertArrayEquals(iv, decoded.iv)
        assertArrayEquals(ciphertext, decoded.ciphertext)
    }

    @Test
    fun `unknown versions are refused`() {
        val encoded = BiometricSlot(iv, ciphertext).encode().also { it[0] = 2 }
        assertThrows(UnsupportedFormatException::class.java) { BiometricSlot.decode(encoded) }
    }

    @Test
    fun `truncated or empty slots are refused`() {
        val encoded = BiometricSlot(iv, ciphertext).encode()
        assertThrows(MalformedDataException::class.java) { BiometricSlot.decode(ByteArray(0)) }
        assertThrows(MalformedDataException::class.java) { BiometricSlot.decode(encoded.copyOf(1)) }
        assertThrows(MalformedDataException::class.java) { BiometricSlot.decode(encoded.copyOf(20)) }
        assertThrows(MalformedDataException::class.java) { BiometricSlot.decode(encoded.copyOf().also { it[1] = 0 }) }
    }

    @Test
    fun `associated data is bound to the vault`() {
        val a = BiometricSlot.associatedData(ByteArray(16) { 1 })
        val b = BiometricSlot.associatedData(ByteArray(16) { 2 })
        assertFalse(a.contentEquals(b))
    }
}
