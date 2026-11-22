package com.myvault.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.components.AutoLockChips
import com.myvault.app.ui.components.ErrorText
import com.myvault.app.ui.components.PassphraseField
import com.myvault.app.ui.components.PrimaryButton
import com.myvault.app.ui.components.QuietButton
import com.myvault.app.ui.components.ScreenTitle
import com.myvault.app.ui.components.StrengthMeter
import com.myvault.app.ui.components.VaultCard
import com.myvault.app.ui.components.VaultIcons
import com.myvault.app.ui.components.VaultLogo
import com.myvault.app.ui.components.VaultScreen
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.crypto.PassphraseStrength

@Composable
fun WelcomeScreen(onStart: () -> Unit, onRestore: () -> Unit = {}) {
    VaultScreen(
        bottomBar = {
            PrimaryButton("Create my vault", onStart)
            QuietButton("Restore from a backup", onRestore)
            Text(
                "Works fully offline. No account needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        },
    ) {
        Spacer(Modifier.height(28.dp))
        VaultLogo(size = 92.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(28.dp))
        Text(
            "MyVault",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your private digital locker.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(40.dp))
        VaultCard {
            FeatureRow("Private by design", "Everything stays on this device. No servers, no accounts, no tracking.")
            Spacer(Modifier.height(18.dp))
            FeatureRow("Encrypted with AES-256", "Your data is encrypted before it is ever written to storage.")
            Spacer(Modifier.height(18.dp))
            FeatureRow("Only you hold the key", "Your master passphrase is never stored, so nobody else can open your vault.")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FeatureRow(title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 2.dp).size(22.dp).clip(CircleShape).background(VaultTheme.palette.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(VaultIcons.Check, contentDescription = null, tint = VaultTheme.palette.accent, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CreatePassphraseScreen(onBack: () -> Unit, onContinue: (String) -> Unit) {
    // Plain remember on purpose: the passphrase must never reach saved instance state.
    var passphrase by remember { mutableStateOf("") }
    val assessment = remember(passphrase) { PassphraseStrength.assess(passphrase) }

    VaultScreen(
        onBack = onBack,
        bottomBar = { PrimaryButton("Continue", { onContinue(passphrase) }, enabled = assessment.acceptable) },
    ) {
        ScreenTitle(
            "Create your master passphrase",
            "This one passphrase encrypts everything in your vault. Make it long and memorable: several unrelated words work well.",
        )
        Spacer(Modifier.height(28.dp))
        PassphraseField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = "Master passphrase",
            imeAction = ImeAction.Next,
            onImeAction = { if (assessment.acceptable) onContinue(passphrase) },
        )
        Spacer(Modifier.height(14.dp))
        StrengthMeter(assessment)
        Spacer(Modifier.height(24.dp))
        VaultCard {
            Text("Tips", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "• At least ${PassphraseStrength.MIN_LENGTH} characters; longer is better\n" +
                    "• Don't reuse a password from another service\n" +
                    "• Write it down and keep it somewhere safe and offline",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun ConfirmPassphraseScreen(onBack: () -> Unit, matches: (String) -> Boolean, onConfirmed: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    val submit = {
        if (matches(passphrase)) onConfirmed() else mismatch = true
    }

    VaultScreen(
        onBack = onBack,
        bottomBar = { PrimaryButton("Continue", submit, enabled = passphrase.isNotEmpty()) },
    ) {
        ScreenTitle("Type it once more", "Confirm your master passphrase so a typo can't lock you out.")
        Spacer(Modifier.height(28.dp))
        PassphraseField(
            value = passphrase,
            onValueChange = {
                passphrase = it
                mismatch = false
            },
            label = "Confirm passphrase",
            errorText = if (mismatch) "The passphrases don't match." else null,
            onImeAction = submit,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SecuritySetupScreen(
    uiState: OnboardingUiState,
    onBack: () -> Unit,
    onAutoLockSelected: (Int) -> Unit,
    onCreate: () -> Unit,
) {
    var acknowledged by remember { mutableStateOf(false) }

    VaultScreen(
        onBack = if (uiState.creating) null else onBack,
        bottomBar = {
            ErrorText(uiState.error, Modifier.padding(bottom = 8.dp))
            PrimaryButton(
                text = if (uiState.creating) "Securing your vault…" else "Create vault",
                onClick = onCreate,
                enabled = acknowledged,
                loading = uiState.creating,
            )
        },
    ) {
        ScreenTitle("Secure your vault", "A few things to know before your vault is created.")
        Spacer(Modifier.height(24.dp))

        VaultCard {
            Text("How it's protected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Fact("Encryption", "AES-256-GCM")
            Fact("Key derivation", "Argon2id")
            Fact("Storage", "This device only")
        }
        Spacer(Modifier.height(14.dp))

        VaultCard {
            Text("Auto-lock after", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            AutoLockChips(selectedSeconds = uiState.autoLockSeconds, onSelected = onAutoLockSelected, enabled = !uiState.creating)
            Spacer(Modifier.height(12.dp))
            Text(
                "MyVault also locks whenever you leave the app or the screen turns off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))

        VaultCard {
            Text("There is no reset", style = MaterialTheme.typography.titleMedium, color = VaultTheme.palette.warning)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your passphrase is never stored or sent anywhere. If you forget it, your vault cannot be opened by anyone, including us.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .toggleable(
                        value = acknowledged,
                        enabled = !uiState.creating,
                        role = Role.Checkbox,
                        onValueChange = { acknowledged = it },
                    )
                    .testTag("acknowledge")
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = acknowledged,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = VaultTheme.palette.accent, checkmarkColor = Color.White),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "I understand that a forgotten passphrase cannot be recovered.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
