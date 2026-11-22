package com.myvault.app.backup

import com.myvault.app.BuildConfig
import com.myvault.app.di.IoDispatcher
import com.myvault.app.security.VaultLockedException
import com.myvault.app.vault.AttachmentInfo
import com.myvault.app.vault.VaultItem
import com.myvault.app.vault.VaultRepository
import com.myvault.crypto.Hkdf
import com.myvault.crypto.SealedStream
import com.myvault.crypto.SecretBytes
import com.myvault.crypto.VaultCipher
import com.myvault.crypto.VaultCryptoException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Writes and reads encrypted backups.
 *
 * A backup is a cleartext [BackupHeader] followed by a [SealedStream] of records encrypted under
 * a key derived from the backup key and the backup's random id. The records hold the vault's
 * decrypted contents, so a backup does not depend on the vault master key or the Android
 * Keystore of the device that made it: the backup passphrase alone opens it anywhere.
 */
@Singleton
class BackupManager @Inject constructor(
    private val repository: VaultRepository,
    private val backupKeys: BackupKeyManager,
    private val cipher: VaultCipher,
    private val random: SecureRandom,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Writes a backup of the unlocked vault's current contents to [output]. */
    suspend fun write(output: OutputStream): BackupSummary = withContext(io) {
        val contents = repository.contents.value ?: throw VaultLockedException()
        val attachments = contents.attachments.values.flatten()
        val createdAt = clock.millis()
        val counting = CountingOutputStream(output)
        backupKeys.withBackupKey { key, keyId, slot ->
            val backupId = ByteArray(BackupFormat.ID_BYTES).also(random::nextBytes)
            BackupHeader(keyId, backupId, slot).writeTo(counting)
            streamKey(key, backupId).use { streamKey ->
                val writer = SealedStream.Writer(counting, cipher, streamKey, backupId, BackupFormat.FRAME_LABEL)
                val manifest = BackupManifest(BackupFormat.SCHEMA_VERSION, createdAt, BuildConfig.VERSION_NAME, contents.items.size, attachments.size)
                writer.write(record(BackupFormat.RECORD_MANIFEST, json.encodeToString(BackupManifest.serializer(), manifest).toByteArray()))
                contents.items.forEach { item ->
                    writer.write(record(BackupFormat.RECORD_ITEM, json.encodeToString(VaultItem.serializer(), item).toByteArray()))
                }
                attachments.forEach { info ->
                    writer.write(record(BackupFormat.RECORD_ATTACHMENT, json.encodeToString(AttachmentInfo.serializer(), info).toByteArray()))
                    val data = repository.readAttachment(info.id)
                    var offset = 0
                    while (offset < data.size) {
                        val end = min(offset + BackupFormat.DATA_CHUNK_BYTES, data.size)
                        writer.write(record(BackupFormat.RECORD_ATTACHMENT_DATA, data.copyOfRange(offset, end)))
                        offset = end
                    }
                    writer.write(record(BackupFormat.RECORD_ATTACHMENT_END, ByteArray(0)))
                }
                writer.finish()
            }
        }
        counting.flush()
        BackupSummary(BackupFormat.fileName(createdAt), createdAt, counting.count, contents.items.size, attachments.size)
    }

    /** Reads only the cleartext header: confirms the file is a backup this build can read. */
    suspend fun readHeader(input: InputStream): BackupHeader = withContext(io) { BackupHeader.readFrom(input) }

    /**
     * Decrypts and checks a complete backup with the vault's own backup key, without needing the
     * backup passphrase. Used to confirm a backup is readable straight after writing it.
     */
    suspend fun verify(input: InputStream): BackupManifest = withContext(io) {
        backupKeys.withBackupKey { key, keyId, _ ->
            val header = BackupHeader.readFrom(input)
            if (!header.keyId.contentEquals(keyId)) throw CorruptedBackupException()
            readRecords(input, header, key, importer = null)
        }
    }

    /**
     * Restores a backup into the unlocked vault. The whole file is decrypted and checked once
     * before anything is written, and the import itself is a single transaction, so a damaged
     * backup or a wrong passphrase leaves the vault exactly as it was.
     *
     * @param open opens a fresh stream over the backup file each time it is called
     */
    suspend fun restore(open: () -> InputStream, passphrase: CharArray, mode: RestoreMode): BackupManifest = withContext(io) {
        if (repository.contents.value == null) throw VaultLockedException()
        val header = open().use { BackupHeader.readFrom(it) }
        val key = backupKeys.open(passphrase, header)
        try {
            val manifest = open().use { input ->
                BackupHeader.readFrom(input)
                readRecords(input, header, key, importer = null)
            }
            repository.importAll(replace = mode == RestoreMode.REPLACE) { importer ->
                open().use { input ->
                    BackupHeader.readFrom(input)
                    readRecords(input, header, key, importer)
                }
            }
            backupKeys.adoptIfUnconfigured(key, header)
            manifest
        } finally {
            key.close()
        }
    }

    private suspend fun readRecords(input: InputStream, header: BackupHeader, key: SecretBytes, importer: VaultRepository.Importer?): BackupManifest =
        try {
            streamKey(key, header.backupId).use { streamKey ->
                val reader = SealedStream.Reader(input, cipher, streamKey, header.backupId, BackupFormat.FRAME_LABEL)
                val first = reader.read() ?: throw CorruptedBackupException()
                if (first.isEmpty() || first[0] != BackupFormat.RECORD_MANIFEST) throw CorruptedBackupException()
                val manifest = json.decodeFromString(BackupManifest.serializer(), String(first, 1, first.size - 1, Charsets.UTF_8))
                if (manifest.schemaVersion > BackupFormat.SCHEMA_VERSION) throw UnsupportedBackupException("schema version ${manifest.schemaVersion}")

                var items = 0
                var attachments = 0
                var openAttachment: AttachmentInfo? = null
                var attachmentData = ByteArrayOutputStream()
                while (true) {
                    val frame = reader.read() ?: break
                    if (frame.isEmpty()) throw CorruptedBackupException()
                    val body = frame.copyOfRange(1, frame.size)
                    when (frame[0]) {
                        BackupFormat.RECORD_ITEM -> {
                            if (openAttachment != null) throw CorruptedBackupException()
                            val item = json.decodeFromString(VaultItem.serializer(), String(body, Charsets.UTF_8))
                            if (item.id.isBlank()) throw CorruptedBackupException()
                            importer?.item(item)
                            items++
                        }
                        BackupFormat.RECORD_ATTACHMENT -> {
                            if (openAttachment != null) throw CorruptedBackupException()
                            val info = json.decodeFromString(AttachmentInfo.serializer(), String(body, Charsets.UTF_8))
                            if (info.sizeBytes < 0 || info.sizeBytes > VaultRepository.MAX_ATTACHMENT_BYTES) throw CorruptedBackupException()
                            openAttachment = info
                            attachmentData = ByteArrayOutputStream(info.sizeBytes.toInt())
                        }
                        BackupFormat.RECORD_ATTACHMENT_DATA -> {
                            val info = openAttachment ?: throw CorruptedBackupException()
                            if (attachmentData.size() + body.size > info.sizeBytes) throw CorruptedBackupException()
                            attachmentData.write(body)
                        }
                        BackupFormat.RECORD_ATTACHMENT_END -> {
                            val info = openAttachment ?: throw CorruptedBackupException()
                            if (attachmentData.size().toLong() != info.sizeBytes) throw CorruptedBackupException()
                            importer?.attachment(info, attachmentData.toByteArray())
                            openAttachment = null
                            attachments++
                        }
                        else -> throw UnsupportedBackupException("record type ${frame[0]}")
                    }
                }
                if (openAttachment != null || items != manifest.itemCount || attachments != manifest.attachmentCount) {
                    throw CorruptedBackupException()
                }
                manifest
            }
        } catch (_: VaultCryptoException) {
            throw CorruptedBackupException()
        } catch (_: SerializationException) {
            throw CorruptedBackupException()
        } catch (_: IllegalArgumentException) {
            throw CorruptedBackupException()
        }

    private fun streamKey(backupKey: SecretBytes, backupId: ByteArray): SecretBytes =
        Hkdf.deriveKey(backupKey, BackupFormat.STREAM_KEY_INFO.toByteArray(Charsets.US_ASCII) + backupId)

    private fun record(type: Byte, body: ByteArray): ByteArray = byteArrayOf(type) + body

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }
}
