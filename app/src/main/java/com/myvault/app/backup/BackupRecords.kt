package com.myvault.app.backup

import com.myvault.app.data.db.BackupMetadataDao
import com.myvault.app.data.db.BackupMetadataEntity
import java.io.File
import java.time.Clock

object BackupDestination {
    const val LOCAL_FILE = "LOCAL_FILE"
    const val GOOGLE_DRIVE = "GOOGLE_DRIVE"
}

object BackupStatus {
    const val PENDING = "PENDING"
    const val UPLOADING = "UPLOADING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
}

enum class BackupHealth(val title: String) {
    NOT_SET_UP("Backups are not set up"),
    NEVER("No backup yet"),
    FAILED("Last backup failed"),
    OUT_OF_DATE("Changes not backed up"),
    STALE("Backup is getting old"),
    GOOD("Backed up");

    companion object {
        private const val STALE_AFTER_MS = 14L * 24 * 60 * 60 * 1000

        fun evaluate(configured: Boolean, history: List<BackupMetadataEntity>, vaultChangedAt: Long, now: Long): BackupHealth {
            if (!configured) return NOT_SET_UP
            val lastSuccess = history.filter { it.status == BackupStatus.COMPLETED }.maxOfOrNull { it.createdAt }
            val latest = history.maxByOrNull { it.createdAt }
            return when {
                latest?.status == BackupStatus.FAILED -> FAILED
                lastSuccess == null -> NEVER
                vaultChangedAt > lastSuccess -> OUT_OF_DATE
                now - lastSuccess > STALE_AFTER_MS -> STALE
                else -> GOOD
            }
        }
    }
}

/** Result of one pass over the backups waiting to be uploaded. */
enum class UploadOutcome { DONE, RETRY_LATER, NEEDS_RECONNECT }

/**
 * Uploads queued backup archives to Google Drive and keeps the history rows truthful about what
 * actually happened. Contains no Android code, so it is tested against a local Drive stand-in.
 */
class DriveUploader(private val dao: BackupMetadataDao, private val clock: Clock, private val keepVersions: Int = KEEP_VERSIONS) {

    suspend fun uploadAll(client: DriveClient, onProgress: (recordId: Long, sent: Long, total: Long) -> Unit = { _, _, _ -> }): UploadOutcome {
        val pending = dao.awaitingUpload()
        if (pending.isEmpty()) return UploadOutcome.DONE
        var outcome = UploadOutcome.DONE
        val folderId = try {
            client.ensureFolder()
        } catch (e: DriveException) {
            pending.forEach { dao.update(it.copy(status = BackupStatus.FAILED, error = e.message)) }
            return if (e.authExpired) UploadOutcome.NEEDS_RECONNECT else UploadOutcome.RETRY_LATER
        }
        for (record in pending) {
            val file = record.localPath?.let(::File)
            if (file == null || !file.isFile) {
                dao.update(record.copy(status = BackupStatus.FAILED, error = "The prepared backup file is missing", localPath = null))
                continue
            }
            dao.update(record.copy(status = BackupStatus.UPLOADING, error = null))
            try {
                val remote = client.upload(folderId, record.fileName, file) { sent, total -> onProgress(record.id, sent, total) }
                dao.update(
                    record.copy(status = BackupStatus.COMPLETED, completedAt = clock.millis(), remoteId = remote.id, localPath = null, error = null),
                )
                file.delete()
            } catch (e: DriveException) {
                dao.update(record.copy(status = BackupStatus.FAILED, error = e.message))
                outcome = when {
                    e.authExpired -> return UploadOutcome.NEEDS_RECONNECT
                    e.retryable -> UploadOutcome.RETRY_LATER
                    else -> outcome
                }
            }
        }
        if (outcome == UploadOutcome.DONE) runCatching { prune(client, folderId) }
        return outcome
    }

    /** Keeps the newest [keepVersions] backups in Drive and removes older ones. */
    private suspend fun prune(client: DriveClient, folderId: String) {
        client.list(folderId).filter { it.name.endsWith(BackupFormat.EXTENSION) }.drop(keepVersions).forEach { client.delete(it.id) }
    }

    companion object {
        const val KEEP_VERSIONS = 10
    }
}
