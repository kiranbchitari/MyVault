package com.myvault.app.security

import com.myvault.crypto.Argon2Params
import com.myvault.crypto.PassphraseKeyslot
import com.myvault.crypto.SecretBytes
import com.myvault.crypto.VaultCipher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class VaultManagerTest {
    private val random = SecureRandom()
    private val store = FakeVaultMetadataStore()
    private val session = VaultSession()
    private val passphrase = "tidal-orchid-lantern-42"

    private fun TestScope.manager(sessionToUse: VaultSession = session) = VaultManager(
        store = store,
        session = sessionToUse,
        keyslot = PassphraseKeyslot(random, VaultCipher(random)),
        random = random,
        kdfParams = Argon2Params(memoryKiB = 256, iterations = 1, parallelism = 1),
        clock = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000), ZoneOffset.UTC),
        kdfDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun masterKeyCopy(from: VaultSession = session): SecretBytes = from.withMasterKey { it.copy() }

    @Test
    fun `creating a vault leaves it unlocked and persisted`() = runTest {
        val manager = manager()
        assertEquals(VaultState.NO_VAULT, manager.state.first())

        manager.createVault(passphrase.toCharArray())

        assertEquals(VaultState.UNLOCKED, manager.state.first())
        val metadata = assertNotNull(store.current.value).let { store.current.value!! }
        assertEquals(VaultMetadata.FORMAT_VERSION, metadata.formatVersion)
        assertEquals(16, metadata.vaultId.size)
        assertNull(metadata.biometricSlot)
        assertEquals(1_800_000_000_000, metadata.createdAtEpochMs)
    }

    @Test
    fun `creation goes straight from no vault to unlocked`() = runTest {
        val manager = manager()
        val seen = mutableListOf<VaultState>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { manager.state.toList(seen) }
        manager.createVault(passphrase.toCharArray())
        collector.cancel()
        assertEquals(listOf(VaultState.NO_VAULT, VaultState.UNLOCKED), seen)
    }

    @Test
    fun `the correct passphrase unlocks and recovers the same master key`() = runTest {
        val manager = manager()
        manager.createVault(passphrase.toCharArray())
        val original = masterKeyCopy()

        manager.lock()
        assertEquals(VaultState.LOCKED, manager.state.first())

        assertEquals(UnlockResult.SUCCESS, manager.unlock(passphrase.toCharArray()))
        assertEquals(VaultState.UNLOCKED, manager.state.first())
        assertTrue(original.contentEquals(masterKeyCopy()))
    }

    @Test
    fun `a wrong passphrase is rejected and the vault stays locked`() = runTest {
        val manager = manager()
        manager.createVault(passphrase.toCharArray())
        manager.lock()

        for (wrong in listOf("tidal-orchid-lantern-43", "TIDAL-ORCHID-LANTERN-42", " $passphrase", "wrong")) {
            assertEquals(wrong, UnlockResult.WRONG_PASSPHRASE, manager.unlock(wrong.toCharArray()))
            assertFalse(session.isUnlocked.value)
        }
        assertThrows(VaultLockedException::class.java) { session.withMasterKey { } }
    }

    @Test
    fun `locking wipes the master key from memory`() = runTest {
        val manager = manager()
        manager.createVault(passphrase.toCharArray())
        val liveKey = session.withMasterKey { it }
        assertFalse(liveKey.isDestroyed)

        manager.lock()

        assertTrue(liveKey.isDestroyed)
        assertThrows(VaultLockedException::class.java) { session.withMasterKey { } }
    }

    @Test
    fun `unlocking does not modify or retain the caller's passphrase array`() = runTest {
        val manager = manager()
        val chars = passphrase.toCharArray()
        manager.createVault(chars)
        assertEquals(passphrase, String(chars))
    }

    @Test
    fun `an existing vault is never overwritten`() = runTest {
        val manager = manager()
        manager.createVault(passphrase.toCharArray())
        val originalSlot = store.current.value!!.passphraseSlot
        manager.lock()

        try {
            manager.createVault("a-completely-different-passphrase".toCharArray())
            fail("expected VaultAlreadyExistsException")
        } catch (_: VaultAlreadyExistsException) {
        }

        assertTrue(originalSlot.contentEquals(store.current.value!!.passphraseSlot))
        assertEquals(UnlockResult.SUCCESS, manager.unlock(passphrase.toCharArray()))
    }

    @Test
    fun `a failed write leaves no vault and no unlocked session`() = runTest {
        val manager = manager()
        store.failNextCreate = true
        try {
            manager.createVault(passphrase.toCharArray())
            fail("expected the write failure to propagate")
        } catch (_: IllegalStateException) {
        }
        assertNull(store.current.value)
        assertFalse(session.isUnlocked.value)
        assertEquals(VaultState.NO_VAULT, manager.state.first())
    }

    @Test
    fun `nothing persisted contains the passphrase or the master key`() = runTest {
        val manager = manager()
        manager.createVault(passphrase.toCharArray())
        val masterKey = session.withMasterKey { key -> key.access { it.copyOf() } }
        val metadata = store.current.value!!
        val persisted = metadata.vaultId + metadata.passphraseSlot

        assertFalse(persisted.contains(masterKey))
        assertFalse(persisted.contains(masterKey.copyOf(8)))
        assertFalse(persisted.contains(passphrase.toByteArray(Charsets.UTF_8)))
        assertFalse(persisted.contains(passphrase.toByteArray(Charsets.UTF_16LE)))
        assertFalse(persisted.contains(passphrase.toByteArray(Charsets.UTF_16BE)))
    }

    @Test
    fun `changing the passphrase re-wraps the same master key`() = runTest {
        val manager = manager()
        manager.createVault(passphrase.toCharArray())
        val original = masterKeyCopy()
        val replacement = "copper-violin-mosaic-77"

        assertEquals(UnlockResult.SUCCESS, manager.changePassphrase(passphrase.toCharArray(), replacement.toCharArray()))
        manager.lock()

        assertEquals(UnlockResult.WRONG_PASSPHRASE, manager.unlock(passphrase.toCharArray()))
        assertEquals(UnlockResult.SUCCESS, manager.unlock(replacement.toCharArray()))
        assertTrue(original.contentEquals(masterKeyCopy()))
    }

    @Test
    fun `changing the passphrase requires the current one`() = runTest {
        val manager = manager()
        manager.createVault(passphrase.toCharArray())
        val slotBefore = store.current.value!!.passphraseSlot

        assertEquals(
            UnlockResult.WRONG_PASSPHRASE,
            manager.changePassphrase("not-the-passphrase".toCharArray(), "copper-violin-mosaic-77".toCharArray()),
        )

        assertTrue(slotBefore.contentEquals(store.current.value!!.passphraseSlot))
        manager.lock()
        assertEquals(UnlockResult.SUCCESS, manager.unlock(passphrase.toCharArray()))
    }

    @Test
    fun `a tampered keyslot does not unlock`() = runTest {
        val manager = manager()
        manager.createVault(passphrase.toCharArray())
        manager.lock()
        store.replace { m -> m.copy(passphraseSlot = m.passphraseSlot.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }) }

        assertEquals(UnlockResult.WRONG_PASSPHRASE, manager.unlock(passphrase.toCharArray()))
        assertFalse(session.isUnlocked.value)
    }

    @Test
    fun `a truncated keyslot is reported as damaged`() = runTest {
        val manager = manager()
        manager.createVault(passphrase.toCharArray())
        manager.lock()
        store.replace { it.copy(passphraseSlot = it.passphraseSlot.copyOf(20)) }

        assertEquals(UnlockResult.DAMAGED_VAULT, manager.unlock(passphrase.toCharArray()))
        assertFalse(session.isUnlocked.value)
    }

    @Test
    fun `a vault from a newer format is refused, not guessed at`() = runTest {
        val manager = manager()
        manager.createVault(passphrase.toCharArray())
        manager.lock()
        store.replace { it.copy(formatVersion = VaultMetadata.FORMAT_VERSION + 1) }

        assertEquals(UnlockResult.UNSUPPORTED_VAULT, manager.unlock(passphrase.toCharArray()))
    }

    @Test
    fun `unlocking without a vault reports that there is none`() = runTest {
        assertEquals(UnlockResult.NO_VAULT, manager().unlock(passphrase.toCharArray()))
    }

    @Test
    fun `a fresh process can unlock a stored vault`() = runTest {
        manager().createVault(passphrase.toCharArray())
        val original = masterKeyCopy()

        // Same persisted store, brand new in-memory session and manager: simulates app restart.
        val newSession = VaultSession()
        val restarted = manager(newSession)
        assertEquals(VaultState.LOCKED, restarted.state.first())
        assertEquals(UnlockResult.SUCCESS, restarted.unlock(passphrase.toCharArray()))
        assertTrue(original.contentEquals(masterKeyCopy(newSession)))
    }
}
