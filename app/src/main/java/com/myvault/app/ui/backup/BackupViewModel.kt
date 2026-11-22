package com.myvault.app.ui.backup

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.backup.BackupCoordinator
import com.myvault.app.backup.BackupException
import com.myvault.app.backup.BackupHealth
import com.myvault.app.backup.BackupKeyManager
import com.myvault.app.backup.BackupStatus
import com.myvault.app.backup.CorruptedBackupException
import com.myvault.app.backup.DriveAuthorization
import com.myvault.app.backup.DriveAuthorizer
import com.myvault.app.backup.DriveException
import com.myvault.app.backup.DriveFile
import com.myvault.app.backup.NotABackupException
import com.myvault.app.backup.RestoreMode
import com.myvault.app.backup.UnsupportedBackupException
import com.myvault.app.backup.UploadProgress
import com.myvault.app.backup.WrongBackupPassphraseException
import com.myvault.app.data.SecuritySettings
import com.myvault.app.data.SecuritySettingsRepository
import com.myvault.app.data.db.BackupMetadataEntity
import com.myvault.app.security.VaultLockedException
import com.myvault.app.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import javax.inject.Inject

data class BackupUiState(
    val configured: Boolean = false,
    val health: BackupHealth = BackupHealth.NOT_SET_UP,
    val history: List<BackupMetadataEntity> = emptyList(),
    val settings: SecuritySettings = SecuritySettings(),
    val upload: UploadProgress = UploadProgress(),
    val itemCount: Int = 0,
) {
    val lastSuccessful: BackupMetadataEntity? get() = history.filter { it.status == BackupStatus.COMPLETED }.maxByOrNull { it.createdAt }
}

data class BackupActionState(val busy: Boolean = false, val message: String? = null, val error: String? = null)

