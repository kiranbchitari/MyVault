package com.myvault.app.security

import kotlinx.coroutines.flow.Flow

/**
 * Everything the app persists about the vault's keys. All of it is safe to store in the clear:
 * the slots are ciphertext, and the rest is non-secret bookkeeping.
 */
class VaultMetadata(
    /** Random identifier bound into every keyslot so slots cannot be moved between vaults. */
    val vaultId: ByteArray,
    val formatVersion: Int,
    /** Generation of the vault master key; incremented by a future master-key rotation. */
    val keyGeneration: Int,
    /** Master key wrapped under the passphrase-derived key. See PassphraseKeyslot. */
    val passphraseSlot: ByteArray,
    /** Master key wrapped under a biometric-bound Android Keystore key, when enabled. */
    val biometricSlot: ByteArray?,
    val createdAtEpochMs: Long,
    val passphraseChangedAtEpochMs: Long,
    /** Identifier of the backup key, bound into the backup passphrase slot. */
    val backupKeyId: ByteArray? = null,
    /** Backup key wrapped under the backup passphrase; copied into every backup file. */
    val backupPassphraseSlot: ByteArray? = null,
    /** Backup key wrapped under a key derived from the vault master key. */
    val backupKeyWrapped: ByteArray? = null,
) {
    companion object {
        const val FORMAT_VERSION = 1
        const val INITIAL_KEY_GENERATION = 1
    }
}

interface VaultMetadataStore {
    fun observe(): Flow<VaultMetadata?>
    suspend fun load(): VaultMetadata?

    /** Fails if a vault already exists; an existing vault is never overwritten. */
    suspend fun create(metadata: VaultMetadata)
    suspend fun updatePassphraseSlot(slot: ByteArray, changedAtEpochMs: Long)
    suspend fun updateBiometricSlot(slot: ByteArray?)
    suspend fun updateBackupKey(keyId: ByteArray, passphraseSlot: ByteArray, wrappedKey: ByteArray)

    /** Installs the results of a master-key rotation; the biometric slot is dropped. */
    suspend fun applyRotation(passphraseSlot: ByteArray, keyGeneration: Int, backupKeyWrapped: ByteArray?, changedAtEpochMs: Long)
}
