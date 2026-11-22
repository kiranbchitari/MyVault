package com.myvault.app.security

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import com.myvault.app.di.IoDispatcher
import com.myvault.crypto.SecretBytes
import com.myvault.crypto.VaultCipher
import com.myvault.crypto.VaultCryptoException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

enum class BiometricAvailability { AVAILABLE, NONE_ENROLLED, NO_HARDWARE, UNAVAILABLE }

sealed interface BiometricUnlockStart {
    /** A cipher that becomes usable once BiometricPrompt has authenticated it. */
    class Ready(val cipher: Cipher) : BiometricUnlockStart
    data object NotEnrolled : BiometricUnlockStart

    /** Biometrics changed (or the key was lost); biometric unlock has been switched off. */
    data object Invalidated : BiometricUnlockStart
}

/**
 * Biometric unlock. A second copy of the vault master key is stored encrypted under a key that
 * lives in the Android Keystore and only works after a strong biometric authentication. This is a
 * convenience layered on top of the passphrase: it can always be discarded, and the passphrase
 * slot never depends on it.
 */
@Singleton
class BiometricVault @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: VaultMetadataStore,
    private val session: VaultSession,
    private val keystore: BiometricKeystore,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    val enrolled: Flow<Boolean> = store.observe().map { it?.biometricSlot != null }.distinctUntilChanged()

    fun availability(): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            else -> BiometricAvailability.UNAVAILABLE
        }

    /** Creates a fresh Keystore key and returns a cipher to authenticate with BiometricPrompt. */
    suspend fun beginEnrollment(): Cipher = withContext(io) {
        keystore.createKey()
        keystore.encryptCipher()
    }

    /** Wraps the master key with the now-authenticated [cipher] and stores the slot. */
    suspend fun finishEnrollment(cipher: Cipher) = withContext(io) {
        val metadata = checkNotNull(store.load()) { "No vault" }
        val ciphertext = session.withMasterKey { masterKey ->
            masterKey.access {
                cipher.updateAAD(BiometricSlot.associatedData(metadata.vaultId))
                cipher.doFinal(it)
            }
        }
        store.updateBiometricSlot(BiometricSlot(cipher.iv, ciphertext).encode())
    }

    suspend fun beginUnlock(): BiometricUnlockStart = withContext(io) {
        val stored = store.load()?.biometricSlot ?: return@withContext BiometricUnlockStart.NotEnrolled
        try {
            BiometricUnlockStart.Ready(keystore.decryptCipher(BiometricSlot.decode(stored).iv))
        } catch (_: KeyPermanentlyInvalidatedException) {
            disable()
            BiometricUnlockStart.Invalidated
        } catch (_: GeneralSecurityException) {
            disable()
            BiometricUnlockStart.Invalidated
        } catch (_: IllegalStateException) {
            disable()
            BiometricUnlockStart.Invalidated
        } catch (_: VaultCryptoException) {
            disable()
            BiometricUnlockStart.Invalidated
        }
    }

    /** Unwraps the master key with the now-authenticated [cipher]. Returns false on failure. */
    suspend fun finishUnlock(cipher: Cipher): Boolean = withContext(io) {
        val metadata = store.load() ?: return@withContext false
        val stored = metadata.biometricSlot ?: return@withContext false
        try {
            cipher.updateAAD(BiometricSlot.associatedData(metadata.vaultId))
            val masterKey = cipher.doFinal(BiometricSlot.decode(stored).ciphertext)
            if (masterKey.size != VaultCipher.KEY_BYTES) {
                masterKey.fill(0)
                return@withContext false
            }
            session.open(SecretBytes.wrap(masterKey), metadata.keyGeneration)
            true
        } catch (_: GeneralSecurityException) {
            false
        } catch (_: VaultCryptoException) {
            false
        }
    }

    /**
     * Confirms the user's identity with an authenticated [cipher] without replacing the session:
     * the key it unwraps must be the one already in use.
     */
    suspend fun verify(cipher: Cipher): Boolean = withContext(io) {
        val metadata = store.load() ?: return@withContext false
        val stored = metadata.biometricSlot ?: return@withContext false
        try {
            cipher.updateAAD(BiometricSlot.associatedData(metadata.vaultId))
            SecretBytes.wrap(cipher.doFinal(BiometricSlot.decode(stored).ciphertext)).use { unwrapped ->
                session.withMasterKey { it.contentEquals(unwrapped) }
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun disable() = withContext(io) {
        store.updateBiometricSlot(null)
        runCatching { keystore.deleteKey() }
        Unit
    }
}
