package com.myvault.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.backup.BackupCoordinator
import com.myvault.app.backup.BackupHealth
import com.myvault.app.backup.BackupKeyManager
import com.myvault.app.backup.BackupStatus
import com.myvault.app.data.SecuritySettings
import com.myvault.app.data.SecuritySettingsRepository
import com.myvault.app.security.BiometricAvailability
import com.myvault.app.security.BiometricVault
import com.myvault.app.security.KeyRotator
import com.myvault.app.security.UnlockResult
import com.myvault.app.security.VaultManager
import com.myvault.app.security.VaultMetadataStore
import com.myvault.app.util.RecoveryKit
import com.myvault.crypto.Argon2Params
import com.myvault.crypto.PassphraseStrength
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.crypto.Cipher
import javax.inject.Inject

data class SecurityUiState(
    val biometricEnrolled: Boolean = false,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.UNAVAILABLE,
    val autoLockSeconds: Int = SecuritySettings.DEFAULT_AUTO_LOCK_SECONDS,
    val clipboardClearSeconds: Int = SecuritySettings.DEFAULT_CLIPBOARD_CLEAR_SECONDS,
    val reauthToReveal: Boolean = false,
    val backupConfigured: Boolean = false,
    val backupHealth: BackupHealth = BackupHealth.NOT_SET_UP,
    val lastBackupAt: Long? = null,
    val passphraseChangedAt: Long? = null,
    val keyGeneration: Int = 1,
    val message: String? = null,
    /** A passphrase-protected operation (change passphrase, rotate key) is running. */
    val working: Boolean = false,
    val dialogError: String? = null,
)

/** Security settings, biometric enrollment, passphrase change, key rotation and the recovery kit. */
@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val vaultManager: VaultManager,
    private val biometricVault: BiometricVault,
    private val settings: SecuritySettingsRepository,
    private val rotator: KeyRotator,
    private val store: VaultMetadataStore,
    private val backupKeys: BackupKeyManager,
    private val backupCoordinator: BackupCoordinator,
    private val kdfParams: Argon2Params,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(SecurityUiState(biometricAvailability = biometricVault.availability()))

    val uiState: StateFlow<SecurityUiState> = combine(
        local, store.observe(), settings.settings, backupCoordinator.history, backupKeys.configured,
    ) { state, metadata, current, history, backupConfigured ->
        state.copy(
            biometricEnrolled = metadata?.biometricSlot != null,
            autoLockSeconds = current.autoLockTimeoutSeconds,
            clipboardClearSeconds = current.clipboardClearSeconds,
            reauthToReveal = current.reauthToReveal,
            backupConfigured = backupConfigured,
            backupHealth = BackupHealth.evaluate(backupConfigured, history, current.vaultChangedAt, clock.millis()),
            lastBackupAt = history.filter { it.status == BackupStatus.COMPLETED }.maxOfOrNull { it.createdAt },
            passphraseChangedAt = metadata?.passphraseChangedAtEpochMs,
            keyGeneration = metadata?.keyGeneration ?: 1,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, local.value)

    /** Biometrics can be added or removed in system settings while the app is in the background. */
    fun refreshAvailability() {
        local.update { it.copy(biometricAvailability = biometricVault.availability()) }
    }

    /** Returns a cipher to pass to the biometric prompt, or null if enrollment cannot start. */
    suspend fun beginBiometricEnrollment(): Cipher? = try {
        local.update { it.copy(message = null) }
        biometricVault.beginEnrollment()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        local.update { it.copy(message = "Biometric unlock could not be set up on this device.") }
        null
    }

    suspend fun finishBiometricEnrollment(cipher: Cipher): Boolean = try {
        biometricVault.finishEnrollment(cipher)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        biometricVault.disable()
        local.update { it.copy(message = "Biometric unlock could not be turned on. Your passphrase still works.") }
        false
    }

    fun showMessage(message: String?) {
        local.update { it.copy(message = message) }
    }

    fun clearDialogError() {
        local.update { it.copy(dialogError = null) }
    }

    fun disableBiometrics() {
        viewModelScope.launch { biometricVault.disable() }
    }

    fun setAutoLock(seconds: Int) {
        viewModelScope.launch { settings.setAutoLockTimeout(seconds) }
    }

    fun setClipboardClear(seconds: Int) {
        viewModelScope.launch { settings.setClipboardClear(seconds) }
    }

    fun setReauthToReveal(enabled: Boolean) {
        viewModelScope.launch { settings.setReauthToReveal(enabled) }
    }

    fun lock() = vaultManager.lock()

    /** Re-wraps the vault key under a new master passphrase. */
    fun changePassphrase(current: String, replacement: String, onDone: () -> Unit) {
        if (!PassphraseStrength.assess(replacement).acceptable) {
            local.update { it.copy(dialogError = PassphraseStrength.assess(replacement).hint ?: "Choose a stronger passphrase.") }
            return
        }
        protectedOperation(onDone, "Master passphrase changed") {
            val old = current.toCharArray()
            val new = replacement.toCharArray()
            try {
                vaultManager.changePassphrase(old, new)
            } finally {
                old.fill('\u0000')
                new.fill('\u0000')
            }
        }
    }

    /** Replaces the vault master key and re-encrypts every record. Turns biometric unlock off. */
    fun rotateKey(passphrase: String, onDone: () -> Unit) {
        protectedOperation(onDone, "Encryption key replaced. Turn biometric unlock back on if you use it.") {
            val chars = passphrase.toCharArray()
            try {
                rotator.rotate(chars).also { if (it == UnlockResult.SUCCESS) biometricVault.disable() }
            } finally {
                chars.fill('\u0000')
            }
        }
    }

    private fun protectedOperation(onDone: () -> Unit, success: String, block: suspend () -> UnlockResult) {
        if (local.value.working) return
        local.update { it.copy(working = true, dialogError = null) }
        viewModelScope.launch {
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            when (result) {
                UnlockResult.SUCCESS -> {
                    local.update { it.copy(working = false, message = success) }
                    onDone()
                }
                UnlockResult.WRONG_PASSPHRASE -> local.update { it.copy(working = false, dialogError = "Incorrect master passphrase.") }
                else -> local.update { it.copy(working = false, dialogError = "That could not be completed. Nothing was changed.") }
            }
        }
    }

    /** The recovery kit as text, or null if there is no vault to describe. */
    suspend fun recoveryKitText(): String? {
        val metadata = store.load() ?: return null
        val last = backupCoordinator.history.first().filter { it.status == BackupStatus.COMPLETED }.maxByOrNull { it.createdAt }
        return RecoveryKit.render(metadata, kdfParams, backupKeys.isConfigured(), last?.createdAt, last?.fileName, clock.millis())
    }
}
