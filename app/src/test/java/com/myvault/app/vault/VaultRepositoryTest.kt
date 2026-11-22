package com.myvault.app.vault

import com.myvault.app.security.VaultLockedException
import com.myvault.app.security.contains
import com.myvault.crypto.VaultCryptoException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom

@OptIn(ExperimentalCoroutinesApi::class)
class VaultRepositoryTest {

    private suspend fun expectFailure(type: Class<out Throwable>, block: suspend () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            if (type.isInstance(t)) return
            throw t
        }
        fail("expected ${type.simpleName}")
    }

    @Test
    fun `items can be created, read, updated and deleted`() = runTest {
        val vault = TestVault(this).apply { create() }
        vault.clock.now = 1_000
        val saved = vault.repository.save(SampleItems.password())
        assertEquals(1_000, saved.createdAt)
        assertEquals(1_000, saved.updatedAt)
        assertEquals(listOf(saved), vault.repository.contents.value!!.items)

        vault.clock.now = 5_000
        val edited = vault.repository.save(saved.copy(title = "Renamed Portal", notes = "changed"))
        assertEquals(1_000, edited.createdAt)
        assertEquals(5_000, edited.updatedAt)
        assertEquals("Renamed Portal", vault.repository.find("p1")!!.title)
        assertEquals(1, vault.storage.items.size)

        vault.repository.delete("p1")
        assertNull(vault.repository.find("p1"))
        assertTrue(vault.storage.items.isEmpty())
    }

    @Test
    fun `every record type round-trips with all of its fields`() = runTest {
        val vault = TestVault(this).apply { create() }
        val saved = SampleItems.all.map { vault.repository.save(it) }

        vault.manager.lock()
        vault.unlock()

        val reloaded = vault.repository.contents.value!!.items.associateBy { it.id }
        assertEquals(saved.size, reloaded.size)
        saved.forEach { assertEquals(it, reloaded[it.id]) }
    }

    @Test
    fun `nothing readable reaches storage`() = runTest {
        val vault = TestVault(this).apply { create() }
        SampleItems.all.forEach { vault.repository.save(it) }
        vault.repository.addAttachment("i1", "passport-scan.jpg", "image/jpeg", "JFIF-plaintext-image-bytes".toByteArray())

        val stored = vault.storage.allBytes()
        val secrets = listOf(
            "Example Bank Portal", "kiran.demo", "S3cr3t-Horse-Battery!", "portal.example.com", "finance", "Renew every March",
            "Travel Rewards Card", "4242", "Locker combination", "Left 12", "Z1234567", "mango-river-2291",
            "PASSWORD", "CREDIT_CARD", "passport-scan.jpg", "image/jpeg", "JFIF-plaintext-image-bytes",
        )
        for (secret in secrets) {
            assertFalse("\"$secret\" is readable in storage", stored.contains(secret.toByteArray()))
            assertFalse("\"$secret\" is readable in storage (UTF-16)", stored.contains(secret.toByteArray(Charsets.UTF_16LE)))
        }
    }

    @Test
    fun `locking drops decrypted contents and blocks every operation`() = runTest {
        val vault = TestVault(this).apply { create() }
        vault.repository.save(SampleItems.password())
        vault.manager.lock()

        assertNull(vault.repository.contents.value)
        assertNull(vault.repository.find("p1"))
        expectFailure(VaultLockedException::class.java) { vault.repository.save(SampleItems.note()) }
        expectFailure(VaultLockedException::class.java) { vault.repository.delete("p1") }
        assertEquals(1, vault.storage.items.size)
    }

    @Test
    fun `favorites and last-accessed are stored without touching the edit time`() = runTest {
        val vault = TestVault(this).apply { create() }
        vault.clock.now = 1_000
        vault.repository.save(SampleItems.password())
        vault.clock.now = 9_000
        vault.repository.setFavorite("p1", true)
        vault.repository.markAccessed("p1")
        vault.manager.lock()
        vault.unlock()

        val item = vault.repository.find("p1")!!
        assertTrue(item.favorite)
        assertEquals(9_000, item.lastAccessedAt)
        assertEquals(1_000, item.updatedAt)
    }

    @Test
    fun `invalid items are refused and nothing is written`() = runTest {
        val vault = TestVault(this).apply { create() }
        expectFailure(InvalidItemException::class.java) { vault.repository.save(SampleItems.password().copy(title = "  ")) }
        assertTrue(vault.storage.items.isEmpty())
    }

    @Test
    fun `rows moved under another id are rejected and reported, not shown`() = runTest {
        val vault = TestVault(this).apply { create() }
        vault.repository.save(SampleItems.password("a"))
        vault.repository.save(SampleItems.note("b"))
        vault.repository.save(SampleItems.wifi("c"))
        vault.manager.lock()
        val swapped = vault.storage.items["a"]!!
        vault.storage.items["a"] = vault.storage.items["b"]!!
        vault.storage.items["b"] = swapped

        vault.unlock()

        val contents = vault.repository.contents.value!!
        assertEquals(listOf("c"), contents.items.map { it.id })
        assertEquals(2, contents.unreadable)
    }

    @Test
    fun `a tampered row is reported and the rest of the vault still opens`() = runTest {
        val vault = TestVault(this).apply { create() }
        vault.repository.save(SampleItems.password("a"))
        vault.repository.save(SampleItems.note("b"))
        vault.manager.lock()
        vault.storage.items["a"] = vault.storage.items["a"]!!.copyOf().also { it[30] = (it[30].toInt() xor 1).toByte() }

        vault.unlock()

        assertEquals(listOf("b"), vault.repository.contents.value!!.items.map { it.id })
        assertEquals(1, vault.repository.contents.value!!.unreadable)
    }

    @Test
    fun `contents are dropped synchronously when the vault locks`() = runTest {
        val vault = TestVault(this).apply { create() }
        vault.repository.save(SampleItems.password())
        // No suspension point between lock() and the check: nothing asynchronous may be needed.
        vault.session.lock()
        assertNull(vault.repository.contents.value)
    }

    @Test
    fun `attachments round-trip across chunk boundaries`() = runTest {
        val vault = TestVault(this).apply { create() }
        vault.repository.save(SampleItems.identity())
        val sizes = listOf(0, 1, VaultRepository.CHUNK_BYTES - 1, VaultRepository.CHUNK_BYTES, VaultRepository.CHUNK_BYTES + 1, 3 * VaultRepository.CHUNK_BYTES + 77)
        val files = sizes.map { size -> ByteArray(size).also(SecureRandom()::nextBytes) }
        val infos = files.mapIndexed { index, data -> vault.repository.addAttachment("i1", "scan-$index.png", "image/png", data) }

        vault.manager.lock()
        vault.unlock()

        assertEquals(infos, vault.repository.contents.value!!.attachments["i1"])
        infos.zip(files).forEach { (info, data) -> assertArrayEquals(data, vault.repository.readAttachment(info.id)) }
    }

    @Test
    fun `attachment chunks cannot be reordered or dropped`() = runTest {
        val vault = TestVault(this).apply { create() }
        vault.repository.save(SampleItems.identity())
        val info = vault.repository.addAttachment("i1", "scan.png", "image/png", ByteArray(2 * VaultRepository.CHUNK_BYTES + 5) { it.toByte() })

        val first = vault.storage.chunks[info.id to 0]!!
        vault.storage.chunks[info.id to 0] = vault.storage.chunks[info.id to 1]!!
        vault.storage.chunks[info.id to 1] = first
        expectFailure(VaultCryptoException::class.java) { vault.repository.readAttachment(info.id) }

        vault.storage.chunks.remove(info.id to 2)
        expectFailure(Exception::class.java) { vault.repository.readAttachment(info.id) }
    }

    @Test
    fun `oversized attachments are refused`() = runTest {
        val vault = TestVault(this).apply { create() }
        vault.repository.save(SampleItems.identity())
        expectFailure(AttachmentTooLargeException::class.java) {
            vault.repository.addAttachment("i1", "huge.png", "image/png", ByteArray(VaultRepository.MAX_ATTACHMENT_BYTES + 1))
        }
        assertTrue(vault.storage.attachments.isEmpty())
    }

    @Test
    fun `deleting an item deletes its attachments`() = runTest {
        val vault = TestVault(this).apply { create() }
        vault.repository.save(SampleItems.identity())
        vault.repository.save(SampleItems.note())
        vault.repository.addAttachment("i1", "scan.png", "image/png", ByteArray(300_000))
        assertFalse(vault.storage.chunks.isEmpty())

        vault.repository.delete("i1")

        assertTrue(vault.storage.attachments.isEmpty())
        assertTrue(vault.storage.chunks.isEmpty())
        assertNotNull(vault.repository.find("n1"))
    }

    @Test
    fun `a change notification is emitted for edits but not for views`() = runTest {
        val vault = TestVault(this).apply { create() }
        var changes = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vault.repository.changes.collect { changes++ } }
        vault.repository.save(SampleItems.password())
        vault.repository.markAccessed("p1")
        vault.repository.setFavorite("p1", true)
        vault.repository.delete("p1")
        job.cancel()
        assertEquals(3, changes)
    }

    @Test
    fun `two thousand items load and search quickly`() = runTest {
        val vault = TestVault(this).apply { create() }
        vault.repository.importAll(replace = true) { importer ->
            repeat(2_000) { importer.item(SampleItems.password(id = "id-$it", title = "Service number $it")) }
        }
        vault.manager.lock()

        val started = System.nanoTime()
        vault.unlock()
        val loadMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(2_000, vault.repository.contents.value!!.items.size)

        val searchStarted = System.nanoTime()
        val results = VaultSearch.search(vault.repository.contents.value!!.items, VaultSearch.Filter("number 1999"))
        val searchMs = (System.nanoTime() - searchStarted) / 1_000_000
        assertEquals("id-1999", results.single().id)
        assertTrue("unlock+decrypt took $loadMs ms", loadMs < 5_000)
        assertTrue("search took $searchMs ms", searchMs < 1_000)
    }
}
