package com.myvault.app.backup

import kotlinx.serialization.Serializable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed class BackupException(message: String) : Exception(message)

/** The file is not a MyVault backup at all. */
class NotABackupException : BackupException("Not a MyVault backup")

/** The file was written by a newer version of MyVault. */
class UnsupportedBackupException(val what: String) : BackupException("Unsupported backup $what")

class WrongBackupPassphraseException : BackupException("Wrong backup passphrase")

/** The file is truncated or has been modified. */
class CorruptedBackupException : BackupException("Backup is damaged")

class BackupNotConfiguredException : BackupException("No backup passphrase has been set")

object BackupFormat {
    val MAGIC: ByteArray = "MYVLTBAK".toByteArray(Charsets.US_ASCII)
    const val VERSION = 1

    /** Version of the record layout inside the encrypted stream. */
    const val SCHEMA_VERSION = 1
    const val EXTENSION = ".vault"
    const val MIME_TYPE = "application/octet-stream"
    const val FRAME_LABEL = "MyVault/backup/frame/v1"
    const val STREAM_KEY_INFO = "MyVault/backup/stream-key/v1"
    const val ID_BYTES = 16
    const val MAX_SLOT_BYTES = 1024
    const val DATA_CHUNK_BYTES = 512 * 1024

    const val RECORD_MANIFEST: Byte = 1
    const val RECORD_ITEM: Byte = 2
    const val RECORD_ATTACHMENT: Byte = 3
    const val RECORD_ATTACHMENT_DATA: Byte = 4
    const val RECORD_ATTACHMENT_END: Byte = 5

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")

    /** For example `MyVault_Backup_2026-11-21_1432.vault`. */
    fun fileName(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "MyVault_Backup_${stamp.format(Instant.ofEpochMilli(epochMs).atZone(zone))}$EXTENSION"
}

/**
 * Cleartext start of a backup file. It holds nothing secret: identifiers, and the backup key
 * wrapped under the backup passphrase, which is what makes the file decryptable on any device.
 */
class BackupHeader(val keyId: ByteArray, val backupId: ByteArray, val slot: ByteArray) {

    fun writeTo(output: OutputStream) {
        val out = DataOutputStream(output)
        out.write(BackupFormat.MAGIC)
        out.writeByte(BackupFormat.VERSION)
        out.write(keyId)
        out.write(backupId)
        out.writeShort(slot.size)
        out.write(slot)
        out.flush()
    }

    companion object {
        fun readFrom(input: InputStream): BackupHeader {
            val source = DataInputStream(input)
            try {
                val magic = ByteArray(BackupFormat.MAGIC.size).also(source::readFully)
                if (!magic.contentEquals(BackupFormat.MAGIC)) throw NotABackupException()
                val version = source.readUnsignedByte()
                if (version != BackupFormat.VERSION) throw UnsupportedBackupException("format version $version")
                val keyId = ByteArray(BackupFormat.ID_BYTES).also(source::readFully)
                val backupId = ByteArray(BackupFormat.ID_BYTES).also(source::readFully)
                val slotLength = source.readUnsignedShort()
                if (slotLength == 0 || slotLength > BackupFormat.MAX_SLOT_BYTES) throw CorruptedBackupException()
                return BackupHeader(keyId, backupId, ByteArray(slotLength).also(source::readFully))
            } catch (_: EOFException) {
                throw NotABackupException()
            }
        }
    }
}

/** First record of the encrypted stream. */
@Serializable
data class BackupManifest(
    val schemaVersion: Int,
    val createdAt: Long,
    val appVersion: String,
    val itemCount: Int,
    val attachmentCount: Int,
)

data class BackupSummary(val fileName: String, val createdAt: Long, val sizeBytes: Long, val itemCount: Int, val attachmentCount: Int)

enum class RestoreMode {
    /** Keep existing records; records in the backup replace those with the same id. */
    MERGE,

    /** Delete everything in the vault, then load the backup. */
    REPLACE,
}
