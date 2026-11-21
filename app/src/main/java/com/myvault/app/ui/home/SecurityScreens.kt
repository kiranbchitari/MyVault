package com.myvault.app.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.myvault.app.ui.components.AutoLockChips
import com.myvault.app.ui.components.ErrorText
import com.myvault.app.ui.components.FingerprintGlyph
import com.myvault.app.ui.components.PrimaryButton
import com.myvault.app.ui.components.QuietButton
import com.myvault.app.ui.components.VaultCard
import com.myvault.app.ui.components.VaultLogo
import com.myvault.app.ui.components.VaultScreen
import com.myvault.app.ui.theme.VaultTheme
import kotlinx.coroutines.launch

/** Runs the two-step biometric enrollment: create the Keystore key, then authenticate to use it. */
private suspend fun enrollBiometrics(activity: FragmentActivity, viewModel: SecurityViewModel): Boolean {
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

/**
 * What an unlocked vault shows in this phase: its security status and controls. Item storage,
 * search and the full dashboard replace this screen in later phases.
 */
@Composable
fun VaultStatusScreen(viewModel: SecurityViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAvailability()
        onPauseOrDispose {}
    }

    VaultStatusContent(
        uiState = uiState,
        canToggleBiometrics = activity != null &&
            (uiState.biometricEnrolled || uiState.biometricAvailability == BiometricAvailability.AVAILABLE),
        onBiometricsToggled = { enable ->
            if (!enable) viewModel.disableBiometrics()
            else if (activity != null) scope.launch { enrollBiometrics(activity, viewModel) }
        },
        onAutoLockSelected = viewModel::setAutoLock,
        onLock = viewModel::lock,
    )
}

@Composable
fun VaultStatusContent(
    uiState: SecurityUiState,
    canToggleBiometrics: Boolean,
    onBiometricsToggled: (Boolean) -> Unit,
    onAutoLockSelected: (Int) -> Unit,
    onLock: () -> Unit,
) {
    val palette = VaultTheme.palette
    VaultScreen(
        modifier = Modifier.testTag("vault-status"),
        bottomBar = { PrimaryButton("Lock vault", onLock, modifier = Modifier.testTag("lock")) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VaultLogo(size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Text("MyVault", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(24.dp))

        VaultCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(palette.success))
                Spacer(Modifier.width(10.dp))
                Text("Vault unlocked", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Your vault key is held in memory only while the vault is unlocked, and is wiped the moment it locks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))

        VaultCard {
            Text("Security", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            StatusRow("Master passphrase", "Configured")
            HorizontalDivider(color = palette.cardBorder)
            StatusRow("Encryption", "AES-256-GCM")
            HorizontalDivider(color = palette.cardBorder)
            StatusRow("Key derivation", "Argon2id")
            HorizontalDivider(color = palette.cardBorder)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Biometric unlock", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = when {
                            uiState.biometricEnrolled -> "On"
                            uiState.biometricAvailability == BiometricAvailability.AVAILABLE -> "Off"
                            uiState.biometricAvailability == BiometricAvailability.NONE_ENROLLED -> "No biometrics set up on this device"
                            else -> "Not supported on this device"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.biometricEnrolled,
                    enabled = canToggleBiometrics,
                    onCheckedChange = onBiometricsToggled,
                    colors = SwitchDefaults.colors(checkedTrackColor = palette.accent, checkedThumbColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.testTag("biometric-switch"),
                )
            }
            ErrorText(uiState.message, Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(14.dp))

        VaultCard {
            Text("Auto-lock after", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            AutoLockChips(selectedSeconds = uiState.autoLockSeconds, onSelected = onAutoLockSelected)
            Spacer(Modifier.height(12.dp))
            Text(
                "MyVault also locks whenever you leave the app or the screen turns off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))

        VaultCard {
            Text("Your vault is ready", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "Saving passwords, cards and notes is not available in this build yet. Items, search and encrypted backups are added in the next phases.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
