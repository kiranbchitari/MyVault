package com.myvault.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.security.VaultManager
import com.myvault.app.security.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(vaultManager: VaultManager) : ViewModel() {

    /** Null until the first read of the database completes. */
    val vaultState: StateFlow<VaultState?> = vaultManager.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val biometricOffer = MutableStateFlow(false)

    /** True between creating a vault and answering the one-time biometric offer. */
    val biometricOfferPending: StateFlow<Boolean> = biometricOffer.asStateFlow()

    fun offerBiometricsAfterSetup() {
        biometricOffer.value = true
    }

    fun biometricOfferHandled() {
        biometricOffer.value = false
    }
}
