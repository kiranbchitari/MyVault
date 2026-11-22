package com.myvault.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.RoomVaultMetadataStore
import com.myvault.app.data.db.Migrations
import com.myvault.app.data.db.VaultDatabase
import com.myvault.app.security.UnlockResult
import com.myvault.app.security.VaultManager
import com.myvault.app.security.VaultSession
import com.myvault.crypto.Argon2Params
import com.myvault.crypto.PassphraseKeyslot
import com.myvault.crypto.SecretBytes
import com.myvault.crypto.VaultCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.time.Clock

/** A vault created by the Phase 1 build (schema 1) must open unchanged after upgrading. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), VaultDatabase::class.java)

    private val name = "migration-test.db"

    @Test
    fun aVersion1VaultStillUnlocksAfterMigratingToVersion2() {
        val random = SecureRandom()
        val cipher = VaultCipher(random)
        val keyslot = PassphraseKeyslot(random, cipher)
        val vaultId = ByteArray(16).also(random::nextBytes)
        val masterKey = SecretBytes.random(32, random)
        val passphrase = "tidal-orchid-lantern-42"
        val slot = keyslot.create(passphrase.toCharArray(), masterKey, vaultId)
        val biometricSlot = ByteArray(62) { it.toByte() }

        // Exactly what the Phase 1 build wrote.
        helper.createDatabase(name, 1).use { db ->
            db.insert("vault_metadata", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", 1)
                put("vault_id", vaultId)
                put("format_version", 1)
                put("key_generation", 1)
                put("passphrase_slot", slot)
                put("biometric_slot", biometricSlot)
                put("created_at", 1_795_000_000_000)
                put("passphrase_changed_at", 1_795_000_000_000)
            })
            db.insert("security_settings", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", 1)
                put("auto_lock_timeout_seconds", 300)
                put("clipboard_clear_seconds", 60)
            })
        }

        // Runs the migration and checks the result against the exported version 2 schema.
        helper.runMigrationsAndValidate(name, 2, true, Migrations.MIGRATION_1_2).close()

        val database = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), VaultDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            runBlocking {
                val metadata = database.vaultMetadataDao().get()!!
                assertTrue(vaultId.contentEquals(metadata.vaultId))
                assertTrue(slot.contentEquals(metadata.passphraseSlot))
                assertTrue(biometricSlot.contentEquals(metadata.biometricSlot))
                assertNull(metadata.backupKeyId)
                assertNull(metadata.backupPassphraseSlot)
                assertNull(metadata.backupKeyWrapped)

                val settings = database.securitySettingsDao().get()!!
                assertEquals(300, settings.autoLockTimeoutSeconds)
                assertEquals(60, settings.clipboardClearSeconds)
                assertFalse(settings.reauthToReveal)
                assertFalse(settings.autoBackup)
                assertFalse(settings.driveConnected)
                assertEquals(0, settings.vaultChangedAt)

                assertEquals(0, database.vaultItemDao().count())

                // The original passphrase still unlocks to the original key.
                val session = VaultSession()
                val manager = VaultManager(
                    RoomVaultMetadataStore(database.vaultMetadataDao()), session, keyslot, random, Argon2Params.DEFAULT, Clock.systemUTC(), Dispatchers.Default,
                )
                assertEquals(UnlockResult.WRONG_PASSPHRASE, manager.unlock("not-the-passphrase-00".toCharArray()))
                assertEquals(UnlockResult.SUCCESS, manager.unlock(passphrase.toCharArray()))
                assertTrue(session.withMasterKey { it.contentEquals(masterKey) })
            }
        } finally {
            database.close()
            ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(name)
        }
    }
}
