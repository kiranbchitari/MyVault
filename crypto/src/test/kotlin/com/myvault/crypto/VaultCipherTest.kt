package com.myvault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.SecureRandom

class VaultCipherTest {
    private val random = SecureRandom()
    private val cipher = VaultCipher(random)
    private val key = SecretBytes.random(32, random)
    private val context = "item/42".toByteArray()
    private val plaintext = "account number 0011223344".toByteArray()

    @Test
    fun `AES-256-GCM matches the NIST known-answer vector`() {
        // McGrew & Viega GCM spec, test case 14: 256-bit zero key, zero nonce, one zero block.
        val out = AesGcm.encrypt(ByteArray(32), ByteArray(12), ByteArray(16), ByteArray(0))
        assertEquals("cea7403d4d606b6e074ec5d3baf39d18" + "d0d1c8a799996bf0265b98b5d48ab919", out.toHex())
    }

    @Test
    fun `seal then open returns the plaintext`() {
        val sealed = cipher.seal(key, 7, plaintext, context)
        assertArrayEquals(plaintext, cipher.open(key, sealed, context))
        assertEquals(7, cipher.keyGenerationOf(sealed))
    }

    @Test
    fun `empty plaintext round-trips`() {
        val sealed = cipher.seal(key, 1, ByteArray(0), context)
        assertEquals(0, cipher.open(key, sealed, context).size)
    }

    @Test
    fun `ciphertext does not contain the plaintext`() {
        val sealed = cipher.seal(key, 1, plaintext, context)
        assertEquals(-1, sealed.indexOfSubArray(plaintext))
        assertEquals(-1, sealed.indexOfSubArray("0011223344".toByteArray()))
    }

    @Test
    fun `flipping any single bit is rejected`() {
        val sealed = cipher.seal(key, 1, plaintext, context)
        for (i in sealed.indices) {
            for (bit in 0 until 8) {
                val tampered = sealed.copyOf().also { it[i] = (it[i].toInt() xor (1 shl bit)).toByte() }
                assertThrows("byte $i bit $bit", VaultCryptoException::class.java) {
                    cipher.open(key, tampered, context)
                }
            }
        }
    }

    @Test
    fun `tampering with the ciphertext body or tag is an authentication failure`() {
        val sealed = cipher.seal(key, 1, plaintext, context)
        for (i in VaultCipher.HEADER_BYTES until sealed.size) {
            val tampered = sealed.copyOf().also { it[i] = (it[i].toInt() xor 0x80).toByte() }
            assertThrows(AuthenticationFailedException::class.java) { cipher.open(key, tampered, context) }
        }
    }

    @Test
    fun `changing the key generation in the header is an authentication failure`() {
        val sealed = cipher.seal(key, 1, plaintext, context)
        sealed[5] = 2
        assertThrows(AuthenticationFailedException::class.java) { cipher.open(key, sealed, context) }
    }

    @Test
    fun `wrong key is rejected`() {
        val sealed = cipher.seal(key, 1, plaintext, context)
        val other = SecretBytes.random(32, random)
        assertThrows(AuthenticationFailedException::class.java) { cipher.open(other, sealed, context) }
    }

    @Test
    fun `wrong context is rejected`() {
        val sealed = cipher.seal(key, 1, plaintext, context)
        assertThrows(AuthenticationFailedException::class.java) {
            cipher.open(key, sealed, "item/43".toByteArray())
        }
    }

    @Test
    fun `truncated data is rejected`() {
        val sealed = cipher.seal(key, 1, plaintext, context)
        assertThrows(MalformedDataException::class.java) { cipher.open(key, sealed.copyOf(20), context) }
        assertThrows(AuthenticationFailedException::class.java) {
            cipher.open(key, sealed.copyOf(sealed.size - 1), context)
        }
    }

    @Test
    fun `unknown version and algorithm are rejected as unsupported`() {
        val sealed = cipher.seal(key, 1, plaintext, context)
        assertThrows(UnsupportedFormatException::class.java) {
            cipher.open(key, sealed.copyOf().also { it[0] = 2 }, context)
        }
        assertThrows(UnsupportedFormatException::class.java) {
            cipher.open(key, sealed.copyOf().also { it[1] = 9 }, context)
        }
    }

    @Test
    fun `every seal uses a fresh nonce`() {
        val count = 20_000
        val nonces = HashSet<String>(count * 2)
        repeat(count) {
            val sealed = cipher.seal(key, 1, plaintext, context)
            nonces += sealed.copyOfRange(VaultCipher.HEADER_BYTES, VaultCipher.HEADER_BYTES + 12).toHex()
        }
        assertEquals(count, nonces.size)
    }

    @Test
    fun `sealing the same plaintext twice gives different ciphertexts`() {
        val a = cipher.seal(key, 1, plaintext, context)
        val b = cipher.seal(key, 1, plaintext, context)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `keys of the wrong size are refused`() {
        val shortKey = SecretBytes.random(16, random)
        assertThrows(IllegalArgumentException::class.java) { cipher.seal(shortKey, 1, plaintext, context) }
    }
}
