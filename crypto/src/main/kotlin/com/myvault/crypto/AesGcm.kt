package com.myvault.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM through the platform JCA provider (Conscrypt on Android, SunJCE on the host JVM).
 * Callers never choose nonces themselves; see [VaultCipher].
 */
internal object AesGcm {
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plaintext)

    /** @throws javax.crypto.BadPaddingException (AEADBadTagException) when authentication fails. */
    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray =
        cipher(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(ciphertext)

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher {
        require(key.size == KEY_BYTES) { "AES-256 requires a 32-byte key" }
        require(nonce.size == NONCE_BYTES) { "GCM requires a 12-byte nonce" }
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            updateAAD(aad)
        }
    }
}