data class RestoreUiState(
    val driveFiles: List<DriveFile>? = null,
    val loadingDrive: Boolean = false,
    val selectedName: String? = null,
    val working: Boolean = false,
    val error: String? = null,
    val restoredItems: Int? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val coordinator: BackupCoordinator,
    private val backupKeys: BackupKeyManager,
    private val settingsRepository: SecuritySettingsRepository,
    private val authorizer: DriveAuthorizer,
    repository: VaultRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<BackupUiState> = combine(
        backupKeys.configured, coordinator.history, settingsRepository.settings, coordinator.uploadProgress, repository.contents,
    ) { configured, history, settings, upload, contents ->
        BackupUiState(
            configured = configured,
            health = BackupHealth.evaluate(configured, history, settings.vaultChangedAt, clock.millis()),
            history = history,
            settings = settings,
            upload = upload,
            itemCount = contents?.items?.size ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupUiState())

    private val action = MutableStateFlow(BackupActionState())
    val actionState: StateFlow<BackupActionState> = action.asStateFlow()

    private val restoreState = MutableStateFlow(RestoreUiState())
    val restore: StateFlow<RestoreUiState> = restoreState.asStateFlow()
    private var restoreFile: File? = null

    fun clearMessages() = action.update { it.copy(message = null, error = null) }

    private fun run(success: String?, block: suspend () -> Unit) {
        if (action.value.busy) return
        action.value = BackupActionState(busy = true)
        viewModelScope.launch {
            action.value = try {
                block()
                BackupActionState(message = success)
            } catch (e: Exception) {
                BackupActionState(error = describe(e))
            }
        }
    }

    fun setBackupPassphrase(passphrase: String, onDone: () -> Unit) = run("Backup passphrase saved") {
        val chars = passphrase.toCharArray()
        try {
            backupKeys.setPassphrase(chars)
        } finally {
            chars.fill('\u0000')
        }
        onDone()
    }

    fun exportTo(uri: Uri) = run("Backup saved and verified") { coordinator.exportTo(uri) }

    fun backupToDrive() = run("Backup created. It will upload as soon as you're online.") { coordinator.queueDriveBackup() }

    fun retryUploads() = coordinator.enqueueUpload()

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoBackup(enabled) }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch { coordinator.deleteRecord(id) }
    }

    fun suggestedFileName(): String = com.myvault.app.backup.BackupFormat.fileName(clock.millis())

    // --- Google Drive --------------------------------------------------------------------------

    suspend fun authorizeDrive(): DriveAuthorization = authorizer.authorize()

    fun driveAuthorized() {
        viewModelScope.launch {
            coordinator.connectDrive()
            action.value = BackupActionState(message = "Google Drive connected")
        }
    }

    fun driveConsentResult(data: Intent?) {
        when (val result = authorizer.fromConsentResult(data)) {
            is DriveAuthorization.Granted -> driveAuthorized()
            is DriveAuthorization.Failed -> action.value = BackupActionState(error = result.message)
            is DriveAuthorization.ConsentRequired -> action.value = BackupActionState(error = "Google Drive access was not granted.")
        }
    }

    fun driveFailed(message: String) {
        action.value = BackupActionState(error = message)
    }

    fun disconnectDrive() = run("Google Drive disconnected. Backups already in Drive were not deleted.") { coordinator.disconnectDrive() }

    // --- Restore -------------------------------------------------------------------------------

    fun pickedBackupFile(uri: Uri, name: String) {
        restoreState.value = RestoreUiState(working = true)
        viewModelScope.launch {
            restoreState.value = try {
                val file = coordinator.copyForRestore(uri)
                coordinator.inspect(file)
                restoreFile = file
                RestoreUiState(selectedName = name)
            } catch (e: Exception) {
                RestoreUiState(error = describe(e))
            }
        }
    }

    fun loadDriveBackups() {
        restoreState.value = RestoreUiState(loadingDrive = true)
        viewModelScope.launch {
            restoreState.value = when (val authorization = authorizer.authorize()) {
                is DriveAuthorization.Granted -> try {
                    RestoreUiState(driveFiles = coordinator.listDriveBackups(authorization.accessToken))
                } catch (e: Exception) {
                    RestoreUiState(error = describe(e))
                }
                is DriveAuthorization.ConsentRequired -> RestoreUiState(error = "Reconnect Google Drive in Backup settings first.")
                is DriveAuthorization.Failed -> RestoreUiState(error = authorization.message)
            }
        }
    }

    fun chooseDriveBackup(file: DriveFile) {
        restoreState.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            restoreState.value = when (val authorization = authorizer.authorize()) {
                is DriveAuthorization.Granted -> try {
                    val downloaded = coordinator.downloadDriveBackup(authorization.accessToken, file.id)
                    coordinator.inspect(downloaded)
                    restoreFile = downloaded
                    RestoreUiState(selectedName = file.name)
                } catch (e: Exception) {
                    RestoreUiState(error = describe(e))
                }
                else -> RestoreUiState(error = "Reconnect Google Drive in Backup settings first.")
            }
        }
    }

    fun resetRestore() {
        restoreFile = null
        coordinator.discardRestoreFiles()
        restoreState.value = RestoreUiState()
    }

    fun restore(passphrase: String, mode: RestoreMode) {
        val file = restoreFile ?: return
        if (restoreState.value.working) return
        restoreState.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            val chars = passphrase.toCharArray()
            try {
                val manifest = coordinator.restore(file, chars, mode)
                coordinator.discardRestoreFiles()
                restoreFile = null
                restoreState.value = RestoreUiState(restoredItems = manifest.itemCount)
            } catch (e: Exception) {
                restoreState.update { it.copy(working = false, error = describe(e)) }
            } finally {
                chars.fill('\u0000')
            }
        }
    }

    override fun onCleared() {
        coordinator.discardRestoreFiles()
    }

    private fun describe(e: Exception): String = when (e) {
        is WrongBackupPassphraseException -> "That backup passphrase is not correct."
        is NotABackupException -> "That file is not a MyVault backup."
        is UnsupportedBackupException -> "This backup was made by a newer version of MyVault. Update the app to restore it."
        is CorruptedBackupException -> "This backup is damaged or incomplete, so nothing was restored."
        is BackupException -> "Set a backup passphrase first."
        is DriveException -> e.message ?: "Google Drive is unavailable."
        is VaultLockedException -> "The vault locked before this finished. Unlock and try again."
        else -> "Something went wrong. Nothing was changed."
    }
}
