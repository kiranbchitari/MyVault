package com.myvault.app.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvault.app.security.BiometricAuthResult
import com.myvault.app.security.BiometricAvailability
import com.myvault.app.security.authenticate
import com.myvault.app.ui.components.ErrorText
import com.myvault.app.ui.components.FingerprintGlyph
import com.myvault.app.ui.components.PrimaryButton
import com.myvault.app.ui.components.QuietButton
import com.myvault.app.ui.components.VaultCard
import com.myvault.app.ui.components.VaultScreen
import kotlinx.coroutines.launch

/** Runs the two-step biometric enrollment: create the Keystore key, then authenticate to use it. */
suspend fun enrollBiometrics(activity: FragmentActivity, viewModel: SecurityViewModel): Boolean {
    val cipher = viewModel.beginBiometricEnrollment() ?: return false
    val result = activity.authenticate(
        cipher = cipher,
        title = "Turn on biometric unlock",
        subtitle = "Confirm it's you to protect your vault key",
        negativeButton = "Cancel",
    )
    return when (result) {
        is BiometricAuthResult.Success -> viewModel.finishBiometricEnrollment(result.cipher)
        is BiometricAuthResult.Failed -> {
            viewModel.showMessage(result.message)
            false
        }
        BiometricAuthResult.Cancelled -> false
    }
}

/** One-time offer shown right after the vault is created. */
@Composable
fun BiometricSetupScreen(onDone: () -> Unit, viewModel: SecurityViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAvailability()
        onPauseOrDispose {}
    }
    BiometricSetupContent(
        uiState = uiState,
        canEnroll = uiState.biometricAvailability == BiometricAvailability.AVAILABLE && activity != null,
        onEnable = { scope.launch { if (activity != null && enrollBiometrics(activity, viewModel)) onDone() } },
        onSkip = onDone,
    )
}

@Composable
fun BiometricSetupContent(uiState: SecurityUiState, canEnroll: Boolean, onEnable: () -> Unit, onSkip: () -> Unit) {
    VaultScreen(
        modifier = Modifier.testTag("biometric-setup"),
        bottomBar = {
            ErrorText(uiState.message, Modifier.padding(bottom = 8.dp))
            if (canEnroll) {
                PrimaryButton("Enable biometric unlock", onEnable)
                QuietButton("Not now", onSkip)
            } else {
                PrimaryButton("Continue", onSkip)
            }
        },
    ) {
        Spacer(Modifier.height(36.dp))
        FingerprintGlyph(size = 104.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(32.dp))
        Text(
            "Unlock with biometrics",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = when (uiState.biometricAvailability) {
                BiometricAvailability.AVAILABLE ->
                    "Open MyVault with your fingerprint or face. Your vault key is sealed by this device's secure hardware and released only after a successful match."
                BiometricAvailability.NONE_ENROLLED ->
                    "No fingerprint or face is set up on this device yet. Add one in system settings and you can turn this on later."
                else ->
                    "This device doesn't offer the strong biometric hardware MyVault requires, so you'll unlock with your passphrase."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        VaultCard {
            Text(
                "Your passphrase is still the master key. You'll need it if your biometrics change, and it is the only way to recover your vault.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
