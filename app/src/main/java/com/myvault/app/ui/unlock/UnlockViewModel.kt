package com.myvault.app.ui.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.security.BiometricUnlockStart
import com.myvault.app.security.BiometricVault
import com.myvault.app.security.UnlockResult
import com.myvault.app.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

data class UnlockUiState(
    val working: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    /** Null until the stored state has been read. */
    val biometricEnrolled: Boolean? = null,
)

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val vaultManager: VaultManager,
    private val biometricVault: BiometricVault,
) : ViewModel() {

    private val progress = MutableStateFlow(UnlockUiState())

    val uiState: StateFlow<UnlockUiState> =
        combine(progress, biometricVault.enrolled) { state, enrolled -> state.copy(biometricEnrolled = enrolled) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UnlockUiState())

    fun unlock(passphrase: String) {
        if (progress.value.working || passphrase.isEmpty()) return
        progress.update { it.copy(working = true, error = null, notice = null) }
        viewModelScope.launch {
            val chars = passphrase.toCharArray()
            val result = try {
                vaultManager.unlock(chars)
            } finally {
                chars.fill('\u0000')
            }
            val error = when (result) {
                UnlockResult.SUCCESS -> null
                UnlockResult.WRONG_PASSPHRASE -> "Incorrect passphrase. Please try again."
                UnlockResult.NO_VAULT -> "No vault was found on this device."
                UnlockResult.UNSUPPORTED_VAULT -> "This vault was created by a newer version of MyVault. Please update the app."
                UnlockResult.DAMAGED_VAULT -> "The vault's key data is damaged. Restore from a backup to regain access."
                UnlockResult.OUT_OF_MEMORY -> "Not enough free memory to unlock. Close other apps and try again."
            }
            progress.update { it.copy(working = false, error = error) }
        }
    }

    /** Returns a cipher to pass to the biometric prompt, or null if biometric unlock cannot start. */
    suspend fun beginBiometricUnlock(): Cipher? = when (val start = biometricVault.beginUnlock()) {
        is BiometricUnlockStart.Ready -> start.cipher
        BiometricUnlockStart.NotEnrolled -> null
        BiometricUnlockStart.Invalidated -> {
            progress.update {
                it.copy(notice = "Biometric unlock was turned off because the biometrics on this device changed. Use your passphrase, then turn it back on.")
            }
            null
        }
    }

    fun finishBiometricUnlock(cipher: Cipher) {
        viewModelScope.launch {
            if (!biometricVault.finishUnlock(cipher)) {
                progress.update { it.copy(error = "Biometric unlock failed. Enter your passphrase instead.") }
            }
        }
    }

    fun biometricFailed(message: String) {
        progress.update { it.copy(error = message) }
    }
}
