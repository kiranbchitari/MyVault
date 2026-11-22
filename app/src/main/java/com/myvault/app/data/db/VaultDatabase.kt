package com.myvault.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    @ColumnInfo(name = "backup_key_id") val backupKeyId: ByteArray? = null,
    @ColumnInfo(name = "backup_passphrase_slot") val backupPassphraseSlot: ByteArray? = null,
    @ColumnInfo(name = "backup_key_wrapped") val backupKeyWrapped: ByteArray? = null,
)

/** Single-row table of non-secret security preferences. */
@Entity(tableName = "security_settings")
class SecuritySettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "auto_lock_timeout_seconds") val autoLockTimeoutSeconds: Int,
    @ColumnInfo(name = "clipboard_clear_seconds") val clipboardClearSeconds: Int,
    @ColumnInfo(name = "reauth_to_reveal", defaultValue = "0") val reauthToReveal: Boolean = false,
    @ColumnInfo(name = "auto_backup", defaultValue = "0") val autoBackup: Boolean = false,
    @ColumnInfo(name = "drive_connected", defaultValue = "0") val driveConnected: Boolean = false,
    /** When stored records last changed; compared with the last backup to judge backup health. */
    @ColumnInfo(name = "vault_changed_at", defaultValue = "0") val vaultChangedAt: Long = 0,
)

/** One encrypted record. The payload hides everything about it, including its title and type. */
@Entity(tableName = "vault_item")
class VaultItemEntity(
    @PrimaryKey val id: String,
    val payload: ByteArray,
)

/** An encrypted attachment's description; its contents live in [AttachmentChunkEntity] rows. */
@Entity(tableName = "encrypted_attachment", indices = [Index("item_id")])
class EncryptedAttachmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    val meta: ByteArray,
    @ColumnInfo(name = "chunk_count") val chunkCount: Int,
)

/** Attachments are stored in chunks so no single row outgrows SQLite's cursor window. */
@Entity(tableName = "attachment_chunk", primaryKeys = ["attachment_id", "chunk_index"])
class AttachmentChunkEntity(
    @ColumnInfo(name = "attachment_id") val attachmentId: String,
    @ColumnInfo(name = "chunk_index") val chunkIndex: Int,
    val data: ByteArray,
)

/** History of backups made from this device. Holds no vault content. */
@Entity(tableName = "backup_metadata")
data class BackupMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    /** LOCAL_FILE or GOOGLE_DRIVE. */
    val destination: String,
    /** PENDING, UPLOADING, COMPLETED or FAILED. */
    val status: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    /** Google Drive file id once uploaded. */
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    /** Encrypted archive waiting in app storage to be uploaded. */
    @ColumnInfo(name = "local_path") val localPath: String? = null,
    val error: String? = null,
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

    @Query("UPDATE vault_metadata SET backup_key_id = :keyId, backup_passphrase_slot = :slot, backup_key_wrapped = :wrapped WHERE id = 1")
    suspend fun updateBackupKey(keyId: ByteArray, slot: ByteArray, wrapped: ByteArray): Int

    @Query(
        "UPDATE vault_metadata SET passphrase_slot = :slot, key_generation = :generation, backup_key_wrapped = :backupKeyWrapped, " +
            "biometric_slot = NULL, passphrase_changed_at = :changedAt WHERE id = 1",
    )
    suspend fun applyRotation(slot: ByteArray, generation: Int, backupKeyWrapped: ByteArray?, changedAt: Long): Int
}

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_item")
    suspend fun all(): List<VaultItemEntity>

    @Upsert
    suspend fun upsert(entity: VaultItemEntity)

    @Query("DELETE FROM vault_item WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM vault_item")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM vault_item")
    suspend fun count(): Int
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM encrypted_attachment")
    suspend fun all(): List<EncryptedAttachmentEntity>

    @Upsert
    suspend fun upsert(entity: EncryptedAttachmentEntity)

    @Upsert
    suspend fun upsertChunk(entity: AttachmentChunkEntity)

    @Query("SELECT data FROM attachment_chunk WHERE attachment_id = :attachmentId AND chunk_index = :index")
    suspend fun chunk(attachmentId: String, index: Int): ByteArray?

    @Query("DELETE FROM encrypted_attachment WHERE id = :id")
    suspend fun deleteRow(id: String)

    @Query("DELETE FROM attachment_chunk WHERE attachment_id = :id")
    suspend fun deleteChunks(id: String)

    @Query("DELETE FROM encrypted_attachment")
    suspend fun deleteAllRows()

    @Query("DELETE FROM attachment_chunk")
    suspend fun deleteAllChunks()
}

@Dao
interface BackupMetadataDao {
    @Query("SELECT * FROM backup_metadata ORDER BY created_at DESC, id DESC")
    fun observe(): Flow<List<BackupMetadataEntity>>

    @Query("SELECT * FROM backup_metadata WHERE id = :id")
    suspend fun get(id: Long): BackupMetadataEntity?

    @Query("SELECT * FROM backup_metadata WHERE destination = 'GOOGLE_DRIVE' AND status IN ('PENDING', 'UPLOADING', 'FAILED') AND local_path IS NOT NULL ORDER BY created_at")
    suspend fun awaitingUpload(): List<BackupMetadataEntity>

    @Insert
    suspend fun insert(entity: BackupMetadataEntity): Long

    @androidx.room.Update
    suspend fun update(entity: BackupMetadataEntity)

    @Query("DELETE FROM backup_metadata WHERE id = :id")
    suspend fun delete(id: Long)
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
    entities = [
        VaultMetadataEntity::class,
        SecuritySettingsEntity::class,
        VaultItemEntity::class,
        EncryptedAttachmentEntity::class,
        AttachmentChunkEntity::class,
        BackupMetadataEntity::class,
    ],
    version = VaultDatabase.VERSION,
    exportSchema = true,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultMetadataDao(): VaultMetadataDao
    abstract fun securitySettingsDao(): SecuritySettingsDao
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun backupMetadataDao(): BackupMetadataDao

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "myvault.db"
    }
}

/**
 * Schema migrations. Every schema change adds a Migration here and a new exported JSON under
 * app/schemas; destructive migration is never enabled.
 */
object Migrations {
    /** Phase 2-4: record storage, attachments, backup history, backup key and new settings. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vault_metadata ADD COLUMN backup_key_id BLOB")
            db.execSQL("ALTER TABLE vault_metadata ADD COLUMN backup_passphrase_slot BLOB")
            db.execSQL("ALTER TABLE vault_metadata ADD COLUMN backup_key_wrapped BLOB")
            db.execSQL("ALTER TABLE security_settings ADD COLUMN reauth_to_reveal INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE security_settings ADD COLUMN auto_backup INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE security_settings ADD COLUMN drive_connected INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE security_settings ADD COLUMN vault_changed_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS `vault_item` (`id` TEXT NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `encrypted_attachment` (`id` TEXT NOT NULL, `item_id` TEXT NOT NULL, " +
                    "`meta` BLOB NOT NULL, `chunk_count` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_encrypted_attachment_item_id` ON `encrypted_attachment` (`item_id`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `attachment_chunk` (`attachment_id` TEXT NOT NULL, `chunk_index` INTEGER NOT NULL, " +
                    "`data` BLOB NOT NULL, PRIMARY KEY(`attachment_id`, `chunk_index`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `backup_metadata` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, " +
                    "`destination` TEXT NOT NULL, `status` TEXT NOT NULL, `completed_at` INTEGER, `remote_id` TEXT, " +
                    "`local_path` TEXT, `error` TEXT)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
