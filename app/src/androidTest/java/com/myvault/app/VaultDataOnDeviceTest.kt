package com.myvault.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myvault.app.backup.BackupException
import com.myvault.app.backup.RestoreMode
import com.myvault.app.backup.WrongBackupPassphraseException
import com.myvault.app.security.UnlockResult
import com.myvault.app.vault.VaultRepository
import com.myvault.app.vault.VaultSearch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.SecureRandom

/** Phases 2 to 4 on the real runtime: SQLite, Conscrypt, real files and production key-derivation cost. */
@RunWith(AndroidJUnit4::class)
class VaultDataOnDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val master = "tidal-orchid-lantern-42"
    private val backupPassphrase = "granite-harbor-willow-81"
    private val scan = ByteArray(2 * VaultRepository.CHUNK_BYTES + 12_345).also(SecureRandom()::nextBytes)
    private val opened = mutableListOf<DeviceVault>()
    private val backupFile: File get() = File(context.cacheDir, "device-test.vault")

    private fun device(name: String) = DeviceVault(context, name).also(opened::add)

    @Before
    @After
    fun cleanUp() {
        opened.forEach { runCatching { it.close() } }
        opened.clear()
        listOf(DB_A, DB_B).forEach(context::deleteDatabase)
        backupFile.delete()
    }

    private suspend fun seeded(name: String = DB_A): DeviceVault {
        val vault = device(name)
        vault.create(master)
        vault.backupKeys.setPassphrase(backupPassphrase.toCharArray())
        DeviceVault.items().forEach { vault.repository.save(it) }
        vault.repository.addAttachment("i1", "passport-scan.png", "image/png", scan)
        return vault
    }

    @Test
    fun recordsSurviveARestartAndTheDatabaseHoldsNoPlaintext() = runBlocking {
        val first = seeded()
        val expected = first.repository.contents.value!!.items
        val raw = first.rawDatabaseBytes()
        for (secret in DeviceVault.readableSecrets + master + backupPassphrase) {
            assertFalse("\"$secret\" is readable in the database", raw.containsBytes(secret.toByteArray()))
            assertFalse("\"$secret\" is readable in the database (UTF-16)", raw.containsBytes(secret.toByteArray(Charsets.UTF_16LE)))
        }
        assertFalse("attachment bytes are readable in the database", raw.containsBytes(scan.copyOf(48)))
        first.close()

        val restarted = device(DB_A)
        assertEquals(null, restarted.repository.contents.value)
        restarted.unlock(master)
        assertEquals(expected, restarted.repository.contents.value!!.items)
        val attachment = restarted.repository.contents.value!!.attachments.getValue("i1").single()
        assertArrayEquals(scan, restarted.repository.readAttachment(attachment.id))
    }

    @Test
    fun exportWipeRestoreKeepsEveryRecord() = runBlocking {
        // Create a vault, save records, export it.
        val original = seeded()
        val expected = original.repository.contents.value!!.items
        val summary = backupFile.outputStream().use { original.backups.write(it) }
        assertEquals(backupFile.length(), summary.sizeBytes)
        assertEquals(expected.size, summary.itemCount)
        val bytes = backupFile.readBytes()
        for (secret in DeviceVault.readableSecrets + master + backupPassphrase) {
            assertFalse("\"$secret\" is readable in the backup file", bytes.containsBytes(secret.toByteArray()))
        }

        // Delete all local data.
        original.close()
        assertTrue(context.deleteDatabase(DB_A))
        assertFalse(context.getDatabasePath(DB_A).exists())

        // A fresh install with a different master passphrase, then restore.
        val fresh = device(DB_B)
        fresh.create("a-different-master-passphrase-7")
        assertTrue(fresh.repository.contents.value!!.items.isEmpty())
        val manifest = fresh.backups.restore({ backupFile.inputStream() }, backupPassphrase.toCharArray(), RestoreMode.REPLACE)
        assertEquals(expected.size, manifest.itemCount)
        assertEquals(1, manifest.attachmentCount)

        // Verify every restored record and the attachment, then again after a restart.
        fun verify(vault: DeviceVault) = runBlocking {
            val restored = vault.repository.contents.value!!
            assertEquals(expected.size, restored.items.size)
            expected.forEach { item -> assertEquals(item, restored.items.single { it.id == item.id }) }
            assertEquals(0, restored.unreadable)
            assertArrayEquals(scan, vault.repository.readAttachment(restored.attachments.getValue("i1").single().id))
        }
        verify(fresh)
        fresh.close()
        val reopened = device(DB_B)
        reopened.unlock("a-different-master-passphrase-7")
        verify(reopened)
    }

    @Test
    fun wrongPassphraseAndCorruptedBackupsChangeNothing() = runBlocking {
        val source = seeded()
        backupFile.outputStream().use { source.backups.write(it) }
        val good = backupFile.readBytes()
        source.close()

        val target = device(DB_B)
        target.create(master)
        target.repository.save(DeviceVault.items().first().copy(id = "keep", title = "Keep me"))

        suspend fun expectRejected(bytes: ByteArray, passphrase: String = backupPassphrase, type: Class<out Throwable> = BackupException::class.java) {
            try {
                target.backups.restore({ bytes.inputStream() }, passphrase.toCharArray(), RestoreMode.REPLACE)
            } catch (t: Throwable) {
                assertTrue("expected ${type.simpleName} but was $t", type.isInstance(t))
                return
            }
            fail("a bad backup was accepted")
        }

        expectRejected(good, "not-the-backup-passphrase", WrongBackupPassphraseException::class.java)
        for (position in listOf(3, 9, 30, 100, good.size / 3, good.size / 2, good.size - 20)) {
            expectRejected(good.copyOf().also { it[position] = (it[position].toInt() xor 0x10).toByte() })
        }
        expectRejected(good.copyOf(good.size - 100))
        expectRejected(ByteArray(2_000).also(SecureRandom()::nextBytes))

        assertEquals(listOf("keep"), target.repository.contents.value!!.items.map { it.id })
        target.manager.lock()
        target.unlock(master)
        assertEquals(listOf("keep"), target.repository.contents.value!!.items.map { it.id })
    }

    @Test
    fun keyRotationReencryptsTheRealDatabaseAtomically() = runBlocking {
        val vault = seeded()
        val expected = vault.repository.contents.value!!.items
        val before = vault.database.vaultItemDao().all().associate { it.id to it.payload }

        assertEquals(UnlockResult.WRONG_PASSPHRASE, vault.rotator.rotate("wrong-passphrase-000".toCharArray()))
        assertEquals(UnlockResult.SUCCESS, vault.rotator.rotate(master.toCharArray()))

        val after = vault.database.vaultItemDao().all().associate { it.id to it.payload }
        assertEquals(before.keys, after.keys)
        after.forEach { (id, payload) -> assertFalse("row $id was not re-encrypted", payload.contentEquals(before.getValue(id))) }
        assertEquals(2, vault.database.vaultMetadataDao().get()!!.keyGeneration)

        vault.close()
        val restarted = device(DB_A)
        restarted.unlock(master)
        assertEquals(expected, restarted.repository.contents.value!!.items)
        assertEquals(0, restarted.repository.contents.value!!.unreadable)
        val attachment = restarted.repository.contents.value!!.attachments.getValue("i1").single()
        assertArrayEquals(scan, restarted.repository.readAttachment(attachment.id))
        // The backup key survived rotation.
        backupFile.outputStream().use { restarted.backups.write(it) }
        assertEquals(expected.size, backupFile.inputStream().use { restarted.backups.verify(it) }.itemCount)
    }

    @Test
    fun aThousandItemsUnlockAndSearchQuickly() = runBlocking {
        val vault = device(DB_A)
        vault.create(master)
        val template = DeviceVault.items().first()
        vault.repository.importAll(replace = true) { importer ->
            repeat(1_000) { importer.item(template.copy(id = "id-$it", title = "Service number $it")) }
        }
        vault.manager.lock()

        val started = System.nanoTime()
        vault.unlock(master)
        val unlockMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(1_000, vault.repository.contents.value!!.items.size)

        val searchStarted = System.nanoTime()
        val results = VaultSearch.search(vault.repository.contents.value!!.items, VaultSearch.Filter("number 999"))
        val searchMs = (System.nanoTime() - searchStarted) / 1_000_000
        assertEquals("id-999", results.single().id)
        // Includes the Argon2id derivation. Generous ceilings that still catch a pathological slowdown.
        assertTrue("unlock and decrypt took $unlockMs ms", unlockMs < 15_000)
        assertTrue("search took $searchMs ms", searchMs < 2_000)
    }

    private companion object {
        const val DB_A = "device-test-a.db"
        const val DB_B = "device-test-b.db"
    }
}
