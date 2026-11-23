package com.myvault.app.backup

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.myvault.app.data.SecuritySettingsRepository
import com.myvault.app.data.db.BackupMetadataDao
import com.myvault.app.data.db.BackupMetadataEntity
import com.myvault.app.di.ApplicationScope
import com.myvault.app.di.IoDispatcher
import com.myvault.app.security.VaultSession
import com.myvault.app.vault.VaultRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class UploadProgress(val running: Boolean = false, val queued: Boolean = false, val sent: Long = 0, val total: Long = 0)

/**
 * Everything around a backup that touches Android: files, the system file picker, Google Drive
 * and background uploads.
 *
 * Archives can only be *created* while the vault is unlocked, because that is the only time the
 * keys exist. Uploading does not need the keys, so it is handed to WorkManager, which runs it
 * when the network is available and retries with backoff. An upload is therefore queued, never
 * assumed to have happened: the history shows each backup's real state.
 */
@Singleton
class BackupCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backups: BackupManager,
    private val backupKeys: BackupKeyManager,
    private val dao: BackupMetadataDao,
    private val settings: SecuritySettingsRepository,
    private val repository: VaultRepository,
    private val session: VaultSession,
    private val authorizer: DriveAuthorizer,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    val history: Flow<List<BackupMetadataEntity>> = dao.observe()

    val uploadProgress: Flow<UploadProgress>
        get() = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UPLOAD_WORK).map { infos ->
            val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
            UploadProgress(
                running = running != null,
                queued = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED },
                sent = running?.progress?.getLong(KEY_SENT, 0) ?: 0,
                total = running?.progress?.getLong(KEY_TOTAL, 0) ?: 0,
            )
        }

    private val pendingDir: File get() = File(context.filesDir, "backups").apply { mkdirs() }
    private val restoreDir: File get() = File(context.cacheDir, "restore").apply { mkdirs() }

    @OptIn(FlowPreview::class)
    fun start() {
        // Remember when the vault last changed, so backup health can tell if a backup is stale.
        scope.launch { repository.changes.collect { settings.markVaultChanged(clock.millis()) } }
        // Automatic backup: shortly after edits settle, and when unlocking with changes outstanding.
        scope.launch { repository.changes.debounce(AUTO_BACKUP_DELAY_MS).collect { runCatching { autoBackupIfDue() } } }
        session.onUnlock {
            scope.launch {
                runCatching {
                    repository.awaitLoaded()
                    autoBackupIfDue()
                }
            }
        }
    }

    private suspend fun autoBackupIfDue() {
        val current = settings.current()
        if (!current.autoBackup || !current.driveConnected || !session.isUnlocked.value || !backupKeys.isConfigured()) return
        val latestDrive = dao.observe().first().filter { it.destination == BackupDestination.GOOGLE_DRIVE }.maxOfOrNull { it.createdAt } ?: 0
        if (current.vaultChangedAt > latestDrive) queueDriveBackup()
    }

    /** Writes a backup to a file the user chose in the system picker, then reads it back to verify it. */
    suspend fun exportTo(uri: Uri): BackupSummary = withContext(io) {
        val resolver = context.contentResolver
        val summary = resolver.openOutputStream(uri, "wt")?.use { backups.write(it) } ?: throw java.io.IOException("Could not open the file")
        try {
            resolver.openInputStream(uri)?.use { backups.verify(it) } ?: throw CorruptedBackupException()
        } catch (e: Exception) {
            dao.insert(record(summary, BackupDestination.LOCAL_FILE, BackupStatus.FAILED, error = "The saved file could not be verified"))
            throw e
        }
        dao.insert(record(summary, BackupDestination.LOCAL_FILE, BackupStatus.COMPLETED, completedAt = clock.millis()))
        summary
    }

    /** Creates and verifies an encrypted archive in app storage, then queues it for upload. */
    suspend fun queueDriveBackup(): BackupSummary = withContext(io) {
        val file = File(pendingDir, "pending-${clock.millis()}${BackupFormat.EXTENSION}")
        val summary = try {
            file.outputStream().use { backups.write(it) }.also { file.inputStream().use { input -> backups.verify(input) } }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        dao.insert(record(summary, BackupDestination.GOOGLE_DRIVE, BackupStatus.PENDING, localPath = file.absolutePath))
        enqueueUpload()
        summary
    }

    /** Queues an upload pass now, and a daily sweep that picks up anything still waiting. */
    fun enqueueUpload() {
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            UPLOAD_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<BackupUploadWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            SWEEP_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupUploadWorker>(1, TimeUnit.DAYS).setConstraints(online).build(),
        )
    }

    suspend fun connectDrive() = settings.setDriveConnected(true)

    /** Stops using Drive on this device. Backups already in Drive are left where they are. */
    suspend fun disconnectDrive() {
        settings.setDriveConnected(false)
        WorkManager.getInstance(context).cancelUniqueWork(UPLOAD_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(SWEEP_WORK)
        (authorizer.authorize() as? DriveAuthorization.Granted)?.let { authorizer.clearToken(it.accessToken) }
    }

    suspend fun listDriveBackups(token: String): List<DriveFile> {
        val client = DriveClient({ token })
        val folder = client.findBackupFolder() ?: return emptyList()
        return client.list(folder).filter { it.name.endsWith(BackupFormat.EXTENSION) }
    }

    suspend fun downloadDriveBackup(token: String, fileId: String): File = withContext(io) {
        File(restoreDir, "download${BackupFormat.EXTENSION}").also { DriveClient({ token }).download(fileId, it) }
    }

    /** Copies a picked backup into the cache (it is still encrypted) so it can be read more than once. */
    suspend fun copyForRestore(uri: Uri): File = withContext(io) {
        val target = File(restoreDir, "picked${BackupFormat.EXTENSION}")
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: throw java.io.IOException("Could not open the file")
        target
    }

    suspend fun inspect(file: File): BackupHeader = file.inputStream().use { backups.readHeader(it) }

    suspend fun restore(file: File, passphrase: CharArray, mode: RestoreMode): BackupManifest =
        backups.restore({ file.inputStream() }, passphrase, mode)

    fun discardRestoreFiles() {
        restoreDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun deleteRecord(id: Long) = withContext(io) {
        dao.get(id)?.localPath?.let { File(it).delete() }
        dao.delete(id)
    }

    /** One upload pass, called by the worker. */
    suspend fun uploadPending(onProgress: (sent: Long, total: Long) -> Unit): UploadOutcome {
        if (dao.awaitingUpload().isEmpty()) return UploadOutcome.DONE
        return when (val authorization = authorizer.authorize()) {
            is DriveAuthorization.Granted -> {
                val outcome = DriveUploader(dao, clock).uploadAll(DriveClient({ authorization.accessToken })) { _, sent, total -> onProgress(sent, total) }
                if (outcome == UploadOutcome.NEEDS_RECONNECT) authorizer.clearToken(authorization.accessToken)
                outcome
            }
            is DriveAuthorization.ConsentRequired -> {
                dao.awaitingUpload().forEach { dao.update(it.copy(status = BackupStatus.FAILED, error = "Reconnect Google Drive to upload this backup")) }
                UploadOutcome.NEEDS_RECONNECT
            }
            is DriveAuthorization.Failed -> {
                dao.awaitingUpload().forEach { dao.update(it.copy(status = BackupStatus.FAILED, error = authorization.message)) }
                UploadOutcome.RETRY_LATER
            }
        }
    }

    private fun record(
        summary: BackupSummary,
        destination: String,
        status: String,
        completedAt: Long? = null,
        localPath: String? = null,
        error: String? = null,
    ) = BackupMetadataEntity(
        createdAt = summary.createdAt,
        fileName = summary.fileName,
        sizeBytes = summary.sizeBytes,
        destination = destination,
        status = status,
        completedAt = completedAt,
        localPath = localPath,
        error = error,
    )

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun backupCoordinator(): BackupCoordinator
    }

    companion object {
        const val UPLOAD_WORK = "myvault-drive-upload"
        const val SWEEP_WORK = "myvault-drive-sweep"
        const val KEY_SENT = "sent"
        const val KEY_TOTAL = "total"
        private const val AUTO_BACKUP_DELAY_MS = 8_000L
    }
}

/** Uploads queued, already-encrypted backups. It never has access to vault keys. */
class BackupUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val coordinator = EntryPointAccessors.fromApplication(applicationContext, BackupCoordinator.WorkerEntryPoint::class.java).backupCoordinator()
        val outcome = coordinator.uploadPending { sent, total ->
            setProgressAsync(workDataOf(BackupCoordinator.KEY_SENT to sent, BackupCoordinator.KEY_TOTAL to total))
        }
        return when (outcome) {
            UploadOutcome.DONE -> Result.success()
            UploadOutcome.RETRY_LATER -> if (runAttemptCount < 8) Result.retry() else Result.failure()
            UploadOutcome.NEEDS_RECONNECT -> Result.failure()
        }
    }
}
