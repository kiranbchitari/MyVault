package com.myvault.app.backup

import com.myvault.app.security.VaultLockedException
import com.myvault.app.security.contains
import com.myvault.app.vault.SampleItems
import com.myvault.app.vault.TestVault
import com.myvault.app.vault.VaultRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.time.ZoneOffset

class BackupManagerTest {
    private val scan = ByteArray(2 * VaultRepository.CHUNK_BYTES + 4_321).also(SecureRandom()::nextBytes)
    private val photo = "small-plaintext-photo-bytes".toByteArray()

    /** A device with a vault, a backup passphrase, every sample record and two attachments. */
    private suspend fun TestScope.seededVault(): TestVault {
        val vault = TestVault(this)
        vault.create()
        vault.backupKeys.setPassphrase(TestVault.BACKUP.toCharArray())
        SampleItems.all.forEach { vault.repository.save(it) }
        vault.repository.addAttachment("i1", "passport-scan.png", "image/png", scan)
        vault.repository.addAttachment("i1", "photo.jpg", "image/jpeg", photo)
        return vault
    }

    private suspend fun TestVault.export(): ByteArray = ByteArrayOutputStream().also { backups.write(it) }.toByteArray()

    private suspend fun TestVault.restore(bytes: ByteArray, passphrase: String = TestVault.BACKUP, mode: RestoreMode = RestoreMode.REPLACE) =
        backups.restore({ bytes.inputStream() }, passphrase.toCharArray(), mode)

