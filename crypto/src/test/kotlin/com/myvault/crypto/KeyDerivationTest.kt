package com.myvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyDerivationTest {
    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun `Argon2id matches the RFC 9106 test vector`() {
        val tag = Argon2idKdf.hash(
            password = ByteArray(32) { 0x01 },
            salt = ByteArray(16) { 0x02 },
            params = Argon2Params(memoryKiB = 32, iterations = 3, parallelism = 4),
            lengthBytes = 32,
            secret = ByteArray(8) { 0x03 },
            additional = ByteArray(12) { 0x04 },
        )
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", tag.toHex())
    }

    @Test
    fun `derivation is deterministic for the same inputs`() {
        val a = Argon2idKdf.deriveKey("correct horse".toCharArray(), salt, FAST_PARAMS)
        val b = Argon2idKdf.deriveKey("correct horse".toCharArray(), salt, FAST_PARAMS)
        assertTrue(a.contentEquals(b))
        assertEquals(32, a.size)
    }

    @Test
    fun `passphrase, salt and every cost parameter change the key`() {
        val base = Argon2idKdf.deriveKey("correct horse".toCharArray(), salt, FAST_PARAMS)
        val variants = listOf(
            Argon2idKdf.deriveKey("correct horsf".toCharArray(), salt, FAST_PARAMS),
            Argon2idKdf.deriveKey("correct horse".toCharArray(), ByteArray(16) { 9 }, FAST_PARAMS),
            Argon2idKdf.deriveKey("correct horse".toCharArray(), salt, FAST_PARAMS.copy(memoryKiB = 512)),
            Argon2idKdf.deriveKey("correct horse".toCharArray(), salt, FAST_PARAMS.copy(iterations = 2)),
            Argon2idKdf.deriveKey("correct horse".toCharArray(), salt, FAST_PARAMS.copy(parallelism = 2)),
        )
        variants.forEach { assertFalse(base.contentEquals(it)) }
    }

    @Test
    fun `equivalent Unicode forms of a passphrase derive the same key`() {
        val composed = "café au lait".toCharArray()      // é as one code point
        val decomposed = "café au lait".toCharArray()   // e + combining acute accent
        assertTrue(
            Argon2idKdf.deriveKey(composed, salt, FAST_PARAMS)
                .contentEquals(Argon2idKdf.deriveKey(decomposed, salt, FAST_PARAMS)),
        )
    }

    @Test
    fun `deriving does not modify the caller's passphrase`() {
        val passphrase = "correct horse".toCharArray()
        Argon2idKdf.deriveKey(passphrase, salt, FAST_PARAMS)
        assertEquals("correct horse", String(passphrase))
    }

    @Test
    fun `empty passphrases and short salts are refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            Argon2idKdf.deriveKey(CharArray(0), salt, FAST_PARAMS)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Argon2idKdf.deriveKey("correct horse".toCharArray(), ByteArray(8), FAST_PARAMS)
        }
    }

    @Test
    fun `out-of-range cost parameters are refused`() {
        assertThrows(IllegalArgumentException::class.java) { Argon2Params(4, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { Argon2Params(1024, 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { Argon2Params(1024, 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { Argon2Params(Argon2Params.MAX_MEMORY_KIB + 1, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { Argon2Params(1024, Argon2Params.MAX_ITERATIONS + 1, 1) }
    }

    @Test
    fun `production defaults meet the RFC 9106 memory-constrained profile`() {
        assertTrue(Argon2Params.DEFAULT.memoryKiB >= 64 * 1024)
        assertTrue(Argon2Params.DEFAULT.iterations >= 3)
    }

    @Test
    fun `HKDF-SHA256 matches RFC 5869 test case 1`() {
        val okm = Hkdf.derive(
            inputKey = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            lengthBytes = 42,
        )
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.toHex(),
        )
    }

    @Test
    fun `HKDF separates keys by purpose`() {
        val master = SecretBytes.wrap(ByteArray(32) { 7 })
        val items = Hkdf.deriveKey(master, "items".toByteArray())
        val attachments = Hkdf.deriveKey(master, "attachments".toByteArray())
        assertFalse(items.contentEquals(attachments))
        assertFalse(items.contentEquals(master))
        assertTrue(items.contentEquals(Hkdf.deriveKey(master, "items".toByteArray())))
    }
}
