package com.myvault.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters

/**
 * HKDF-SHA256 (RFC 5869). Used to derive independent purpose-specific keys from the vault
 * master key, so a key used for one purpose is never reused for another.
 */
object Hkdf {
    fun deriveKey(
        inputKey: SecretBytes,
        info: ByteArray,
        salt: ByteArray? = null,
        lengthBytes: Int = VaultCipher.KEY_BYTES,
    ): SecretBytes = inputKey.access { SecretBytes.wrap(derive(it, salt, info, lengthBytes)) }

    internal fun derive(inputKey: ByteArray, salt: ByteArray?, info: ByteArray, lengthBytes: Int): ByteArray {
        val out = ByteArray(lengthBytes)
        HKDFBytesGenerator(SHA256Digest()).apply {
            init(HKDFParameters(inputKey, salt, info))
        }.generateBytes(out, 0, lengthBytes)
        return out
    }
}