    private suspend inline fun <reified T : Throwable> expect(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            if (t is T) return
            throw t
        }
        fail("expected ${T::class.simpleName}")
    }

    @Test
    fun `a vault can be exported, wiped and restored with every record intact`() = runTest {
        // Create a vault and save records.
        val original = seededVault()
        val expectedItems = original.repository.contents.value!!.items
        val expectedAttachments = original.repository.contents.value!!.attachments
        assertEquals(5, expectedItems.size)

        // Export it.
        val backup = original.export()

        // Delete local data: a brand new device, new storage, new master passphrase, new master key.
        val fresh = TestVault(this)
        fresh.create("a-different-master-passphrase-7")
        assertTrue(fresh.repository.contents.value!!.items.isEmpty())

        // Restore the backup.
        val manifest = fresh.restore(backup)
        assertEquals(5, manifest.itemCount)
        assertEquals(2, manifest.attachmentCount)

        // Verify every restored record, field by field, and every attachment byte.
        val restored = fresh.repository.contents.value!!
        assertEquals(expectedItems.size, restored.items.size)
        expectedItems.forEach { expected -> assertEquals(expected, restored.items.single { it.id == expected.id }) }
        assertEquals(expectedAttachments, restored.attachments)
        val restoredFiles = restored.attachments.getValue("i1").associateBy { it.name }
        assertArrayEquals(scan, fresh.repository.readAttachment(restoredFiles.getValue("passport-scan.png").id))
        assertArrayEquals(photo, fresh.repository.readAttachment(restoredFiles.getValue("photo.jpg").id))

        // And it is really persisted under the new device's own key, not just held in memory.
        fresh.manager.lock()
        fresh.unlock("a-different-master-passphrase-7")
        assertEquals(expectedItems, fresh.repository.contents.value!!.items)
        assertArrayEquals(scan, fresh.repository.readAttachment(restoredFiles.getValue("passport-scan.png").id))
    }

    @Test
    fun `an incorrect backup passphrase is rejected and the vault is untouched`() = runTest {
        val backup = seededVault().export()
        val target = TestVault(this).apply { create() }
        target.repository.save(SampleItems.note("keep-me"))

        for (wrong in listOf("granite-harbor-willow-82", TestVault.MASTER, "x")) {
            expect<WrongBackupPassphraseException> { target.restore(backup, wrong) }
        }
        assertEquals(listOf("keep-me"), target.repository.contents.value!!.items.map { it.id })
    }

    @Test
    fun `a corrupted backup is rejected wherever the damage is`() = runTest {
        val backup = seededVault().export()
        val target = TestVault(this).apply { create() }
        target.repository.save(SampleItems.note("keep-me"))
        val before = target.storage.allBytes()

        // One flipped bit at positions spread across the header, the key slot and every part of the stream.
        val positions = (0 until 120) + (120 until backup.size step 4_099) + listOf(backup.size - 1)
        for (position in positions) {
            val damaged = backup.copyOf().also { it[position] = (it[position].toInt() xor 0x01).toByte() }
            expect<BackupException> { target.restore(damaged) }
        }
        // Truncation and trailing data.
        for (length in listOf(0, 5, 40, 200, backup.size / 2, backup.size - 1)) {
            expect<BackupException> { target.restore(backup.copyOf(length)) }
        }
        expect<BackupException> { target.restore(backup + byteArrayOf(0)) }

        assertArrayEquals("a rejected backup must not change the vault", before, target.storage.allBytes())
        assertEquals(listOf("keep-me"), target.repository.contents.value!!.items.map { it.id })
    }

    @Test
    fun `files that are not backups are recognised before the passphrase is tried`() = runTest {
        val target = TestVault(this).apply { create() }
        expect<NotABackupException> { target.restore(ByteArray(0)) }
        expect<NotABackupException> { target.restore("just a text file, not a backup".toByteArray()) }
        expect<NotABackupException> { target.backups.readHeader(ByteArray(500).inputStream()) }

        val backup = seededVault().export()
        val newer = backup.copyOf().also { it[BackupFormat.MAGIC.size] = 2 }
        expect<UnsupportedBackupException> { target.restore(newer) }
        assertEquals(BackupFormat.ID_BYTES, target.backups.readHeader(backup.inputStream()).backupId.size)
    }

    @Test
    fun `the body of one backup cannot be attached to the header of another`() = runTest {
        val vault = seededVault()
        val first = vault.export()
        val second = vault.export()
        val headerLength = ByteArrayOutputStream().also { BackupHeader.readFrom(first.inputStream()).writeTo(it) }.size()
        val spliced = first.copyOf(headerLength) + second.copyOfRange(headerLength, second.size)

        expect<CorruptedBackupException> { TestVault(this).apply { create() }.restore(spliced) }
    }

    @Test
    fun `a backup file contains no readable vault data`() = runTest {
        val backup = seededVault().export()
        val secrets = listOf(
            "Example Bank Portal", "kiran.demo", "S3cr3t-Horse-Battery!", "Travel Rewards Card", "Locker combination",
            "Z1234567", "mango-river-2291", "passport-scan.png", "small-plaintext-photo-bytes", "PASSWORD", "itemCount",
            TestVault.BACKUP, TestVault.MASTER,
        )
        for (secret in secrets) assertFalse("\"$secret\" is readable in the backup", backup.contains(secret.toByteArray()))
        assertFalse(backup.contains(scan.copyOf(64)))
    }

    @Test
    fun `replace removes existing records and merge keeps them`() = runTest {
        val backup = seededVault().export()

        val replaced = TestVault(this).apply { create() }
        replaced.repository.save(SampleItems.note("only-here"))
        replaced.restore(backup, mode = RestoreMode.REPLACE)
        assertNull(replaced.repository.find("only-here"))
        assertEquals(5, replaced.repository.contents.value!!.items.size)

        val merged = TestVault(this).apply { create() }
        merged.repository.save(SampleItems.note("only-here"))
        merged.repository.save(SampleItems.password("p1", title = "Older local copy"))
        merged.restore(backup, mode = RestoreMode.MERGE)
        assertEquals("Locker combination", merged.repository.find("only-here")!!.title)
        assertEquals("Example Bank Portal", merged.repository.find("p1")!!.title)
        assertEquals(6, merged.repository.contents.value!!.items.size)
        assertEquals(2, merged.repository.contents.value!!.attachments.getValue("i1").size)
    }

    @Test
    fun `restoring the same backup twice does not duplicate anything`() = runTest {
        val backup = seededVault().export()
        val target = TestVault(this).apply { create() }
        target.restore(backup, mode = RestoreMode.MERGE)
        target.restore(backup, mode = RestoreMode.MERGE)
        assertEquals(5, target.repository.contents.value!!.items.size)
        assertEquals(2, target.repository.contents.value!!.attachments.getValue("i1").size)
        assertEquals(2, target.storage.attachments.size)
    }

    @Test
    fun `backing up requires a backup passphrase and an unlocked vault`() = runTest {
        val unconfigured = TestVault(this).apply { create() }
        expect<BackupNotConfiguredException> { unconfigured.export() }

        val locked = seededVault()
        locked.manager.lock()
        expect<VaultLockedException> { locked.export() }
    }

    @Test
    fun `a fresh backup verifies and a damaged one does not`() = runTest {
        val vault = seededVault()
        val backup = vault.export()
        val manifest = vault.backups.verify(backup.inputStream())
        assertEquals(5, manifest.itemCount)
        assertEquals(BackupFormat.SCHEMA_VERSION, manifest.schemaVersion)

        val damaged = backup.copyOf().also { it[backup.size / 2] = (it[backup.size / 2].toInt() xor 1).toByte() }
        expect<BackupException> { vault.backups.verify(damaged.inputStream()) }
    }

    @Test
    fun `a restored device keeps the backup passphrase and can back up again`() = runTest {
        val backup = seededVault().export()
        val second = TestVault(this).apply { create("second-device-passphrase-55") }
        assertFalse(second.backupKeys.isConfigured())

        second.restore(backup)
        assertTrue(second.backupKeys.isConfigured())

        val third = TestVault(this).apply { create("third-device-passphrase-66") }
        third.restore(second.export())
        assertEquals(5, third.repository.contents.value!!.items.size)
    }

    @Test
    fun `changing the backup passphrase applies to new backups only`() = runTest {
        val vault = seededVault()
        val old = vault.export()
        vault.backupKeys.setPassphrase("brand-new-backup-phrase-90".toCharArray())
        val new = vault.export()

        val target = TestVault(this).apply { create() }
        target.restore(old, TestVault.BACKUP)
        target.restore(new, "brand-new-backup-phrase-90")
        expect<WrongBackupPassphraseException> { target.restore(new, TestVault.BACKUP) }
        expect<WrongBackupPassphraseException> { target.restore(old, "brand-new-backup-phrase-90") }
    }

    @Test
    fun `the backup passphrase is independent of the master passphrase`() = runTest {
        val vault = seededVault()
        val metadata = vault.store.current.value!!
        assertFalse(metadata.backupPassphraseSlot!!.contentEquals(metadata.passphraseSlot))
        // The master passphrase does not open a backup, and the backup passphrase does not open the vault.
        expect<WrongBackupPassphraseException> { TestVault(this).apply { create() }.restore(vault.export(), TestVault.MASTER) }
        vault.manager.lock()
        assertEquals(com.myvault.app.security.UnlockResult.WRONG_PASSPHRASE, vault.manager.unlock(TestVault.BACKUP.toCharArray()))
    }

    @Test
    fun `backup file names carry the date and time`() {
        assertEquals("MyVault_Backup_2026-11-21_1432.vault", BackupFormat.fileName(1_795_271_520_000, ZoneOffset.UTC))
    }
}
