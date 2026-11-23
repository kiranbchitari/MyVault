package com.myvault.app.ui.items

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvault.app.security.BiometricAuthResult
import com.myvault.app.security.authenticate
import com.myvault.app.ui.components.PassphraseDialog
import com.myvault.app.ui.main.VaultViewModel
import com.myvault.app.vault.FieldKind
import com.myvault.app.vault.VaultItem
import kotlinx.coroutines.launch

/**
 * Returns a gate for anything that shows or copies a secret. The gate runs its action straight
 * away, or, when the vault is set to ask again before revealing secrets, only after the user has
 * confirmed who they are with biometrics or the master passphrase.
 */
@Composable
fun rememberIdentityGuard(vault: VaultViewModel): (action: () -> Unit) -> Unit {
    val biometricEnrolled by vault.biometricEnrolled.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var askPassphrase by remember { mutableStateOf(false) }
    var passphraseError by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    if (askPassphrase) {
        PassphraseDialog(
            title = "Confirm it's you",
            body = "Enter your master passphrase to show or copy this secret.",
            confirmLabel = "Confirm",
            error = passphraseError,
            working = checking,
            onDismiss = {
                askPassphrase = false
                pendingAction = null
                passphraseError = null
            },
            onSubmit = { passphrase, _ ->
                checking = true
                scope.launch {
                    if (vault.confirmWithPassphrase(passphrase)) {
                        askPassphrase = false
                        passphraseError = null
                        pendingAction?.invoke()
                        pendingAction = null
                    } else {
                        passphraseError = "Incorrect passphrase."
                    }
                    checking = false
                }
            },
        )
    }

    return guard@{ action ->
        if (!vault.needsIdentityCheck()) return@guard action()
        pendingAction = action
        if (biometricEnrolled && activity != null) {
            scope.launch {
                val cipher = vault.beginBiometricConfirm()
                val result = cipher?.let { activity.authenticate(it, "Confirm it's you", "Needed before a secret is shown", "Use passphrase") }
                if (result is BiometricAuthResult.Success && vault.finishBiometricConfirm(result.cipher)) {
                    pendingAction?.invoke()
                    pendingAction = null
                } else {
                    askPassphrase = true
                }
            }
        } else {
            askPassphrase = true
        }
    }
}

/**
 * Returns the single-tap copy used by item lists: it copies the record's main value (password,
 * PIN, key…) without opening the record, behind the same identity check as the detail screen.
 */
@Composable
fun rememberQuickCopy(vault: VaultViewModel): (VaultItem) -> Unit {
    val guarded = rememberIdentityGuard(vault)
    return { item ->
        item.quickCopyField?.let { field ->
            val copy = {
                vault.copyField(item, field)
                vault.markAccessed(item.id)
            }
            if (field.kind == FieldKind.SECRET) guarded(copy) else copy()
        }
    }
}
