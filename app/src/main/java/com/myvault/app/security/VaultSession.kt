package com.myvault.app.security

import com.myvault.crypto.SecretBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

class VaultLockedException : IllegalStateException("Vault is locked")

/**
 * The only place the vault master key lives while the vault is unlocked. Locking wipes the key,
 * after which nothing in the process can decrypt vault data until the user authenticates again.
 */
@Singleton
class VaultSession @Inject constructor() {
    private val monitor = Any()
    private var masterKey: SecretBytes? = null
    private val unlocked = MutableStateFlow(false)

    val isUnlocked: StateFlow<Boolean> = unlocked.asStateFlow()

    /** Takes ownership of [key]. */
    fun open(key: SecretBytes) {
        synchronized(monitor) {
            masterKey?.close()
            masterKey = key
            unlocked.value = true
        }
    }

    fun lock() {
        synchronized(monitor) {
            masterKey?.close()
            masterKey = null
            unlocked.value = false
        }
    }

    /** Runs [block] with the master key, which must not be retained beyond the call. */
    fun <T> withMasterKey(block: (SecretBytes) -> T): T = synchronized(monitor) {
        block(masterKey ?: throw VaultLockedException())
    }
}
