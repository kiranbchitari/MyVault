package com.myvault.app.ui.unlock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvault.app.security.BiometricAuthResult
import com.myvault.app.security.authenticate
import com.myvault.app.ui.components.ErrorText
import com.myvault.app.ui.components.PassphraseField
import com.myvault.app.ui.components.PrimaryButton
import com.myvault.app.ui.components.QuietButton
import com.myvault.app.ui.components.VaultLogo
import com.myvault.app.ui.components.VaultScreen
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(viewModel: UnlockViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var biometricPromptShown by remember { mutableStateOf(false) }

    val startBiometricUnlock: () -> Unit = {
        if (activity != null) {
            scope.launch {
                val cipher = viewModel.beginBiometricUnlock() ?: return@launch
                val result = activity.authenticate(
                    cipher = cipher,
                    title = "Unlock MyVault",
                    subtitle = "Confirm it's you to open your vault",
                    negativeButton = "Use passphrase",
                )
                when (result) {
                    is BiometricAuthResult.Success -> viewModel.finishBiometricUnlock(result.cipher)
                    is BiometricAuthResult.Failed -> viewModel.biometricFailed(result.message)
                    BiometricAuthResult.Cancelled -> Unit
                }
            }
        }
    }

    // Offer biometrics once, automatically, when the screen opens.
    LaunchedEffect(uiState.biometricEnrolled) {
        if (uiState.biometricEnrolled == true && !biometricPromptShown) {
            biometricPromptShown = true
            startBiometricUnlock()
        }
    }

    UnlockContent(uiState = uiState, onUnlock = viewModel::unlock, onUseBiometrics = startBiometricUnlock)
}

@Composable
fun UnlockContent(uiState: UnlockUiState, onUnlock: (String) -> Unit, onUseBiometrics: () -> Unit) {
    // Plain remember on purpose: the passphrase must never reach saved instance state.
    var passphrase by remember { mutableStateOf("") }
    val submit = {
        onUnlock(passphrase)
        passphrase = ""
    }

    VaultScreen(
        modifier = Modifier.testTag("unlock-screen"),
        bottomBar = {
            PrimaryButton(
                text = if (uiState.working) "Unlocking…" else "Unlock",
                onClick = submit,
                enabled = passphrase.isNotEmpty(),
                loading = uiState.working,
            )
            if (uiState.biometricEnrolled == true) {
                QuietButton("Use biometrics", onUseBiometrics, enabled = !uiState.working)
            }
        },
    ) {
        Spacer(Modifier.height(20.dp))
        VaultLogo(size = 76.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
        Text(
            "Welcome back",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter your master passphrase to unlock your vault.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(32.dp))
        PassphraseField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = "Master passphrase",
            enabled = !uiState.working,
            onImeAction = submit,
        )
        Spacer(Modifier.height(12.dp))
        ErrorText(uiState.error)
        uiState.notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
    }
}
