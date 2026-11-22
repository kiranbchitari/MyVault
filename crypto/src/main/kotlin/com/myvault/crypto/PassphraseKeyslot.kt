package com.myvault.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom

/**
 * Wraps the vault master key (VMK) under a key derived from the master passphrase.
 *
 * ```
 * offset  size  field
 * 0       1     slot version (1)
 * 1       1     KDF (1 = Argon2id v1.3 over the NFKC-normalized, UTF-8 encoded passphrase)
 * 2       4     Argon2 memory in KiB, big-endian
 * 6       4     Argon2 iterations
 * 10      4     Argon2 parallelism
 * 14      1     salt length s
 * 15      s     salt
 * 15+s    ..    VaultCipher envelope of the VMK, sealed with the derived key
 * ```
 *
 * The envelope's associated data is `label || vaultId || bytes[0 .. 15+s)`: every header field
 * is authenticated, and a slot cannot be transplanted into another vault. Only this blob is
 * stored; the passphrase and the derived key exist in memory only while a slot is being
 * created or opened.
 */
class PassphraseKeyslot(
    private val random: SecureRandom,
    private val cipher: VaultCipher,
    /** Domain-separation label: a slot made for one purpose cannot be opened as another. */
    label: String = VAULT_LABEL,
) {
    private val contextLabel = label.toByteArray(Charsets.US_ASCII)

    fun create(
        passphrase: CharArray,
        masterKey: SecretBytes,
        vaultId: ByteArray,
        params: Argon2Params = Argon2Params.DEFAULT,
    ): ByteArray {
        require(masterKey.size == VaultCipher.KEY_BYTES) { "master key must be 256 bits" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(FIXED_HEADER_BYTES + salt.size)
            .put(SLOT_VERSION)
            .put(KDF_ARGON2ID_V13_NFKC)
            .putInt(params.memoryKiB)
            .putInt(params.iterations)
            .putInt(params.parallelism)
            .put(salt.size.toByte())
            .put(salt)
            .array()
        return Argon2idKdf.deriveKey(passphrase, salt, params).use { kek ->
            val sealed = masterKey.access { cipher.seal(kek, KEK_GENERATION, it, context(vaultId, header)) }
            header + sealed
        }
    }

    /**
     * Recovers the VMK. The caller owns the returned secret and must close it.
     *
     * @throws AuthenticationFailedException wrong passphrase, or the slot was modified
     * @throws UnsupportedFormatException slot written by a newer format
     * @throws MalformedDataException slot is truncated or carries out-of-range parameters
     */
    fun open(passphrase: CharArray, slot: ByteArray, vaultId: ByteArray): SecretBytes {
        val parsed = parse(slot)
        return Argon2idKdf.deriveKey(passphrase, parsed.salt, parsed.params).use { kek ->
            val masterKey = cipher.open(kek, parsed.sealed, context(vaultId, parsed.header))
            if (masterKey.size != VaultCipher.KEY_BYTES) {
                masterKey.fill(0)
                throw MalformedDataException("keyslot payload")
            }
            SecretBytes.wrap(masterKey)
        }
    }

    /** The KDF parameters a stored slot was created with. */
    fun paramsOf(slot: ByteArray): Argon2Params = parse(slot).params

    private class Parsed(val params: Argon2Params, val salt: ByteArray, val header: ByteArray, val sealed: ByteArray)

    private fun parse(slot: ByteArray): Parsed {
        if (slot.size < FIXED_HEADER_BYTES) throw MalformedDataException("keyslot")
        if (slot[0] != SLOT_VERSION) throw UnsupportedFormatException("keyslot version")
        if (slot[1] != KDF_ARGON2ID_V13_NFKC) throw UnsupportedFormatException("key derivation function")
        val buffer = ByteBuffer.wrap(slot, 2, 12)
        val params = try {
            Argon2Params(memoryKiB = buffer.int, iterations = buffer.int, parallelism = buffer.int)
        } catch (_: IllegalArgumentException) {
            throw MalformedDataException("keyslot parameters")
        }
        val saltLength = slot[14].toInt() and 0xFF
        val headerLength = FIXED_HEADER_BYTES + saltLength
        if (saltLength < Argon2idKdf.MIN_SALT_BYTES || slot.size < headerLength + VaultCipher.MIN_SEALED_BYTES) {
            throw MalformedDataException("keyslot")
        }
        return Parsed(
            params = params,
            salt = slot.copyOfRange(FIXED_HEADER_BYTES, headerLength),
            header = slot.copyOfRange(0, headerLength),
            sealed = slot.copyOfRange(headerLength, slot.size),
        )
    }

    private fun context(vaultId: ByteArray, header: ByteArray): ByteArray = contextLabel + vaultId + header

    companion object {
        const val SLOT_VERSION: Byte = 1
        const val KDF_ARGON2ID_V13_NFKC: Byte = 1
        const val SALT_BYTES = 32
        private const val FIXED_HEADER_BYTES = 15
        private const val KEK_GENERATION = 0
        const val VAULT_LABEL = "MyVault/keyslot/passphrase/v1"
        const val BACKUP_LABEL = "MyVault/keyslot/backup/v1"
    }
}
