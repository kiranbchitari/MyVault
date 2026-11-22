package com.myvault.app.security

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVaultMetadataStore : VaultMetadataStore {
    val current = MutableStateFlow<VaultMetadata?>(null)
    var failNextCreate = false

    override fun observe(): Flow<VaultMetadata?> = current

    override suspend fun load(): VaultMetadata? = current.value

    override suspend fun create(metadata: VaultMetadata) {
        if (failNextCreate) {
            failNextCreate = false
            throw IllegalStateException("disk full")
        }
        check(current.value == null) { "vault exists" }
        current.value = metadata
    }

    override suspend fun updatePassphraseSlot(slot: ByteArray, changedAtEpochMs: Long) {
        current.value = current.value!!.copy(passphraseSlot = slot, passphraseChangedAtEpochMs = changedAtEpochMs)
    }

    override suspend fun updateBiometricSlot(slot: ByteArray?) {
        current.value = current.value!!.copy(biometricSlot = slot, clearBiometric = slot == null)
    }

    override suspend fun updateBackupKey(keyId: ByteArray, passphraseSlot: ByteArray, wrappedKey: ByteArray) {
        current.value = current.value!!.copy(backupKeyId = keyId, backupPassphraseSlot = passphraseSlot, backupKeyWrapped = wrappedKey)
    }

    override suspend fun applyRotation(passphraseSlot: ByteArray, keyGeneration: Int, backupKeyWrapped: ByteArray?, changedAtEpochMs: Long) {
        if (failNextRotation) {
            failNextRotation = false
            throw IllegalStateException("disk full")
        }
        current.value = current.value!!.copy(
            passphraseSlot = passphraseSlot,
            keyGeneration = keyGeneration,
            backupKeyWrapped = backupKeyWrapped,
            clearBackupWrap = backupKeyWrapped == null,
            clearBiometric = true,
            passphraseChangedAtEpochMs = changedAtEpochMs,
        )
    }

    var failNextRotation = false

    fun replace(transform: (VaultMetadata) -> VaultMetadata) {
        current.value = transform(current.value!!)
    }
}

fun VaultMetadata.copy(
    formatVersion: Int = this.formatVersion,
    keyGeneration: Int = this.keyGeneration,
    passphraseSlot: ByteArray = this.passphraseSlot,
    biometricSlot: ByteArray? = this.biometricSlot,
    clearBiometric: Boolean = false,
    passphraseChangedAtEpochMs: Long = this.passphraseChangedAtEpochMs,
    backupKeyId: ByteArray? = this.backupKeyId,
    backupPassphraseSlot: ByteArray? = this.backupPassphraseSlot,
    backupKeyWrapped: ByteArray? = this.backupKeyWrapped,
    clearBackupWrap: Boolean = false,
) = VaultMetadata(
    vaultId = vaultId,
    formatVersion = formatVersion,
    keyGeneration = keyGeneration,
    passphraseSlot = passphraseSlot,
    biometricSlot = if (clearBiometric) null else biometricSlot,
    createdAtEpochMs = createdAtEpochMs,
    passphraseChangedAtEpochMs = passphraseChangedAtEpochMs,
    backupKeyId = backupKeyId,
    backupPassphraseSlot = backupPassphraseSlot,
    backupKeyWrapped = if (clearBackupWrap) null else backupKeyWrapped,
)

fun ByteArray.contains(needle: ByteArray): Boolean {
    if (needle.isEmpty() || needle.size > size) return false
    outer@ for (i in 0..size - needle.size) {
        for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
        return true
    }
    return false
}
