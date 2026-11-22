package com.myvault.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.BuildConfig
import com.myvault.app.backup.BackupCoordinator
import com.myvault.app.backup.BackupHealth
import com.myvault.app.backup.BackupKeyManager
import com.myvault.app.backup.BackupStatus
import com.myvault.app.data.SecuritySettings
import com.myvault.app.data.SecuritySettingsRepository
import com.myvault.app.security.BiometricUnlockStart
import com.myvault.app.security.BiometricVault
import com.myvault.app.security.VaultManager
import com.myvault.app.util.DemoData
import com.myvault.app.util.SecureClipboard
import com.myvault.app.vault.AttachmentTooLargeException
import com.myvault.app.vault.VaultContents
import com.myvault.app.vault.VaultItem
import com.myvault.app.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.crypto.Cipher
import javax.inject.Inject

data class BackupOverview(val health: BackupHealth = BackupHealth.NOT_SET_UP, val lastSuccessAt: Long? = null, val lastSuccessName: String? = null)

/** Shared by the screens that read the vault: dashboard, lists, search and item details. */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val vaultManager: VaultManager,
    private val biometricVault: BiometricVault,
    private val clipboard: SecureClipboard,
    settingsRepository: SecuritySettingsRepository,
    backupCoordinator: BackupCoordinator,
    backupKeys: BackupKeyManager,
    private val clock: Clock,
) : ViewModel() {

    val contents: StateFlow<VaultContents?> = repository.contents

    val settings: StateFlow<SecuritySettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SecuritySettings())

    val backup: StateFlow<BackupOverview> =
        combine(backupKeys.configured, backupCoordinator.history, settingsRepository.settings) { configured, history, current ->
            val last = history.filter { it.status == BackupStatus.COMPLETED }.maxByOrNull { it.createdAt }
            BackupOverview(BackupHealth.evaluate(configured, history, current.vaultChangedAt, clock.millis()), last?.createdAt, last?.fileName)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupOverview())

    val biometricEnrolled: StateFlow<Boolean> = biometricVault.enrolled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val messageEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Short confirmations shown as a snackbar. */
    val messages: SharedFlow<String> = messageEvents.asSharedFlow()

    /** The vault was just unlocked when this view model was created. */
    private var lastIdentityCheck = clock.millis()

    fun show(message: String) {
        messageEvents.tryEmit(message)
    }

    fun item(id: String): VaultItem? = contents.value?.items?.firstOrNull { it.id == id }

    fun toggleFavorite(item: VaultItem) {
        viewModelScope.launch { runCatching { repository.setFavorite(item.id, !item.favorite) } }
    }

    fun markAccessed(id: String) {
        viewModelScope.launch { runCatching { repository.markAccessed(id) } }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }.onSuccess { show("Item deleted") }
        }
    }

    fun copy(label: String, value: String, sensitive: Boolean) {
        viewModelScope.launch {
            val seconds = clipboard.copy(label, value, sensitive)
            show(if (seconds > 0) "$label copied. Clipboard clears in $seconds seconds." else "$label copied")
        }
    }

    fun lock() = vaultManager.lock()

    // --- Re-authentication before revealing or copying secrets -------------------------------

    fun needsIdentityCheck(): Boolean =
        settings.value.reauthToReveal && clock.millis() - lastIdentityCheck > IDENTITY_GRACE_MS

    suspend fun confirmWithPassphrase(passphrase: String): Boolean {
        val chars = passphrase.toCharArray()
        val ok = try {
            vaultManager.verifyPassphrase(chars)
        } finally {
            chars.fill('\u0000')
        }
        if (ok) lastIdentityCheck = clock.millis()
        return ok
    }

    suspend fun beginBiometricConfirm(): Cipher? = (biometricVault.beginUnlock() as? BiometricUnlockStart.Ready)?.cipher

    suspend fun finishBiometricConfirm(cipher: Cipher): Boolean =
        biometricVault.verify(cipher).also { if (it) lastIdentityCheck = clock.millis() }

    // --- Attachments ---------------------------------------------------------------------------

    fun addAttachment(itemId: String, name: String, mimeType: String, data: ByteArray) {
        viewModelScope.launch {
            try {
                repository.addAttachment(itemId, name, mimeType, data)
                show("Attachment encrypted and saved")
            } catch (_: AttachmentTooLargeException) {
                show("That file is too large. The limit is ${VaultRepository.MAX_ATTACHMENT_BYTES / (1024 * 1024)} MB.")
            } catch (_: Exception) {
                show("The attachment could not be saved")
            }
        }
    }

    suspend fun readAttachment(id: String): ByteArray? = runCatching { repository.readAttachment(id) }.getOrNull()

    fun deleteAttachment(id: String) {
        viewModelScope.launch { runCatching { repository.deleteAttachment(id) } }
    }

    // --- Debug builds only ---------------------------------------------------------------------

    val demoDataAvailable: Boolean get() = BuildConfig.DEBUG

    fun loadDemoData() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            runCatching { DemoData.items().forEach { repository.save(it.copy(id = repository.newId())) } }
                .onSuccess { show("Demo data added") }
        }
    }

    private companion object {
        const val IDENTITY_GRACE_MS = 30_000L
    }
}
