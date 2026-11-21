package com.myvault.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myvault.crypto.Argon2Params
import com.myvault.crypto.AuthenticationFailedException
import com.myvault.crypto.PassphraseKeyslot
import com.myvault.crypto.SecretBytes
import com.myvault.crypto.VaultCipher
import com.myvault.crypto.VaultCryptoException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

/** The crypto core on the real Android runtime: Conscrypt's AES-GCM and Argon2id on ART. */
@RunWith(AndroidJUnit4::class)
class CryptoOnDeviceTest {
    private val random = SecureRandom()
    private val cipher = VaultCipher(random)

    @Test
    fun envelopeRoundTripsAndRejectsTampering() {
        val key = SecretBytes.random(32, random)
        val plaintext = "IFSC HDFC0001234 / account 50100123456789".toByteArray()
        val context = "item/1".toByteArray()
        val sealed = cipher.seal(key, 1, plaintext, context)

        assertArrayEquals(plaintext, cipher.open(key, sealed, context))
        for (i in sealed.indices) {
            val tampered = sealed.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            assertThrows("byte $i", VaultCryptoException::class.java) { cipher.open(key, tampered, context) }
        }
        assertThrows(AuthenticationFailedException::class.java) { cipher.open(key, sealed, "item/2".toByteArray()) }
        assertThrows(AuthenticationFailedException::class.java) {
            cipher.open(SecretBytes.random(32, random), sealed, context)
        }
    }

    @Test
    fun noncesAreUniqueOnDevice() {
        val key = SecretBytes.random(32, random)
        val nonces = HashSet<String>()
        repeat(5_000) {
            val sealed = cipher.seal(key, 1, ByteArray(8), ByteArray(0))
            nonces += sealed.copyOfRange(VaultCipher.HEADER_BYTES, VaultCipher.HEADER_BYTES + 12).joinToString("") { "%02x".format(it) }
        }
        assertEquals(5_000, nonces.size)
    }

    @Test
    fun keyslotWithProductionParametersWorksWithinAppMemoryLimits() {
        val keyslot = PassphraseKeyslot(random, cipher)
        val vaultId = ByteArray(16).also(random::nextBytes)
        val masterKey = SecretBytes.random(32, random)
        val passphrase = "tidal-orchid-lantern-42".toCharArray()

        val started = System.nanoTime()
        val slot = keyslot.create(passphrase, masterKey, vaultId)
        val createMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(Argon2Params.DEFAULT, keyslot.paramsOf(slot))
        assertTrue(keyslot.open(passphrase, slot, vaultId).contentEquals(masterKey))
        assertThrows(AuthenticationFailedException::class.java) {
            keyslot.open("tidal-orchid-lantern-43".toCharArray(), slot, vaultId)
        }
        // Generous ceiling: catches a pathological slowdown without being flaky on slow devices.
        assertTrue("Argon2id took $createMs ms", createMs < 20_000)
    }
}
