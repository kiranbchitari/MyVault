package com.myvault.app.security

import com.myvault.app.di.KdfDispatcher
import com.myvault.crypto.Argon2Params
import com.myvault.crypto.AuthenticationFailedException
import com.myvault.crypto.MalformedDataException
import com.myvault.crypto.PassphraseKeyslot
import com.myvault.crypto.SecretBytes
import com.myvault.crypto.UnsupportedFormatException
import com.myvault.crypto.VaultCipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

enum class VaultState { NO_VAULT, LOCKED, UNLOCKED }

enum class UnlockResult {
    SUCCESS,

    /** Wrong passphrase, or the stored keyslot was modified; the two are indistinguishable. */
    WRONG_PASSPHRASE,
    NO_VAULT,

    /** The vault was written by a newer version of the app. */
    UNSUPPORTED_VAULT,
    DAMAGED_VAULT,
    OUT_OF_MEMORY,
}

class VaultAlreadyExistsException : IllegalStateException("A vault already exists")

/**
 * Creates the vault and moves it between locked and unlocked.
 *
 * Callers own the passphrase arrays they pass in and should wipe them once the call returns.
 */
@Singleton
class VaultManager @Inject constructor(
    private val store: VaultMetadataStore,
    private val session: VaultSession,
    private val keyslot: PassphraseKeyslot,
    private val random: SecureRandom,
    private val kdfParams: Argon2Params,
    private val clock: Clock,
    @param:KdfDispatcher private val kdfDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()

    val state: Flow<VaultState> =
        combine(store.observe().map { it != null }.distinctUntilChanged(), session.isUnlocked) { exists, unlocked ->
            when {
                !exists -> VaultState.NO_VAULT
                unlocked -> VaultState.UNLOCKED
                else -> VaultState.LOCKED
            }
        }.distinctUntilChanged()

    /**
     * Generates a new vault master key, wraps it under [passphrase] and leaves the vault unlocked.
     *
     * The new keyslot is opened again with the same passphrase before anything is persisted, so
     * a vault that cannot be unlocked is never written.
     */
    suspend fun createVault(passphrase: CharArray) = withContext(kdfDispatcher) {
        mutex.withLock {
            if (store.load() != null) throw VaultAlreadyExistsException()
            val vaultId = ByteArray(VAULT_ID_BYTES).also(random::nextBytes)
            val masterKey = SecretBytes.random(VaultCipher.KEY_BYTES, random)
            try {
                val slot = keyslot.create(passphrase, masterKey, vaultId, kdfParams)
                keyslot.open(passphrase, slot, vaultId).use { check(it.contentEquals(masterKey)) }
                val now = clock.millis()
                // Unlock first so observers go straight from NO_VAULT to UNLOCKED.
                session.open(masterKey.copy())
                store.create(
                    VaultMetadata(
                        vaultId = vaultId,
                        formatVersion = VaultMetadata.FORMAT_VERSION,
                        keyGeneration = VaultMetadata.INITIAL_KEY_GENERATION,
                        passphraseSlot = slot,
                        biometricSlot = null,
                        createdAtEpochMs = now,
                        passphraseChangedAtEpochMs = now,
                    ),
                )
            } catch (t: Throwable) {
                session.lock()
                throw t
            } finally {
                masterKey.close()
            }
        }
    }

    suspend fun unlock(passphrase: CharArray): UnlockResult = withContext(kdfDispatcher) {
        mutex.withLock {
            val metadata = store.load() ?: return@withLock UnlockResult.NO_VAULT
            openSlot(passphrase, metadata) { session.open(it) }
        }
    }

    /**
     * Re-wraps the existing master key under [replacement]. Stored records and the biometric
     * slot are untouched because the master key itself does not change.
     */
    suspend fun changePassphrase(current: CharArray, replacement: CharArray): UnlockResult =
        withContext(kdfDispatcher) {
            mutex.withLock {
                val metadata = store.load() ?: return@withLock UnlockResult.NO_VAULT
                openSlot(current, metadata) { masterKey ->
                    try {
                        val slot = keyslot.create(replacement, masterKey, metadata.vaultId, kdfParams)
                        val reopened = keyslot.open(replacement, slot, metadata.vaultId)
                        try {
                            check(reopened.contentEquals(masterKey))
                        } finally {
                            reopened.close()
                        }
                        store.updatePassphraseSlot(slot, clock.millis())
                    } finally {
                        masterKey.close()
                    }
                }
            }
        }

    fun lock() = session.lock()

    private inline fun openSlot(
        passphrase: CharArray,
        metadata: VaultMetadata,
        onOpened: (SecretBytes) -> Unit,
    ): UnlockResult {
        if (metadata.formatVersion != VaultMetadata.FORMAT_VERSION) return UnlockResult.UNSUPPORTED_VAULT
        val masterKey = try {
            keyslot.open(passphrase, metadata.passphraseSlot, metadata.vaultId)
        } catch (_: AuthenticationFailedException) {
            return UnlockResult.WRONG_PASSPHRASE
        } catch (_: UnsupportedFormatException) {
            return UnlockResult.UNSUPPORTED_VAULT
        } catch (_: MalformedDataException) {
            return UnlockResult.DAMAGED_VAULT
        } catch (_: OutOfMemoryError) {
            return UnlockResult.OUT_OF_MEMORY
        }
        onOpened(masterKey)
        return UnlockResult.SUCCESS
    }

    private companion object {
        const val VAULT_ID_BYTES = 16
    }
}
