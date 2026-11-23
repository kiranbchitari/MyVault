package com.myvault.app.backup

import com.myvault.app.data.db.BackupMetadataEntity
import com.myvault.app.vault.MutableClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.SecureRandom

class DriveBackupTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val drive = FakeDrive()
    private val client = DriveClient(token = { "test-token" }, baseUrl = drive.baseUrl)
    private val dao = FakeBackupMetadataDao()
    private val clock = MutableClock()

    @After
    fun stop() = drive.close()

    private fun archive(size: Int = 200_000): File =
        temp.newFile().apply { writeBytes(ByteArray(size).also(SecureRandom()::nextBytes)) }

    private suspend fun queue(file: File, createdAt: Long = clock.now, name: String = "MyVault_Backup_2026-11-21_1000.vault"): Long = dao.insert(
        BackupMetadataEntity(
            createdAt = createdAt, fileName = name, sizeBytes = file.length(),
            destination = BackupDestination.GOOGLE_DRIVE, status = BackupStatus.PENDING, localPath = file.absolutePath,
        ),
    )

    private suspend fun expectDriveError(block: suspend () -> Unit): DriveException {
        try {
            block()
        } catch (e: DriveException) {
            return e
        }
        fail("expected DriveException")
        throw IllegalStateException()
    }

    @Test
    fun `backups go in a MyVault Backups folder at the top of My Drive, created once and then reused`() = runBlocking {
        val first = client.ensureFolder()
        val second = client.ensureFolder()

        assertEquals(first, second)
        assertEquals(1, drive.files.values.count { it.folder })
        val folder = drive.files.getValue(first)
        assertEquals("MyVault Backups", folder.name)
        assertNull("no parent folder is created", folder.parent)
    }

    @Test
    fun `the folder is still found after the user moves it into a folder of their own`() = runBlocking {
        val folder = client.ensureFolder()
        drive.move(folder, parent = "users-own-folder")

        assertEquals(folder, client.ensureFolder())
        assertEquals(folder, client.findBackupFolder())
        assertEquals(1, drive.files.values.count { it.folder })
    }

    @Test
    fun `with two MyVault Backups folders the older one keeps being used`() = runBlocking {
        val older = client.ensureFolder()
        drive.files["newer"] = FakeDrive.Stored("newer", "MyVault Backups", null, true, ByteArray(0), Int.MAX_VALUE)

        assertEquals(older, client.ensureFolder())
    }

    @Test
    fun `looking for backups before the first upload creates nothing`() = runBlocking {
        assertNull(client.findBackupFolder())
        assertTrue(drive.files.isEmpty())

        val folder = client.ensureFolder()
        assertEquals(folder, client.findBackupFolder())
    }

    @Test
    fun `every backup is listed newest first, across pages`() = runBlocking {
        val folder = client.ensureFolder()
        val names = (1..8).map { drive.seed(folder, "MyVault_Backup_$it.vault").name }
        drive.maxPageSize = 3
        drive.requests.clear()

        val listed = client.list(folder).map { it.name }

        assertEquals(names.reversed(), listed)
        assertEquals("three pages of 3, 3 and 2", 3, drive.requests.size)
    }

    @Test
    fun `a backup name with quotes and backslashes is sent intact`() = runBlocking {
        val name = "My \"vault\" \\ backup.vault"
        val uploaded = client.upload(client.ensureFolder(), name, archive(1_000))

        assertEquals(name, drive.files.getValue(uploaded.id).name)
        assertEquals(name, uploaded.name)
    }

    @Test
    fun `an upload arrives intact, is verified, listed and downloadable`() = runBlocking {
        val file = archive()
        val folder = client.ensureFolder()
        val progress = mutableListOf<Long>()

        val uploaded = client.upload(folder, "MyVault_Backup_2026-11-21_1432.vault", file) { sent, total ->
            assertEquals(file.length(), total)
            progress += sent
        }

        assertEquals(file.length(), uploaded.sizeBytes)
        assertArrayEquals(file.readBytes(), drive.files.getValue(uploaded.id).bytes)
        assertEquals(file.length(), progress.last())
        assertEquals(progress.sorted(), progress)
        assertEquals(listOf(uploaded.id), client.list(folder).map { it.id })

        val downloaded = temp.newFile()
        client.download(uploaded.id, downloaded)
        assertArrayEquals(file.readBytes(), downloaded.readBytes())
    }

    @Test
    fun `every request carries the bearer token and stays on the Drive host`() = runBlocking {
        val folder = client.ensureFolder()
        val uploaded = client.upload(folder, "a.vault", archive(10_000))
        client.list(folder)
        client.delete(uploaded.id)

        assertTrue(drive.authorizationHeaders.isNotEmpty())
        assertTrue(drive.authorizationHeaders.all { it == "Bearer test-token" })
        assertTrue(drive.requests.all { it.contains("/drive/v3/files") })
        assertFalse(drive.files.containsKey(uploaded.id))
    }

    @Test
    fun `an upload that does not match what was sent is rejected and removed`() = runBlocking {
        val folder = client.ensureFolder()
        drive.corruptUploads = true

        val error = expectDriveError { client.upload(folder, "a.vault", archive()) }

        assertTrue(error.retryable)
        assertTrue("the bad copy must not be left in Drive", drive.files.values.none { !it.folder })
    }

    @Test
    fun `errors are classified so the right ones are retried`() = runBlocking {
        drive.failures += 503
        assertTrue(expectDriveError { client.ensureFolder() }.retryable)
        drive.failures += 429
        assertTrue(expectDriveError { client.ensureFolder() }.retryable)
        drive.failures += 401
        val expired = expectDriveError { client.ensureFolder() }
        assertTrue(expired.authExpired)
        assertFalse(expired.retryable)
        drive.failures += 400
        assertFalse(expectDriveError { client.ensureFolder() }.retryable)

        val unreachable = DriveClient(token = { "t" }, baseUrl = "http://127.0.0.1:1")
        assertTrue(expectDriveError { unreachable.ensureFolder() }.retryable)
    }

    @Test
    fun `a queued backup is uploaded, recorded and its local copy removed`() = runBlocking {
        val file = archive()
        val contents = file.readBytes()
        val id = queue(file)
        clock.now += 5_000

        assertEquals(UploadOutcome.DONE, DriveUploader(dao, clock).uploadAll(client))

        val record = dao.get(id)!!
        assertEquals(BackupStatus.COMPLETED, record.status)
        assertEquals(clock.now, record.completedAt)
        assertNull(record.localPath)
        assertNull(record.error)
        assertArrayEquals(contents, drive.files.getValue(record.remoteId!!).bytes)
        assertFalse("the uploaded archive should be removed from app storage", file.exists())
    }

    @Test
    fun `a failed upload is recorded, kept and succeeds when retried`() = runBlocking {
        val file = archive()
        val id = queue(file)
        val uploader = DriveUploader(dao, clock)

        // Drive is unavailable for the first request of the pass.
        drive.failures += 503
        assertEquals(UploadOutcome.RETRY_LATER, uploader.uploadAll(client))
        assertEquals(BackupStatus.FAILED, dao.get(id)!!.status)
        assertTrue(dao.get(id)!!.error!!.isNotBlank())
        assertTrue("the archive must be kept for the retry", file.exists())
        assertEquals(1, dao.awaitingUpload().size)

        assertEquals(UploadOutcome.DONE, uploader.uploadAll(client))
        assertEquals(BackupStatus.COMPLETED, dao.get(id)!!.status)
        assertFalse(file.exists())
    }

    @Test
    fun `expired access stops the pass and asks for a reconnect`() = runBlocking {
        val id = queue(archive())
        drive.failures += 401

        assertEquals(UploadOutcome.NEEDS_RECONNECT, DriveUploader(dao, clock).uploadAll(client))

        assertEquals(BackupStatus.FAILED, dao.get(id)!!.status)
        assertTrue(drive.files.values.none { !it.folder })
    }

    @Test
    fun `a missing archive is reported instead of retried forever`() = runBlocking {
        val file = archive()
        val id = queue(file)
        file.delete()

        assertEquals(UploadOutcome.DONE, DriveUploader(dao, clock).uploadAll(client))

        assertEquals(BackupStatus.FAILED, dao.get(id)!!.status)
        assertTrue(dao.awaitingUpload().isEmpty())
    }

    @Test
    fun `only the newest versions are kept in Drive`() = runBlocking {
        val uploader = DriveUploader(dao, clock, keepVersions = 3)
        repeat(5) { index ->
            queue(archive(5_000), createdAt = clock.now + index, name = "MyVault_Backup_2026-11-2${index}_1000.vault")
            assertEquals(UploadOutcome.DONE, uploader.uploadAll(client))
        }
        val remaining = drive.files.values.filterNot { it.folder }.map { it.name }
        assertEquals(
            listOf("MyVault_Backup_2026-11-22_1000.vault", "MyVault_Backup_2026-11-23_1000.vault", "MyVault_Backup_2026-11-24_1000.vault"),
            remaining.sorted(),
        )
    }

    @Test
    fun `backup health reflects the real state of backups`() {
        fun row(status: String, at: Long) = BackupMetadataEntity(
            createdAt = at, fileName = "f", sizeBytes = 1, destination = BackupDestination.LOCAL_FILE, status = status,
        )
        val day = 24L * 60 * 60 * 1000
        assertEquals(BackupHealth.NOT_SET_UP, BackupHealth.evaluate(false, emptyList(), 0, 0))
        assertEquals(BackupHealth.NEVER, BackupHealth.evaluate(true, emptyList(), 0, 0))
        assertEquals(BackupHealth.GOOD, BackupHealth.evaluate(true, listOf(row(BackupStatus.COMPLETED, 100)), 50, 200))
        assertEquals(BackupHealth.OUT_OF_DATE, BackupHealth.evaluate(true, listOf(row(BackupStatus.COMPLETED, 100)), 150, 200))
        assertEquals(BackupHealth.STALE, BackupHealth.evaluate(true, listOf(row(BackupStatus.COMPLETED, 100)), 50, 100 + 15 * day))
        assertEquals(
            BackupHealth.FAILED,
            BackupHealth.evaluate(true, listOf(row(BackupStatus.COMPLETED, 100), row(BackupStatus.FAILED, 300)), 50, 400),
        )
        assertEquals(BackupHealth.NEVER, BackupHealth.evaluate(true, listOf(row(BackupStatus.PENDING, 100)), 50, 200))
    }
}
