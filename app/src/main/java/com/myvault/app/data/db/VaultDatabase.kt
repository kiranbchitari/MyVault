package com.myvault.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

/** Single-row table holding the vault's wrapped keys. Nothing here is plaintext secret data. */
@Entity(tableName = "vault_metadata")
class VaultMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "vault_id") val vaultId: ByteArray,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    @ColumnInfo(name = "key_generation") val keyGeneration: Int,
    @ColumnInfo(name = "passphrase_slot") val passphraseSlot: ByteArray,
    @ColumnInfo(name = "biometric_slot") val biometricSlot: ByteArray?,
    @ColumnInfo(name = "created_at") val createdAtEpochMs: Long,
    @ColumnInfo(name = "passphrase_changed_at") val passphraseChangedAtEpochMs: Long,
)

/** Single-row table of non-secret security preferences. */
@Entity(tableName = "security_settings")
class SecuritySettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "auto_lock_timeout_seconds") val autoLockTimeoutSeconds: Int,
    @ColumnInfo(name = "clipboard_clear_seconds") val clipboardClearSeconds: Int,
)

const val SINGLETON_ID = 1

@Dao
interface VaultMetadataDao {
    @Query("SELECT * FROM vault_metadata WHERE id = 1")
    fun observe(): Flow<VaultMetadataEntity?>

    @Query("SELECT * FROM vault_metadata WHERE id = 1")
    suspend fun get(): VaultMetadataEntity?

    /** ABORT on conflict: an existing vault row is never replaced. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: VaultMetadataEntity)

    @Query("UPDATE vault_metadata SET passphrase_slot = :slot, passphrase_changed_at = :changedAt WHERE id = 1")
    suspend fun updatePassphraseSlot(slot: ByteArray, changedAt: Long): Int

    @Query("UPDATE vault_metadata SET biometric_slot = :slot WHERE id = 1")
    suspend fun updateBiometricSlot(slot: ByteArray?): Int
}

@Dao
interface SecuritySettingsDao {
    @Query("SELECT * FROM security_settings WHERE id = 1")
    fun observe(): Flow<SecuritySettingsEntity?>

    @Query("SELECT * FROM security_settings WHERE id = 1")
    suspend fun get(): SecuritySettingsEntity?

    @Upsert
    suspend fun upsert(entity: SecuritySettingsEntity)
}

@Database(
    entities = [VaultMetadataEntity::class, SecuritySettingsEntity::class],
    version = VaultDatabase.VERSION,
    exportSchema = true,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultMetadataDao(): VaultMetadataDao
    abstract fun securitySettingsDao(): SecuritySettingsDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "myvault.db"
    }
}

/**
 * Schema migrations. Every schema change adds a Migration here and a new exported JSON under
 * app/schemas; destructive migration is never enabled.
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
