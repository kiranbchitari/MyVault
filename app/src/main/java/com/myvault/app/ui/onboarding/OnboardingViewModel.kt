package com.myvault.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.SecuritySettings
import com.myvault.app.data.SecuritySettingsRepository
import com.myvault.app.security.VaultAlreadyExistsException
import com.myvault.app.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val creating: Boolean = false,
    val error: String? = null,
    val autoLockSeconds: Int = SecuritySettings.DEFAULT_AUTO_LOCK_SECONDS,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val vaultManager: VaultManager,
    private val settings: SecuritySettingsRepository,
) : ViewModel() {

    /** The passphrase being set up. Held only in memory, and wiped once it is no longer needed. */
    private var candidate: CharArray? = null

    private val progress = MutableStateFlow(OnboardingUiState())

    val uiState: StateFlow<OnboardingUiState> =
        combine(progress, settings.settings) { state, current -> state.copy(autoLockSeconds = current.autoLockTimeoutSeconds) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingUiState())

    fun setCandidate(passphrase: String) {
        clearCandidate()
        candidate = passphrase.toCharArray()
    }

    fun matchesCandidate(passphrase: String): Boolean = candidate?.contentEquals(passphrase.toCharArray()) == true

    fun clearCandidate() {
        candidate?.fill('\u0000')
        candidate = null
    }

    fun setAutoLock(seconds: Int) {
        viewModelScope.launch { settings.setAutoLockTimeout(seconds) }
    }

    fun createVault() {
        val passphrase = candidate
        if (passphrase == null) {
            progress.update { it.copy(error = "Please go back and enter your passphrase again.") }
            return
        }
        if (progress.value.creating) return
        progress.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            val error = try {
                vaultManager.createVault(passphrase)
                clearCandidate()
                null
            } catch (_: VaultAlreadyExistsException) {
                "A vault already exists on this device."
            } catch (_: OutOfMemoryError) {
                "Not enough free memory to secure the vault. Close other apps and try again."
            } catch (_: Exception) {
                "The vault could not be created. Nothing was saved; please try again."
            }
            progress.update { it.copy(creating = false, error = error) }
        }
    }

    override fun onCleared() = clearCandidate()
}
