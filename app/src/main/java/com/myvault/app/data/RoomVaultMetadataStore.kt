package com.myvault.app.data

import com.myvault.app.data.db.VaultMetadataDao
import com.myvault.app.data.db.VaultMetadataEntity
import com.myvault.app.security.VaultMetadata
import com.myvault.app.security.VaultMetadataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomVaultMetadataStore @Inject constructor(private val dao: VaultMetadataDao) : VaultMetadataStore {

    override fun observe(): Flow<VaultMetadata?> = dao.observe().map { it?.toModel() }

    override suspend fun load(): VaultMetadata? = dao.get()?.toModel()

    override suspend fun create(metadata: VaultMetadata) = dao.insert(
        VaultMetadataEntity(
            vaultId = metadata.vaultId,
            formatVersion = metadata.formatVersion,
            keyGeneration = metadata.keyGeneration,
            passphraseSlot = metadata.passphraseSlot,
            biometricSlot = metadata.biometricSlot,
            createdAtEpochMs = metadata.createdAtEpochMs,
            passphraseChangedAtEpochMs = metadata.passphraseChangedAtEpochMs,
            backupKeyId = metadata.backupKeyId,
            backupPassphraseSlot = metadata.backupPassphraseSlot,
            backupKeyWrapped = metadata.backupKeyWrapped,
        ),
    )

    override suspend fun updatePassphraseSlot(slot: ByteArray, changedAtEpochMs: Long) {
        check(dao.updatePassphraseSlot(slot, changedAtEpochMs) == 1) { "No vault to update" }
    }

    override suspend fun updateBiometricSlot(slot: ByteArray?) {
        dao.updateBiometricSlot(slot)
    }

    override suspend fun updateBackupKey(keyId: ByteArray, passphraseSlot: ByteArray, wrappedKey: ByteArray) {
        check(dao.updateBackupKey(keyId, passphraseSlot, wrappedKey) == 1) { "No vault to update" }
    }

    override suspend fun applyRotation(passphraseSlot: ByteArray, keyGeneration: Int, backupKeyWrapped: ByteArray?, changedAtEpochMs: Long) {
        check(dao.applyRotation(passphraseSlot, keyGeneration, backupKeyWrapped, changedAtEpochMs) == 1) { "No vault to update" }
    }

    private fun VaultMetadataEntity.toModel() = VaultMetadata(
        vaultId = vaultId,
        formatVersion = formatVersion,
        keyGeneration = keyGeneration,
        passphraseSlot = passphraseSlot,
        biometricSlot = biometricSlot,
        createdAtEpochMs = createdAtEpochMs,
        passphraseChangedAtEpochMs = passphraseChangedAtEpochMs,
        backupKeyId = backupKeyId,
        backupPassphraseSlot = backupPassphraseSlot,
        backupKeyWrapped = backupKeyWrapped,
    )
}
