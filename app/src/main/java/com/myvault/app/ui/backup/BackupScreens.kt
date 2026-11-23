package com.myvault.app.ui.backup

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvault.app.backup.BackupDestination
import com.myvault.app.backup.BackupFormat
import com.myvault.app.backup.BackupHealth
import com.myvault.app.backup.BackupStatus
import com.myvault.app.backup.DriveAuthorization
import com.myvault.app.backup.RestoreMode
import com.myvault.app.ui.components.ChoiceChips
import com.myvault.app.ui.components.ConfirmDialog
import com.myvault.app.ui.components.EmptyState
import com.myvault.app.ui.components.ErrorText
import com.myvault.app.ui.components.LocalExpectExternalFlow
import com.myvault.app.ui.components.PassphraseDialog
import com.myvault.app.ui.components.PassphraseField
import com.myvault.app.ui.components.PrimaryButton
import com.myvault.app.ui.components.QuietButton
import com.myvault.app.ui.components.ScreenColumn
import com.myvault.app.ui.components.SettingRow
import com.myvault.app.ui.components.VaultCard
import com.myvault.app.ui.components.VaultSwitch
import com.myvault.app.ui.theme.VaultTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val stamp = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

private fun formatStamp(ms: Long): String = stamp.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes bytes"
}

@Composable
private fun ActionMessages(state: BackupActionState) {
    state.message?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = VaultTheme.palette.success, modifier = Modifier.padding(top = 10.dp).testTag("backup-message"))
    }
    ErrorText(state.error, Modifier.padding(top = 10.dp))
}

