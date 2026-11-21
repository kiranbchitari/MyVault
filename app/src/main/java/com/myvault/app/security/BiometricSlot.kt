package com.myvault.app.security

import com.myvault.crypto.MalformedDataException
import com.myvault.crypto.UnsupportedFormatException

/**
 * Stored form of the master key wrapped by the biometric-bound Android Keystore key.
 *
 * ```
 * 0      1    slot version (1)
 * 1      1    IV length n
 * 2      n    IV chosen by the Keystore
 * 2+n    ..   AES-256-GCM ciphertext || tag
 * ```
 */
class BiometricSlot(val iv: ByteArray, val ciphertext: ByteArray) {

    fun encode(): ByteArray = byteArrayOf(VERSION, iv.size.toByte()) + iv + ciphertext

    companion object {
        const val VERSION: Byte = 1
        private const val MIN_CIPHERTEXT_BYTES = 16
        private val LABEL = "MyVault/keyslot/biometric/v1".toByteArray(Charsets.US_ASCII)

        fun decode(bytes: ByteArray): BiometricSlot {
            if (bytes.size < 2) throw MalformedDataException("biometric slot")
            if (bytes[0] != VERSION) throw UnsupportedFormatException("biometric slot version")
            val ivLength = bytes[1].toInt() and 0xFF
            if (ivLength == 0 || bytes.size < 2 + ivLength + MIN_CIPHERTEXT_BYTES) {
                throw MalformedDataException("biometric slot")
            }
            return BiometricSlot(bytes.copyOfRange(2, 2 + ivLength), bytes.copyOfRange(2 + ivLength, bytes.size))
        }

        /** Associated data binding the slot to its vault and format version. */
        fun associatedData(vaultId: ByteArray): ByteArray = LABEL + vaultId + byteArrayOf(VERSION)
    }
}
