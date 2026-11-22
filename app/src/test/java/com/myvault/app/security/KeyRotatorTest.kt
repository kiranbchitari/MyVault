package com.myvault.app.security

import com.myvault.app.backup.RestoreMode
import com.myvault.app.vault.SampleItems
import com.myvault.app.vault.TestVault
import com.myvault.app.vault.VaultRepository
import com.myvault.crypto.VaultCipher
import com.myvault.crypto.VaultCryptoException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream

class KeyRotatorTest {
    private val file = ByteArray(VaultRepository.CHUNK_BYTES + 99) { (it * 7).toByte() }

    private suspend fun TestScope.seededVault(): TestVault {
        val vault = TestVault(this)
        vault.create()
        vault.backupKeys.setPassphrase(TestVault.BACKUP.toCharArray())
        SampleItems.all.forEach { vault.repository.save(it) }
        vault.repository.addAttachment("i1", "scan.png", "image/png", file)
        return vault
    }

    @Test
    fun `rotation re-encrypts everything under a new master key`() = runTest {
        val vault = seededVault()
        val oldKey = vault.session.withMasterKey { it.copy() }
        val itemsBefore = vault.repository.contents.value!!.items
        val rowsBefore = vault.storage.items.mapValues { it.value.copyOf() }
        val chunksBefore = vault.storage.chunks.mapValues { it.value.copyOf() }
        val cipher = VaultCipher(java.security.SecureRandom())

        assertEquals(UnlockResult.SUCCESS, vault.rotator.rotate(TestVault.MASTER.toCharArray()))

        // New key, next generation, and every stored ciphertext replaced.
        assertFalse(oldKey.contentEquals(vault.session.withMasterKey { it.copy() }))
        assertEquals(2, vault.store.current.value!!.keyGeneration)
        vault.storage.items.forEach { (id, payload) ->
            assertFalse("row $id was not re-encrypted", payload.contentEquals(rowsBefore.getValue(id)))
            assertEquals(2, cipher.keyGenerationOf(payload))
        }
        vault.storage.chunks.forEach { (key, data) -> assertFalse(data.contentEquals(chunksBefore.getValue(key))) }

        // The passphrase still unlocks, now to the new key, and nothing was lost.
        vault.manager.lock()
        vault.unlock()
        assertEquals(itemsBefore, vault.repository.contents.value!!.items)
        val attachment = vault.repository.contents.value!!.attachments.getValue("i1").single()
        assertArrayEquals(file, vault.repository.readAttachment(attachment.id))
        assertEquals(0, vault.repository.contents.value!!.unreadable)
    }

    @Test
    fun `the old master key can no longer read the vault`() = runTest {
        val vault = seededVault()
        val oldSession = VaultSession().apply { open(vault.session.withMasterKey { it.copy() }, 1) }
        val cipher = VaultCipher(java.security.SecureRandom())

        vault.rotator.rotate(TestVault.MASTER.toCharArray())

        val (id, payload) = vault.storage.items.entries.first()
        assertThrows(VaultCryptoException::class.java) {
            oldSession.withKey(KeyPurpose.ITEMS) { key, _ -> cipher.open(key, payload, "MyVault/item/v1/$id".toByteArray()) }
        }
    }

    @Test
    fun `rotation requires the master passphrase`() = runTest {
        val vault = seededVault()
        val rowsBefore = vault.storage.items.mapValues { it.value.copyOf() }
        val slotBefore = vault.store.current.value!!.passphraseSlot

        assertEquals(UnlockResult.WRONG_PASSPHRASE, vault.rotator.rotate("not-the-passphrase".toCharArray()))

        assertEquals(1, vault.store.current.value!!.keyGeneration)
        assertTrue(slotBefore.contentEquals(vault.store.current.value!!.passphraseSlot))
        vault.storage.items.forEach { (id, payload) -> assertTrue(payload.contentEquals(rowsBefore.getValue(id))) }
    }

    @Test
    fun `a failure part-way through leaves the old key fully in force`() = runTest {
        val vault = seededVault()
        val itemsBefore = vault.repository.contents.value!!.items
        val rowsBefore = vault.storage.items.mapValues { it.value.copyOf() }
        vault.store.failNextRotation = true

        try {
            vault.rotator.rotate(TestVault.MASTER.toCharArray())
            fail("expected the failure to propagate")
        } catch (_: IllegalStateException) {
        }

        assertEquals(1, vault.store.current.value!!.keyGeneration)
        vault.storage.items.forEach { (id, payload) -> assertTrue("row $id changed", payload.contentEquals(rowsBefore.getValue(id))) }
        vault.manager.lock()
        vault.unlock()
        assertEquals(itemsBefore, vault.repository.contents.value!!.items)
        assertEquals(0, vault.repository.contents.value!!.unreadable)
    }

    @Test
    fun `rotation drops the biometric slot and keeps backups working`() = runTest {
        val vault = seededVault()
        vault.store.updateBiometricSlot(ByteArray(64) { 1 })
        val backupBefore = ByteArrayOutputStream().also { vault.backups.write(it) }.toByteArray()

        vault.rotator.rotate(TestVault.MASTER.toCharArray())

        assertNull(vault.store.current.value!!.biometricSlot)
        // Backups made before and after rotation both restore with the unchanged backup passphrase.
        val backupAfter = ByteArrayOutputStream().also { vault.backups.write(it) }.toByteArray()
        val target = TestVault(this).apply { create() }
        target.backups.restore({ backupBefore.inputStream() }, TestVault.BACKUP.toCharArray(), RestoreMode.REPLACE)
        target.backups.restore({ backupAfter.inputStream() }, TestVault.BACKUP.toCharArray(), RestoreMode.REPLACE)
        assertEquals(5, target.repository.contents.value!!.items.size)
    }

    @Test
    fun `rotation can be repeated`() = runTest {
        val vault = seededVault()
        repeat(3) { assertEquals(UnlockResult.SUCCESS, vault.rotator.rotate(TestVault.MASTER.toCharArray())) }
        assertEquals(4, vault.store.current.value!!.keyGeneration)
        vault.manager.lock()
        vault.unlock()
        assertEquals(5, vault.repository.contents.value!!.items.size)
    }
}
