package com.myvault.app.security

import com.myvault.app.backup.BackupKeyManager
import com.myvault.app.di.KdfDispatcher
import com.myvault.app.vault.VaultRepository
import com.myvault.crypto.Argon2Params
import com.myvault.crypto.PassphraseKeyslot
import com.myvault.crypto.SecretBytes
import com.myvault.crypto.VaultCipher
import com.myvault.crypto.VaultCryptoException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replaces the vault master key.
 *
 * A new random key is generated, every stored record and attachment is re-encrypted under keys
 * derived from it, and the new key is wrapped under the passphrase, all in one database
 * transaction. If anything fails the transaction rolls back and the old key remains in force.
 *
 * The biometric slot is dropped, because it wraps the old key; biometric unlock has to be turned
 * on again afterwards.
 */
@Singleton
class KeyRotator @Inject constructor(
    private val store: VaultMetadataStore,
    private val session: VaultSession,
    private val repository: VaultRepository,
    private val backupKeys: BackupKeyManager,
    private val keyslot: PassphraseKeyslot,
    private val random: SecureRandom,
    private val kdfParams: Argon2Params,
    private val clock: Clock,
    @param:KdfDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /** Requires the master passphrase, both to confirm the user and to wrap the new key. */
    suspend fun rotate(passphrase: CharArray): UnlockResult = withContext(dispatcher) {
        val metadata = store.load() ?: return@withContext UnlockResult.NO_VAULT
        if (!session.isUnlocked.value) throw VaultLockedException()
        try {
            keyslot.open(passphrase, metadata.passphraseSlot, metadata.vaultId).close()
        } catch (_: VaultCryptoException) {
            return@withContext UnlockResult.WRONG_PASSPHRASE
        }

        val nextGeneration = metadata.keyGeneration + 1
        val newKey = SecretBytes.random(VaultCipher.KEY_BYTES, random)
        val next = VaultSession().apply { open(newKey.copy(), nextGeneration) }
        try {
            val slot = keyslot.create(passphrase, newKey, metadata.vaultId, kdfParams)
            val reopened = keyslot.open(passphrase, slot, metadata.vaultId)
            try {
                check(reopened.contentEquals(newKey))
            } finally {
                reopened.close()
            }
            repository.reencryptAll(next) {
                store.applyRotation(slot, nextGeneration, backupKeys.rewrapFor(next, metadata), clock.millis())
            }
            session.open(newKey.copy(), nextGeneration)
            UnlockResult.SUCCESS
        } finally {
            next.lock()
            newKey.close()
        }
    }
}
