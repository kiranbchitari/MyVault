package com.myvault.app

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myvault.app.data.RoomVaultMetadataStore
import com.myvault.app.data.SecuritySettingsRepository
import com.myvault.app.data.db.Migrations
import com.myvault.app.data.db.VaultDatabase
import com.myvault.app.data.db.VaultMetadataEntity
import com.myvault.app.security.UnlockResult
import com.myvault.app.security.VaultAlreadyExistsException
import com.myvault.app.security.VaultManager
import com.myvault.app.security.VaultSession
import com.myvault.app.security.VaultState
import com.myvault.crypto.Argon2Params
import com.myvault.crypto.PassphraseKeyslot
import com.myvault.crypto.VaultCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.time.Clock

/** Real SQLite on a real file: the vault survives a restart and the file holds no plaintext. */
@RunWith(AndroidJUnit4::class)
class VaultPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val random = SecureRandom()
    private val passphrase = "tidal-orchid-lantern-42"
    private var database: VaultDatabase? = null

    private fun openDatabase(): VaultDatabase =
        Room.databaseBuilder(context, VaultDatabase::class.java, DB_NAME).addMigrations(*Migrations.ALL).build().also { database = it }

    private fun manager(db: VaultDatabase, session: VaultSession) = VaultManager(
        store = RoomVaultMetadataStore(db.vaultMetadataDao()),
        session = session,
        keyslot = PassphraseKeyslot(random, VaultCipher(random)),
        random = random,
        kdfParams = Argon2Params.DEFAULT,
        clock = Clock.systemUTC(),
        kdfDispatcher = Dispatchers.Default,
    )

    @Before
    fun deleteOldDatabase() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun cleanUp() {
        database?.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun vaultSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        val firstSession = VaultSession()
        val first = openDatabase()
        manager(first, firstSession).createVault(passphrase.toCharArray())
        val originalKey = firstSession.withMasterKey { it.copy() }
        first.close()

        val secondSession = VaultSession()
        val restarted = manager(openDatabase(), secondSession)
        assertEquals(VaultState.LOCKED, restarted.state.first())
        assertEquals(UnlockResult.WRONG_PASSPHRASE, restarted.unlock("tidal-orchid-lantern-43".toCharArray()))
        assertFalse(secondSession.isUnlocked.value)
        assertEquals(UnlockResult.SUCCESS, restarted.unlock(passphrase.toCharArray()))
        assertTrue(originalKey.contentEquals(secondSession.withMasterKey { it.copy() }))
    }

    @Test
    fun databaseFilesContainNoPassphraseOrMasterKey() = runBlocking {
        val session = VaultSession()
        val db = openDatabase()
        manager(db, session).createVault(passphrase.toCharArray())
        val masterKey = session.withMasterKey { key -> key.access { it.copyOf() } }
        db.close()

        val files = context.getDatabasePath(DB_NAME).parentFile!!.listFiles { file -> file.name.startsWith(DB_NAME) }!!
        assertTrue("database file missing", files.any { it.name == DB_NAME && it.length() > 0 })
        for (file in files) {
            val bytes = file.readBytes()
            assertFalse("${file.name} holds the master key", bytes.contains(masterKey))
            assertFalse("${file.name} holds part of the master key", bytes.contains(masterKey.copyOf(8)))
            for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
                assertFalse("${file.name} holds the passphrase ($charset)", bytes.contains(passphrase.toByteArray(charset)))
            }
        }
    }

    @Test
    fun secondVaultCannotReplaceTheFirst() = runBlocking {
        val db = openDatabase()
        val session = VaultSession()
        val manager = manager(db, session)
        manager.createVault(passphrase.toCharArray())
        try {
            manager.createVault("some-other-passphrase-99".toCharArray())
            fail("expected VaultAlreadyExistsException")
        } catch (_: VaultAlreadyExistsException) {
        }

        // Even bypassing the manager, the table refuses a second row.
        val existing = db.vaultMetadataDao().get()!!
        try {
            db.vaultMetadataDao().insert(
                VaultMetadataEntity(
                    vaultId = ByteArray(16),
                    formatVersion = 1,
                    keyGeneration = 1,
                    passphraseSlot = ByteArray(64),
                    biometricSlot = null,
                    createdAtEpochMs = 0,
                    passphraseChangedAtEpochMs = 0,
                ),
            )
            fail("expected a constraint violation")
        } catch (_: SQLiteConstraintException) {
        }
        assertTrue(existing.passphraseSlot.contentEquals(db.vaultMetadataDao().get()!!.passphraseSlot))
    }

    @Test
    fun securitySettingsPersistAndRejectUnsupportedValues() = runBlocking {
        val first = openDatabase()
        val repository = SecuritySettingsRepository(first.securitySettingsDao())
        assertEquals(60, repository.settings.first().autoLockTimeoutSeconds)
        repository.setAutoLockTimeout(300)
        try {
            repository.setAutoLockTimeout(Int.MAX_VALUE)
            fail("an effectively-never timeout must be rejected")
        } catch (_: IllegalArgumentException) {
        }
        first.close()

        val reopened = SecuritySettingsRepository(openDatabase().securitySettingsDao())
        assertEquals(300, reopened.settings.first().autoLockTimeoutSeconds)
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    private companion object {
        const val DB_NAME = "persistence-test.db"
    }
}
