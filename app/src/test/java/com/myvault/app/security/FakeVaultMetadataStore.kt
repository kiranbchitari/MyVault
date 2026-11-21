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

    fun replace(transform: (VaultMetadata) -> VaultMetadata) {
        current.value = transform(current.value!!)
    }
}

fun VaultMetadata.copy(
    formatVersion: Int = this.formatVersion,
    passphraseSlot: ByteArray = this.passphraseSlot,
    biometricSlot: ByteArray? = this.biometricSlot,
    clearBiometric: Boolean = false,
    passphraseChangedAtEpochMs: Long = this.passphraseChangedAtEpochMs,
) = VaultMetadata(
    vaultId = vaultId,
    formatVersion = formatVersion,
    keyGeneration = keyGeneration,
    passphraseSlot = passphraseSlot,
    biometricSlot = if (clearBiometric) null else biometricSlot,
    createdAtEpochMs = createdAtEpochMs,
    passphraseChangedAtEpochMs = passphraseChangedAtEpochMs,
)

fun ByteArray.contains(needle: ByteArray): Boolean {
    if (needle.isEmpty() || needle.size > size) return false
    outer@ for (i in 0..size - needle.size) {
        for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
        return true
    }
    return false
}