@Composable
fun BackupSettingsScreen(onBack: () -> Unit, onDrive: () -> Unit, onHistory: () -> Unit, onRestore: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val action by viewModel.actionState.collectAsStateWithLifecycle()
    val expectExternalFlow = LocalExpectExternalFlow.current
    val palette = VaultTheme.palette
    var settingPassphrase by remember { mutableStateOf(false) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }

    ScreenColumn(title = "Backup", modifier = Modifier.testTag("backup-settings"), onBack = onBack) {
        VaultCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.health == BackupHealth.GOOD) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = if (state.health == BackupHealth.GOOD) palette.success else palette.warning,
                )
                Spacer(Modifier.width(12.dp))
                Text(state.health.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("backup-health"))
            }
            Spacer(Modifier.height(8.dp))
            val last = state.lastSuccessful
            Text(
                text = if (last != null) "Last successful backup ${formatStamp(last.createdAt)} · ${formatSize(last.sizeBytes)}" else "No backup has been completed on this device yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))

        if (!state.configured) {
            VaultCard {
                Text("Set a backup passphrase", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Backups are encrypted with their own passphrase, separate from your master passphrase. With it, a backup can be opened on any phone. Without it, a backup cannot be opened by anyone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Set backup passphrase", { settingPassphrase = true }, modifier = Modifier.testTag("set-backup-passphrase"))
            }
        } else {
            VaultCard {
                Text("Back up now", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${state.itemCount} ${if (state.itemCount == 1) "item" else "items"} will be encrypted into a single .vault file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = "Save to a file",
                    onClick = {
                        viewModel.clearMessages()
                        expectExternalFlow()
                        createFile.launch(viewModel.suggestedFileName())
                    },
                    loading = action.busy,
                    modifier = Modifier.testTag("backup-to-file"),
                )
                if (state.settings.driveConnected) {
                    QuietButton("Upload to Google Drive", viewModel::backupToDrive, enabled = !action.busy, modifier = Modifier.testTag("backup-to-drive"))
                }
                if (state.upload.running) {
                    Spacer(Modifier.height(8.dp))
                    if (state.upload.total > 0) {
                        LinearProgressIndicator(
                            progress = { state.upload.sent.toFloat() / state.upload.total },
                            modifier = Modifier.fillMaxWidth(),
                            color = palette.accent,
                        )
                        Text("Uploading ${formatSize(state.upload.sent)} of ${formatSize(state.upload.total)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
                    }
                } else if (state.upload.queued) {
                    Text("An upload is queued and will start when the phone is online.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ActionMessages(action)
            }
        }
        Spacer(Modifier.height(14.dp))

        VaultCard {
            SettingRow(
                title = "Google Drive",
                subtitle = if (state.settings.driveConnected) "Connected" else "Not connected · optional",
                icon = Icons.Rounded.Cloud,
                iconColor = Color(0xFF1F7AFF),
                onClick = onDrive,
                modifier = Modifier.testTag("row-drive"),
            )
            HorizontalDivider(thickness = 0.7.dp, color = palette.cardBorder)
            SettingRow(
                title = "Automatic backup",
                subtitle = if (state.settings.driveConnected) "Uploads a new backup after you make changes" else "Needs Google Drive",
                trailing = {
                    VaultSwitch(
                        checked = state.settings.autoBackup,
                        enabled = state.settings.driveConnected && state.configured,
                        onCheckedChange = viewModel::setAutoBackup,
                    )
                },
            )
            HorizontalDivider(thickness = 0.7.dp, color = palette.cardBorder)
            SettingRow("Backup history", icon = Icons.Rounded.History, iconColor = Color(0xFF8B5CF6), subtitle = "${state.history.size} recorded", onClick = onHistory, modifier = Modifier.testTag("row-history"))
            HorizontalDivider(thickness = 0.7.dp, color = palette.cardBorder)
            SettingRow("Restore from a backup", icon = Icons.Rounded.Restore, iconColor = Color(0xFF34C759), onClick = onRestore, modifier = Modifier.testTag("row-restore"))
            if (state.configured) {
                HorizontalDivider(thickness = 0.7.dp, color = palette.cardBorder)
                SettingRow("Change backup passphrase", icon = Icons.Rounded.Key, iconColor = Color(0xFFFF9F0A), subtitle = "Applies to new backups only", onClick = { settingPassphrase = true })
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Keep the backup passphrase somewhere safe. If you lose it, your backups can never be opened: there is no reset.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.warning,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (settingPassphrase) {
        PassphraseDialog(
            title = if (state.configured) "Change backup passphrase" else "Set backup passphrase",
            body = "Choose a passphrase you will still know on a new phone. Backups you already made keep the passphrase they were made with.",
            confirmLabel = "Save",
            askCurrent = false,
            confirmNew = true,
            newLabel = "Backup passphrase",
            working = action.busy,
            error = action.error,
            onDismiss = { settingPassphrase = false },
            onSubmit = { _, new -> viewModel.setBackupPassphrase(new) { settingPassphrase = false } },
        )
    }
}

@Composable
fun DriveConnectionScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val action by viewModel.actionState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val expectExternalFlow = LocalExpectExternalFlow.current
    val palette = VaultTheme.palette
    var confirmDisconnect by remember { mutableStateOf(false) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.driveConsentResult(result.data)
    }

    ScreenColumn(title = "Google Drive", modifier = Modifier.testTag("drive-connection"), onBack = onBack) {
        VaultCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.settings.driveConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = if (state.settings.driveConnected) palette.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (state.settings.driveConnected) "Connected" else "Not connected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("drive-status"),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Google Drive is optional. MyVault works fully without it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        VaultCard {
            Text("What connecting does", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "• Backups are encrypted on this phone before upload. Google only ever receives an unreadable .vault file.\n" +
                    "• MyVault asks for the narrowest Drive permission: it can see and manage only the files it created, in a \"${com.myvault.app.backup.DriveClient.FOLDER_NAME}\" folder in My Drive. It cannot read anything else in your Drive.\n" +
                    "• Your Google password is never seen or stored by MyVault.\n" +
                    "• The newest ${com.myvault.app.backup.DriveUploader.KEEP_VERSIONS} backups are kept; older ones are removed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(18.dp))
        if (state.settings.driveConnected) {
            QuietButton("Disconnect Google Drive", { confirmDisconnect = true }, modifier = Modifier.testTag("drive-disconnect"))
        } else {
            PrimaryButton(
                text = "Connect Google Drive",
                loading = action.busy,
                modifier = Modifier.testTag("drive-connect"),
                onClick = {
                    viewModel.clearMessages()
                    scope.launch {
                        when (val result = viewModel.authorizeDrive()) {
                            is DriveAuthorization.Granted -> viewModel.driveAuthorized()
                            is DriveAuthorization.ConsentRequired -> {
                                expectExternalFlow()
                                consent.launch(IntentSenderRequest.Builder(result.consent.intentSender).build())
                            }
                            is DriveAuthorization.Failed -> viewModel.driveFailed(result.message)
                        }
                    }
                },
            )
        }
        ActionMessages(action)
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Disconnect Google Drive?",
            body = "Automatic backups will stop. Backups already in your Drive stay there until you delete them.",
            confirmLabel = "Disconnect",
            onDismiss = { confirmDisconnect = false },
            onConfirm = {
                confirmDisconnect = false
                viewModel.disconnectDrive()
            },
        )
    }
}

@Composable
fun BackupHistoryScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = VaultTheme.palette
    ScreenColumn(title = "Backup history", modifier = Modifier.testTag("backup-history"), onBack = onBack, scroll = false) {
        if (state.history.isEmpty()) {
            EmptyState(Icons.Rounded.History, "No backups yet", "Each backup you make from this phone is listed here with its real status.")
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.history, key = { it.id }) { record ->
                    VaultCard(Modifier.testTag("history-row")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (record.destination == BackupDestination.GOOGLE_DRIVE) Icons.Rounded.Cloud else Icons.Rounded.Description,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(record.fileName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${formatStamp(record.createdAt)} · ${formatSize(record.sizeBytes)} · ${if (record.destination == BackupDestination.GOOGLE_DRIVE) "Google Drive" else "Saved to file"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.deleteRecord(record.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove from history", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val (label, colour) = when (record.status) {
                            BackupStatus.COMPLETED -> "Completed" to palette.success
                            BackupStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
                            BackupStatus.UPLOADING -> "Uploading" to palette.accent
                            else -> "Waiting to upload" to palette.warning
                        }
                        Text(label, style = MaterialTheme.typography.labelMedium, color = colour)
                        record.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (record.status == BackupStatus.FAILED && record.localPath != null) {
                            QuietButton("Retry upload", viewModel::retryUploads)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RestoreScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val restore by viewModel.restore.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val expectExternalFlow = LocalExpectExternalFlow.current
    val palette = VaultTheme.palette
    // Plain remember: the backup passphrase never reaches saved instance state.
    var passphrase by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    var confirmReplace by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetRestore() }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Selected file"
            viewModel.pickedBackupFile(uri, name)
        }
    }

    ScreenColumn(title = "Restore", modifier = Modifier.testTag("restore"), onBack = onBack) {
        when {
            restore.restoredItems != null -> {
                VaultCard {
                    Text("Restore complete", style = MaterialTheme.typography.titleMedium, color = palette.success, modifier = Modifier.testTag("restore-done"))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${restore.restoredItems} ${if (restore.restoredItems == 1) "item was" else "items were"} decrypted, checked and added to your vault.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(18.dp))
                PrimaryButton("Go to my vault", onDone)
            }

            restore.selectedName != null -> {
                VaultCard {
                    Text("Backup selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(restore.selectedName.orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("restore-file"))
                    Text("Recognised as a MyVault backup.", style = MaterialTheme.typography.bodySmall, color = palette.success)
                }
                Spacer(Modifier.height(16.dp))
                PassphraseField(passphrase, { passphrase = it }, "Backup passphrase", enabled = !restore.working)
                Spacer(Modifier.height(16.dp))
                if (state.itemCount > 0) {
                    VaultCard {
                        Text("Your vault already has ${state.itemCount} ${if (state.itemCount == 1) "item" else "items"}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(10.dp))
                        ChoiceChips(
                            options = listOf(RestoreMode.MERGE, RestoreMode.REPLACE),
                            selected = mode,
                            label = { if (it == RestoreMode.MERGE) "Merge" else "Replace all" },
                            onSelected = { mode = it },
                            tagPrefix = "restore-mode",
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (mode == RestoreMode.MERGE) {
                                "Keeps everything you have. Items in the backup are added, and replace items that are the same record."
                            } else {
                                "Deletes everything currently in your vault and loads only what is in the backup."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (mode == RestoreMode.REPLACE) palette.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                ErrorText(restore.error, Modifier.padding(bottom = 10.dp))
                PrimaryButton(
                    text = if (restore.working) "Decrypting and checking…" else "Restore",
                    enabled = passphrase.isNotEmpty(),
                    loading = restore.working,
                    modifier = Modifier.testTag("restore-confirm"),
                    onClick = { if (mode == RestoreMode.REPLACE && state.itemCount > 0) confirmReplace = true else viewModel.restore(passphrase, mode) },
                )
                QuietButton("Choose a different backup", {
                    passphrase = ""
                    viewModel.resetRestore()
                }, enabled = !restore.working)
            }

            else -> {
                Text(
                    "Choose a .vault backup. It stays encrypted until you enter its backup passphrase, and it is fully checked before anything in your vault changes.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    text = "Choose a backup file",
                    loading = restore.working,
                    modifier = Modifier.testTag("restore-pick-file"),
                    onClick = {
                        expectExternalFlow()
                        openFile.launch(arrayOf("*/*"))
                    },
                )
                if (state.settings.driveConnected) {
                    QuietButton("Choose from Google Drive", viewModel::loadDriveBackups, enabled = !restore.loadingDrive)
                }
                ErrorText(restore.error, Modifier.padding(top = 10.dp))
                if (restore.loadingDrive) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
                }
                restore.driveFiles?.let { files ->
                    Spacer(Modifier.height(12.dp))
                    if (files.isEmpty()) {
                        Text("No MyVault backups were found in your Google Drive.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        VaultCard {
                            files.forEachIndexed { index, file ->
                                if (index > 0) HorizontalDivider(thickness = 0.7.dp, color = palette.cardBorder)
                                SettingRow(file.name, subtitle = formatSize(file.sizeBytes), icon = Icons.Rounded.Cloud, onClick = { viewModel.chooseDriveBackup(file) })
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmReplace) {
        ConfirmDialog(
            title = "Replace your whole vault?",
            body = "All ${state.itemCount} items currently in your vault will be deleted and replaced by the contents of this backup. This cannot be undone.",
            confirmLabel = "Replace everything",
            destructive = true,
            onDismiss = { confirmReplace = false },
            onConfirm = {
                confirmReplace = false
                viewModel.restore(passphrase, RestoreMode.REPLACE)
            },
        )
    }
}
