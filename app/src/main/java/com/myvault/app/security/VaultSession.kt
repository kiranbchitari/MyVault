package com.myvault.app.security

import com.myvault.crypto.Hkdf
import com.myvault.crypto.SecretBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.EnumMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

class VaultLockedException : IllegalStateException("Vault is locked")

/** Independent keys derived from the vault master key, one per use. */
enum class KeyPurpose(val info: String) {
    ITEMS("MyVault/key/items/v1"),
    ATTACHMENTS("MyVault/key/attachments/v1"),
    BACKUP_KEY_WRAP("MyVault/key/backup-key-wrap/v1"),
}

/**
 * The only place the vault master key lives while the vault is unlocked. Locking wipes the key
 * and every key derived from it, after which nothing in the process can decrypt vault data until
 * the user authenticates again.
 */
@Singleton
class VaultSession @Inject constructor() {
    private val monitor = Any()
    private var masterKey: SecretBytes? = null
    private var generation = VaultMetadata.INITIAL_KEY_GENERATION
    private val derived = EnumMap<KeyPurpose, SecretBytes>(KeyPurpose::class.java)
    private val unlocked = MutableStateFlow(false)

    val isUnlocked: StateFlow<Boolean> = unlocked.asStateFlow()

    private val lockListeners = CopyOnWriteArrayList<() -> Unit>()

    /**
     * Registers a callback that runs synchronously inside [lock], before it returns. Holders of
     * decrypted data use this to drop it at the same instant the keys are wiped, rather than a
     * moment later when a coroutine gets scheduled.
     */
    fun onLock(listener: () -> Unit) {
        lockListeners += listener
    }

    private val unlockListeners = CopyOnWriteArrayList<() -> Unit>()

    /**
     * Registers a callback that runs every time a key is installed. A state flow is not enough
     * for this: a lock immediately followed by an unlock is conflated away by a slow collector.
     */
    fun onUnlock(listener: () -> Unit) {
        unlockListeners += listener
    }

    /** Takes ownership of [key]. */
    fun open(key: SecretBytes, keyGeneration: Int = VaultMetadata.INITIAL_KEY_GENERATION) {
        synchronized(monitor) {
            wipe()
            masterKey = key
            generation = keyGeneration
            unlocked.value = true
        }
        unlockListeners.forEach { it() }
    }

    fun lock() {
        synchronized(monitor) {
            wipe()
            unlocked.value = false
        }
        lockListeners.forEach { it() }
    }

    /** Runs [block] with the master key, which must not be retained beyond the call. */
    fun <T> withMasterKey(block: (SecretBytes) -> T): T = synchronized(monitor) {
        block(masterKey ?: throw VaultLockedException())
    }

    /**
     * Runs [block] with the key for [purpose] and the generation of the master key it comes
     * from. The key must not be retained beyond the call.
     */
    fun <T> withKey(purpose: KeyPurpose, block: (key: SecretBytes, keyGeneration: Int) -> T): T = synchronized(monitor) {
        val master = masterKey ?: throw VaultLockedException()
        val key = derived.getOrPut(purpose) { Hkdf.deriveKey(master, purpose.info.toByteArray(Charsets.US_ASCII)) }
        block(key, generation)
    }

    private fun wipe() {
        masterKey?.close()
        masterKey = null
        derived.values.forEach(SecretBytes::close)
        derived.clear()
    }
}
