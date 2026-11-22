package com.myvault.app.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvault.app.BuildConfig
import com.myvault.app.backup.BackupHealth
import com.myvault.app.data.SecuritySettings
import com.myvault.app.security.BiometricAvailability
import com.myvault.app.ui.components.AutoLockChips
import com.myvault.app.ui.components.ChoiceChips
import com.myvault.app.ui.components.ErrorText
import com.myvault.app.ui.components.LocalExpectExternalFlow
import com.myvault.app.ui.components.PassphraseDialog
import com.myvault.app.ui.components.PrimaryButton
import com.myvault.app.ui.components.ScreenColumn
import com.myvault.app.ui.components.SettingRow
import com.myvault.app.ui.components.VaultCard
import com.myvault.app.ui.components.VaultSwitch
import com.myvault.app.ui.components.VaultLogo
import com.myvault.app.ui.home.SecurityViewModel
import com.myvault.app.ui.home.enrollBiometrics
import com.myvault.app.ui.main.VaultViewModel
import com.myvault.app.ui.main.formatDay
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.util.RecoveryKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun Divider() = HorizontalDivider(thickness = 0.7.dp, color = VaultTheme.palette.cardBorder)

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun SettingsScreen(
    vault: VaultViewModel,
    onSecurity: () -> Unit,
    onBackup: () -> Unit,
    onGenerator: () -> Unit,
    onAbout: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    val palette = VaultTheme.palette
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAvailability()
        onPauseOrDispose {}
    }

    ScreenColumn(title = "Settings", modifier = Modifier.testTag("settings")) {
        VaultCard {
            CardTitle("Auto-lock after")
            AutoLockChips(selectedSeconds = state.autoLockSeconds, onSelected = viewModel::setAutoLock)
            Spacer(Modifier.height(10.dp))
            Text(
                "MyVault also locks whenever you leave the app or the screen turns off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))

        VaultCard {
            CardTitle("Clear copied secrets after")
            ChoiceChips(
                options = SecuritySettings.CLIPBOARD_OPTIONS_SECONDS,
                selected = state.clipboardClearSeconds,
                label = { if (it < 60) "$it sec" else "${it / 60} min" },
                onSelected = viewModel::setClipboardClear,
                tagPrefix = "clipboard",
            )
        }
        Spacer(Modifier.height(14.dp))

        VaultCard {
            SettingRow(
                title = "Biometric unlock",
                subtitle = when {
                    state.biometricEnrolled -> "On"
                    state.biometricAvailability == BiometricAvailability.AVAILABLE -> "Off"
                    state.biometricAvailability == BiometricAvailability.NONE_ENROLLED -> "No biometrics set up on this device"
                    else -> "Not supported on this device"
                },
                trailing = {
                    VaultSwitch(
                        checked = state.biometricEnrolled,
                        enabled = activity != null && (state.biometricEnrolled || state.biometricAvailability == BiometricAvailability.AVAILABLE),
                        onCheckedChange = { enable ->
                            if (!enable) viewModel.disableBiometrics()
                            else if (activity != null) scope.launch { enrollBiometrics(activity, viewModel) }
                        },
                        modifier = Modifier.testTag("biometric-switch"),
                    )
                },
            )
            Divider()
            SettingRow(
                title = "Confirm before revealing",
                subtitle = "Ask for your passphrase or biometrics again before a secret is shown or copied",
                trailing = {
                    VaultSwitch(checked = state.reauthToReveal, onCheckedChange = viewModel::setReauthToReveal, modifier = Modifier.testTag("reauth-switch"))
                },
            )
            Divider()
            SettingRow("Security & recovery", icon = Icons.Rounded.Shield, iconColor = Color(0xFF34C759), subtitle = "Passphrase, encryption key, recovery kit", onClick = onSecurity, modifier = Modifier.testTag("row-security"))
            ErrorText(state.message, Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(14.dp))

        VaultCard {
            SettingRow("Backup & restore", icon = Icons.Rounded.Cloud, iconColor = Color(0xFF1F7AFF), subtitle = state.backupHealth.title, onClick = onBackup, modifier = Modifier.testTag("row-backup"))
            Divider()
            SettingRow("Password generator", icon = Icons.Rounded.AutoAwesome, iconColor = Color(0xFF8B5CF6), onClick = onGenerator, modifier = Modifier.testTag("row-generator"))
            Divider()
            SettingRow("About MyVault", icon = Icons.Rounded.Info, iconColor = Color(0xFF8E8E93), subtitle = "Version ${BuildConfig.VERSION_NAME}", onClick = onAbout, modifier = Modifier.testTag("row-about"))
            if (vault.demoDataAvailable) {
                Divider()
                SettingRow("Load demo data", icon = Icons.Rounded.BugReport, iconColor = Color(0xFFFF9F0A), subtitle = "Debug builds only · adds fictional sample items", onClick = vault::loadDemoData, modifier = Modifier.testTag("row-demo"))
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Lock vault", viewModel::lock, modifier = Modifier.testTag("lock-vault"))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SecurityRecoveryScreen(onBack: () -> Unit, onBackup: () -> Unit, viewModel: SecurityViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val expectExternalFlow = LocalExpectExternalFlow.current
    val palette = VaultTheme.palette
    var changing by remember { mutableStateOf(false) }
    var rotating by remember { mutableStateOf(false) }

    val saveKit = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            scope.launch {
                val text = viewModel.recoveryKitText()
                val saved = text != null && withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
                }
                viewModel.showMessage(if (saved) "Recovery kit saved. Print it or keep it somewhere safe." else "The recovery kit could not be saved.")
            }
        }
    }

    ScreenColumn(title = "Security & recovery", modifier = Modifier.testTag("security"), onBack = onBack) {
        VaultCard {
            CardTitle("Status")
            StatusLine("Master passphrase", state.passphraseChangedAt?.let { "Configured · set ${formatDay(it)}" } ?: "Configured", good = true)
            Divider()
            StatusLine("Biometric unlock", if (state.biometricEnrolled) "On" else "Off", good = state.biometricEnrolled, neutral = !state.biometricEnrolled)
            Divider()
            StatusLine("Backup passphrase", if (state.backupConfigured) "Configured" else "Not set", good = state.backupConfigured)
            Divider()
            StatusLine("Last successful backup", state.lastBackupAt?.let(::formatDay) ?: "Never", good = state.lastBackupAt != null)
            Divider()
            StatusLine("Backup health", state.backupHealth.title, good = state.backupHealth == BackupHealth.GOOD)
            Divider()
            StatusLine("Encryption", "AES-256-GCM · Argon2id", good = true)
            Divider()
            StatusLine("Key generation", "${state.keyGeneration}", good = true, neutral = true)
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.success, modifier = Modifier.padding(top = 12.dp).testTag("security-message"))
        }
        Spacer(Modifier.height(14.dp))

        VaultCard {
            SettingRow("Change master passphrase", icon = Icons.Rounded.Key, iconColor = Color(0xFF1F7AFF), subtitle = "Your items are not re-encrypted; only the key's wrapping changes", onClick = {
                viewModel.clearDialogError()
                changing = true
            }, modifier = Modifier.testTag("row-change-passphrase"))
            Divider()
            SettingRow("Replace encryption key", icon = Icons.Rounded.Refresh, iconColor = Color(0xFFFF9F0A), subtitle = "Generates a new vault key and re-encrypts every item", onClick = {
                viewModel.clearDialogError()
                rotating = true
            }, modifier = Modifier.testTag("row-rotate-key"))
            Divider()
            SettingRow("Export recovery kit", icon = Icons.Rounded.Description, iconColor = Color(0xFF8B5CF6), subtitle = "A sheet of what you need to recover. Contains no secrets", onClick = {
                expectExternalFlow()
                saveKit.launch(RecoveryKit.FILE_NAME)
            }, modifier = Modifier.testTag("row-recovery-kit"))
            Divider()
            SettingRow("Backup & restore", icon = Icons.Rounded.Cloud, iconColor = Color(0xFF34C759), onClick = onBackup)
        }
        Spacer(Modifier.height(14.dp))

        VaultCard {
            CardTitle("How recovery works")
            Text(
                "• On this phone, your master passphrase is the only way in. Biometrics are a shortcut that stops working if they change.\n" +
                    "• To move to a new phone, or after losing this one, you need a backup file and your backup passphrase.\n" +
                    "• Neither passphrase is stored anywhere and neither can be reset.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "If you lose the master passphrase and have no backup you can open, your data is permanently unrecoverable.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.warning,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (changing) {
        PassphraseDialog(
            title = "Change master passphrase",
            body = "Enter your current passphrase, then the new one twice.",
            confirmLabel = "Change",
            currentLabel = "Current passphrase",
            confirmNew = true,
            newLabel = "New master passphrase",
            working = state.working,
            error = state.dialogError,
            onDismiss = { changing = false },
            onSubmit = { current, new -> viewModel.changePassphrase(current, new) { changing = false } },
        )
    }
    if (rotating) {
        PassphraseDialog(
            title = "Replace encryption key",
            body = "A new vault key is generated and every item is re-encrypted with it. Your passphrases stay the same. Biometric unlock is turned off and needs to be switched on again afterwards.",
            confirmLabel = "Replace key",
            working = state.working,
            error = state.dialogError,
            onDismiss = { rotating = false },
            onSubmit = { passphrase, _ -> viewModel.rotateKey(passphrase) { rotating = false } },
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String, good: Boolean, neutral: Boolean = false) {
    val palette = VaultTheme.palette
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            color = when {
                neutral -> MaterialTheme.colorScheme.onSurfaceVariant
                good -> palette.success
                else -> palette.warning
            },
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenColumn(title = "About", modifier = Modifier.testTag("about"), onBack = onBack) {
        Spacer(Modifier.height(12.dp))
        VaultLogo(size = 72.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Text("MyVault", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(24.dp))
        VaultCard {
            CardTitle("Privacy")
            Text(
                "MyVault has no server, no account, no analytics and no advertising. Everything you save is encrypted on this phone and stays here. " +
                    "The only time the app uses the network is when you choose to back up to Google Drive, and then it sends only an encrypted file.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        VaultCard {
            CardTitle("How it is protected")
            Text(
                "• AES-256-GCM encrypts every record, attachment and backup\n" +
                    "• Argon2id turns your passphrases into keys\n" +
                    "• Android Keystore guards the key used for biometric unlock\n" +
                    "• Your passphrases are never stored",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        VaultCard {
            CardTitle("Open-source components")
            Text(
                "Bouncy Castle (MIT licence) · AndroidX, Jetpack Compose, Room, WorkManager and Dagger Hilt (Apache 2.0) · " +
                    "Kotlin coroutines and serialization (Apache 2.0) · Google Play services auth (Android SDK licence)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
