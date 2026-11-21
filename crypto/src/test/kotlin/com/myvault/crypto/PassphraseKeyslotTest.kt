package com.myvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PassphraseKeyslotTest {
    private val random = SecureRandom()
    private val keyslot = PassphraseKeyslot(random, VaultCipher(random))
    private val vaultId = ByteArray(16).also(random::nextBytes)
    private val passphrase = "tidal-orchid-lantern-42".toCharArray()

    @Test
    fun `the correct passphrase recovers the master key`() {
        val vmk = SecretBytes.random(32, random)
        val slot = keyslot.create(passphrase, vmk, vaultId, FAST_PARAMS)
        assertTrue(keyslot.open(passphrase, slot, vaultId).contentEquals(vmk))
        assertEquals(FAST_PARAMS, keyslot.paramsOf(slot))
    }

    @Test
    fun `a wrong passphrase is rejected`() {
        val slot = keyslot.create(passphrase, SecretBytes.random(32, random), vaultId, FAST_PARAMS)
        for (wrong in listOf("tidal-orchid-lantern-43", "Tidal-orchid-lantern-42", "tidal-orchid-lantern-42 ", "x")) {
            assertThrows(wrong, AuthenticationFailedException::class.java) {
                keyslot.open(wrong.toCharArray(), slot, vaultId)
            }
        }
    }

    @Test
    fun `the slot contains neither the master key nor the passphrase`() {
        val raw = ByteArray(32).also(random::nextBytes)
        val slot = keyslot.create(passphrase, SecretBytes.wrap(raw.copyOf()), vaultId, FAST_PARAMS)
        assertEquals(-1, slot.indexOfSubArray(raw))
        assertEquals(-1, slot.indexOfSubArray(raw.copyOf(8)))
        assertEquals(-1, slot.indexOfSubArray(String(passphrase).toByteArray()))
        assertEquals(-1, slot.indexOfSubArray(String(passphrase).toByteArray(Charsets.UTF_16LE)))
    }

    @Test
    fun `each slot gets a fresh salt and nonce`() {
        val vmk = SecretBytes.random(32, random)
        val a = keyslot.create(passphrase, vmk, vaultId, FAST_PARAMS)
        val b = keyslot.create(passphrase, vmk, vaultId, FAST_PARAMS)
        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOfRange(15, 47).contentEquals(b.copyOfRange(15, 47)))
    }

    @Test
    fun `modifying any byte of the slot is rejected`() {
        val slot = keyslot.create(passphrase, SecretBytes.random(32, random), vaultId, FAST_PARAMS)
        for (i in slot.indices) {
            val tampered = slot.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            assertThrows("byte $i", VaultCryptoException::class.java) { keyslot.open(passphrase, tampered, vaultId) }
        }
    }

    @Test
    fun `weakening the stored cost parameters does not open the slot`() {
        val slot = keyslot.create(passphrase, SecretBytes.random(32, random), vaultId, Argon2Params(512, 2, 1))
        val downgraded = slot.copyOf().also { it[9] = 1 }   // iterations 2 -> 1
        assertThrows(AuthenticationFailedException::class.java) { keyslot.open(passphrase, downgraded, vaultId) }
    }

    @Test
    fun `a slot cannot be moved to another vault`() {
        val slot = keyslot.create(passphrase, SecretBytes.random(32, random), vaultId, FAST_PARAMS)
        val otherVault = ByteArray(16).also(random::nextBytes)
        assertThrows(AuthenticationFailedException::class.java) { keyslot.open(passphrase, slot, otherVault) }
    }

    @Test
    fun `truncated slots and unknown versions are rejected without deriving a key`() {
        val slot = keyslot.create(passphrase, SecretBytes.random(32, random), vaultId, FAST_PARAMS)
        assertThrows(MalformedDataException::class.java) { keyslot.open(passphrase, slot.copyOf(10), vaultId) }
        assertThrows(MalformedDataException::class.java) { keyslot.open(passphrase, slot.copyOf(60), vaultId) }
        assertThrows(UnsupportedFormatException::class.java) {
            keyslot.open(passphrase, slot.copyOf().also { it[0] = 2 }, vaultId)
        }
        assertThrows(UnsupportedFormatException::class.java) {
            keyslot.open(passphrase, slot.copyOf().also { it[1] = 2 }, vaultId)
        }
    }

    @Test
    fun `hostile cost parameters are rejected instead of exhausting memory`() {
        val slot = keyslot.create(passphrase, SecretBytes.random(32, random), vaultId, FAST_PARAMS)
        val huge = slot.copyOf().also { it[2] = 0x7F }   // memory ~ 2 TiB
        assertThrows(MalformedDataException::class.java) { keyslot.open(passphrase, huge, vaultId) }
    }

    @Test
    fun `a slot created with production parameters opens`() {
        val vmk = SecretBytes.random(32, random)
        val slot = keyslot.create(passphrase, vmk, vaultId)
        assertEquals(Argon2Params.DEFAULT, keyslot.paramsOf(slot))
        assertTrue(keyslot.open(passphrase, slot, vaultId).contentEquals(vmk))
    }
}
