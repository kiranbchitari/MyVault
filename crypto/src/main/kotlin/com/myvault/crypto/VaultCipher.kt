package com.myvault.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.BadPaddingException

/**
 * Authenticated encryption with a versioned, self-describing envelope.
 *
 * ```
 * offset  size  field
 * 0       1     envelope version (1)
 * 1       1     algorithm (1 = AES-256-GCM, 96-bit nonce, 128-bit tag)
 * 2       4     key generation, big-endian (which generation of the key sealed this)
 * 6       12    nonce, fresh from SecureRandom for every seal
 * 18      n+16  ciphertext || tag
 * ```
 *
 * The GCM associated data is `header[0..6) || context`. The header is therefore authenticated,
 * and the caller-supplied context binds a ciphertext to its purpose and owner (for example a
 * record id), so a valid ciphertext cannot be moved into a different row or role.
 *
 * Nonces are random. With 96-bit random nonces the collision probability stays below 2^-32 for
 * up to 2^32 encryptions per key, far beyond what a personal vault produces.
 */
class VaultCipher(private val random: SecureRandom) {

    fun seal(key: SecretBytes, keyGeneration: Int, plaintext: ByteArray, context: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(ENVELOPE_VERSION)
            .put(ALGORITHM_AES_256_GCM)
            .putInt(keyGeneration)
            .array()
        val nonce = ByteArray(AesGcm.NONCE_BYTES).also(random::nextBytes)
        val ciphertext = key.access { AesGcm.encrypt(it, nonce, plaintext, header + context) }
        return header + nonce + ciphertext
    }

    /**
     * @throws AuthenticationFailedException wrong key, wrong context, or modified data
     * @throws UnsupportedFormatException unknown envelope version or algorithm
     * @throws MalformedDataException truncated envelope
     */
    fun open(key: SecretBytes, sealed: ByteArray, context: ByteArray): ByteArray {
        if (sealed.size < MIN_SEALED_BYTES) throw MalformedDataException("envelope")
        if (sealed[0] != ENVELOPE_VERSION) throw UnsupportedFormatException("envelope version")
        if (sealed[1] != ALGORITHM_AES_256_GCM) throw UnsupportedFormatException("encryption algorithm")
        val header = sealed.copyOfRange(0, HEADER_BYTES)
        val nonce = sealed.copyOfRange(HEADER_BYTES, HEADER_BYTES + AesGcm.NONCE_BYTES)
        val ciphertext = sealed.copyOfRange(HEADER_BYTES + AesGcm.NONCE_BYTES, sealed.size)
        return try {
            key.access { AesGcm.decrypt(it, nonce, ciphertext, header + context) }
        } catch (_: BadPaddingException) {
            throw AuthenticationFailedException()
        }
    }

    /** Reads the key generation from an envelope header without decrypting it. */
    fun keyGenerationOf(sealed: ByteArray): Int {
        if (sealed.size < MIN_SEALED_BYTES) throw MalformedDataException("envelope")
        return ByteBuffer.wrap(sealed, 2, 4).int
    }

    companion object {
        const val ENVELOPE_VERSION: Byte = 1
        const val ALGORITHM_AES_256_GCM: Byte = 1
        const val HEADER_BYTES = 6
        const val MIN_SEALED_BYTES = HEADER_BYTES + AesGcm.NONCE_BYTES + AesGcm.TAG_BYTES
        const val KEY_BYTES = AesGcm.KEY_BYTES
    }
}
