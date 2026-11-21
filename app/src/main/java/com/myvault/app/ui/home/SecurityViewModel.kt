package com.myvault.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.SecuritySettings
import com.myvault.app.data.SecuritySettingsRepository
import com.myvault.app.security.BiometricAvailability
import com.myvault.app.security.BiometricVault
import com.myvault.app.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

data class SecurityUiState(
    val biometricEnrolled: Boolean = false,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.UNAVAILABLE,
    val autoLockSeconds: Int = SecuritySettings.DEFAULT_AUTO_LOCK_SECONDS,
    val message: String? = null,
)

/** Backs the screens shown while unlocked in this phase: biometric setup and vault status. */
@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val vaultManager: VaultManager,
    private val biometricVault: BiometricVault,
    private val settings: SecuritySettingsRepository,
) : ViewModel() {

    private val local = MutableStateFlow(SecurityUiState(biometricAvailability = biometricVault.availability()))

    val uiState: StateFlow<SecurityUiState> =
        combine(local, biometricVault.enrolled, settings.settings) { state, enrolled, current ->
            state.copy(biometricEnrolled = enrolled, autoLockSeconds = current.autoLockTimeoutSeconds)
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

    fun showMessage(message: String) {
        local.update { it.copy(message = message) }
    }

    fun disableBiometrics() {
        viewModelScope.launch { biometricVault.disable() }
    }

    fun setAutoLock(seconds: Int) {
        viewModelScope.launch { settings.setAutoLockTimeout(seconds) }
    }

    fun lock() = vaultManager.lock()
}
