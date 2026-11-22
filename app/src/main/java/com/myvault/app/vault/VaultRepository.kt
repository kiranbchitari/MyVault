package com.myvault.app.vault

import com.myvault.app.di.ApplicationScope
import com.myvault.app.di.IoDispatcher
import com.myvault.app.security.KeyPurpose
import com.myvault.app.security.VaultLockedException
import com.myvault.app.security.VaultSession
import com.myvault.crypto.VaultCipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

class InvalidItemException(message: String) : IllegalArgumentException(message)

class AttachmentTooLargeException : IllegalArgumentException("Attachment is too large")

/** Decrypted vault contents, held in memory only while the vault is unlocked. */
data class VaultContents(
    val items: List<VaultItem> = emptyList(),
    val attachments: Map<String, List<AttachmentInfo>> = emptyMap(),
    /** Stored records that failed authentication or could not be parsed: damaged or tampered with. */
    val unreadable: Int = 0,
)

/**
 * The secure repository. Records are decrypted into memory when the vault unlocks and dropped
 * the moment it locks; every write is encrypted before it reaches [VaultStorage].
 */
@Singleton
class VaultRepository @Inject constructor(
    private val storage: VaultStorage,
    private val session: VaultSession,
    private val cipher: VaultCipher,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val state = MutableStateFlow<VaultContents?>(null)
    private val changeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    /** Null while locked or still decrypting. */
    val contents: StateFlow<VaultContents?> = state.asStateFlow()
    val items: Flow<List<VaultItem>?> = state.map { it?.items }

    /** Emits after every change to stored records. */
    val changes: SharedFlow<Unit> = changeEvents.asSharedFlow()

    init {
        // Dropped synchronously with the keys; see VaultSession.onLock.
        session.onLock { state.value = null }
        session.onUnlock { scope.launch { runCatching { reload() } } }
        if (session.isUnlocked.value) scope.launch { runCatching { reload() } }
    }

    /** Suspends until the contents have been decrypted after an unlock. */
    suspend fun awaitLoaded(): VaultContents = contents.first { it != null }!!

    /**
     * Decrypts everything in storage. A record that fails authentication is not loaded and is
     * counted in [VaultContents.unreadable] instead of hiding the rest of the vault.
     */
    suspend fun reload() = withContext(io) {
        mutex.withLock {
            val loaded = try {
                var unreadable = 0
                val items = storage.items().mapNotNull { row ->
                    try {
                        openItem(session, row)
                    } catch (e: VaultLockedException) {
                        throw e
                    } catch (_: Exception) {
                        unreadable++
                        null
                    }
                }.sortedBy { it.title.lowercase() }
                val attachments = storage.attachments().mapNotNull { row ->
                    try {
                        openAttachmentMeta(session, row)
                    } catch (e: VaultLockedException) {
                        throw e
                    } catch (_: Exception) {
                        unreadable++
                        null
                    }
                }.groupBy { it.itemId }
                VaultContents(items, attachments, unreadable)
            } catch (_: VaultLockedException) {
                null
            }
            publish(loaded)
        }
    }

    /** Never leaves decrypted contents behind if the vault locked while they were being prepared. */
    private fun publish(contents: VaultContents?) {
        state.value = contents
        if (!session.isUnlocked.value) state.value = null
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun find(id: String): VaultItem? = state.value?.items?.firstOrNull { it.id == id }

    /** Creates or updates [item]. Returns the stored item with its timestamps set. */
    suspend fun save(item: VaultItem): VaultItem = withContext(io) {
        ItemValidator.validate(item)?.let { throw InvalidItemException(it) }
        mutate { current ->
            val now = clock.millis()
            val existing = current.items.firstOrNull { it.id == item.id }
            val stored = item.copy(
                title = item.title.trim(),
                tags = item.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                lastAccessedAt = existing?.lastAccessedAt ?: item.lastAccessedAt,
            )
            storage.putItem(sealItem(session, stored))
            stored to current.copy(items = (current.items.filterNot { it.id == stored.id } + stored).sortedBy { it.title.lowercase() })
        }
    }

    suspend fun delete(id: String) = withContext(io) {
        mutate { current ->
            storage.transaction {
                current.attachments[id].orEmpty().forEach { storage.deleteAttachment(it.id) }
                storage.deleteItem(id)
            }
            Unit to current.copy(items = current.items.filterNot { it.id == id }, attachments = current.attachments - id)
        }
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = update(id, notify = true) { it.copy(favorite = favorite) }

    /** Records that the item was opened, for the "recently accessed" list. */
    suspend fun markAccessed(id: String) = update(id, notify = false) { it.copy(lastAccessedAt = clock.millis()) }

    private suspend fun update(id: String, notify: Boolean, transform: (VaultItem) -> VaultItem) = withContext(io) {
        mutate(notify) { current ->
            val existing = current.items.firstOrNull { it.id == id } ?: return@mutate Unit to current
            val updated = transform(existing)
            storage.putItem(sealItem(session, updated))
            Unit to current.copy(items = current.items.map { if (it.id == id) updated else it })
        }
    }

    suspend fun addAttachment(itemId: String, name: String, mimeType: String, data: ByteArray): AttachmentInfo = withContext(io) {
        if (data.size > MAX_ATTACHMENT_BYTES) throw AttachmentTooLargeException()
        mutate { current ->
            val info = AttachmentInfo(newId(), itemId, name.take(200), mimeType, data.size.toLong(), clock.millis())
            storage.transaction { writeAttachment(session, info, data) }
            info to current.copy(attachments = current.attachments + (itemId to (current.attachments[itemId].orEmpty() + info)))
        }
    }

    suspend fun readAttachment(id: String): ByteArray = withContext(io) {
        val info = state.value?.attachments?.values?.flatten()?.firstOrNull { it.id == id } ?: throw VaultLockedException()
        val chunkCount = chunkCount(info.sizeBytes)
        val out = ByteArray(info.sizeBytes.toInt())
        var offset = 0
        for (index in 0 until chunkCount) {
            val sealed = storage.chunk(id, index) ?: throw IllegalStateException("Attachment data is missing")
            val chunk = session.withKey(KeyPurpose.ATTACHMENTS) { key, _ -> cipher.open(key, sealed, chunkContext(id, index, chunkCount)) }
            chunk.copyInto(out, offset)
            offset += chunk.size
        }
        check(offset == out.size) { "Attachment data is incomplete" }
        out
    }

    suspend fun deleteAttachment(id: String) = withContext(io) {
        mutate { current ->
            storage.deleteAttachment(id)
            Unit to current.copy(attachments = current.attachments.mapValues { (_, list) -> list.filterNot { it.id == id } }.filterValues { it.isNotEmpty() })
        }
    }

    /** Writes used by restore. */
    interface Importer {
        suspend fun item(item: VaultItem)
        suspend fun attachment(info: AttachmentInfo, data: ByteArray)
    }

    /**
     * Imports records in one transaction: if [block] fails, nothing is changed. With [replace]
     * the existing contents are removed first; otherwise imported records overwrite records
     * with the same id and everything else is kept.
     */
    suspend fun importAll(replace: Boolean, block: suspend (Importer) -> Unit) = withContext(io) {
        mutex.withLock {
            if (!session.isUnlocked.value) throw VaultLockedException()
            storage.transaction {
                if (replace) {
                    storage.deleteAllAttachments()
                    storage.deleteAllItems()
                }
                block(object : Importer {
                    override suspend fun item(item: VaultItem) = storage.putItem(sealItem(session, item))
                    override suspend fun attachment(info: AttachmentInfo, data: ByteArray) {
                        storage.deleteAttachment(info.id)
                        writeAttachment(session, info, data)
                    }
                })
            }
        }
        reload()
        changeEvents.tryEmit(Unit)
    }

    /**
     * Master-key rotation: re-encrypts every stored row from the current session's keys to
     * [next]'s keys and runs [alsoInTransaction] (the key metadata update) in the same
     * transaction, so the stored keys and the stored rows can never disagree.
     */
    suspend fun reencryptAll(next: VaultSession, alsoInTransaction: suspend () -> Unit) = withContext(io) {
        mutex.withLock {
            storage.transaction {
                storage.items().forEach { row -> storage.putItem(sealItem(next, openItem(session, row))) }
                storage.attachments().forEach { row ->
                    val info = openAttachmentMeta(session, row)
                    storage.putAttachment(AttachmentRow(row.id, row.itemId, sealAttachmentMeta(next, info), row.chunkCount))
                    for (index in 0 until row.chunkCount) {
                        val sealed = storage.chunk(row.id, index) ?: throw IllegalStateException("Attachment data is missing")
                        val context = chunkContext(row.id, index, row.chunkCount)
                        val plain = session.withKey(KeyPurpose.ATTACHMENTS) { key, _ -> cipher.open(key, sealed, context) }
                        storage.putChunk(row.id, index, next.withKey(KeyPurpose.ATTACHMENTS) { key, generation -> cipher.seal(key, generation, plain, context) })
                    }
                }
                alsoInTransaction()
            }
        }
    }

    private suspend fun <T> mutate(notify: Boolean = true, block: suspend (VaultContents) -> Pair<T, VaultContents>): T =
        mutex.withLock {
            val current = state.value ?: throw VaultLockedException()
            val (result, updated) = block(current)
            publish(updated)
            if (notify) changeEvents.tryEmit(Unit)
            result
        }

    private fun sealItem(with: VaultSession, item: VaultItem): ItemRow {
        val plain = json.encodeToString(VaultItem.serializer(), item).toByteArray(Charsets.UTF_8)
        return ItemRow(item.id, with.withKey(KeyPurpose.ITEMS) { key, generation -> cipher.seal(key, generation, plain, itemContext(item.id)) })
    }

    private fun openItem(with: VaultSession, row: ItemRow): VaultItem {
        val plain = with.withKey(KeyPurpose.ITEMS) { key, _ -> cipher.open(key, row.payload, itemContext(row.id)) }
        return json.decodeFromString(VaultItem.serializer(), String(plain, Charsets.UTF_8)).also {
            check(it.id == row.id) { "Record id mismatch" }
        }
    }

    private fun sealAttachmentMeta(with: VaultSession, info: AttachmentInfo): ByteArray {
        val plain = json.encodeToString(AttachmentInfo.serializer(), info).toByteArray(Charsets.UTF_8)
        return with.withKey(KeyPurpose.ATTACHMENTS) { key, generation -> cipher.seal(key, generation, plain, metaContext(info.id)) }
    }

    private fun openAttachmentMeta(with: VaultSession, row: AttachmentRow): AttachmentInfo {
        val plain = with.withKey(KeyPurpose.ATTACHMENTS) { key, _ -> cipher.open(key, row.meta, metaContext(row.id)) }
        return json.decodeFromString(AttachmentInfo.serializer(), String(plain, Charsets.UTF_8))
    }

    private suspend fun writeAttachment(with: VaultSession, info: AttachmentInfo, data: ByteArray) {
        val chunkCount = chunkCount(data.size.toLong())
        storage.putAttachment(AttachmentRow(info.id, info.itemId, sealAttachmentMeta(with, info), chunkCount))
        for (index in 0 until chunkCount) {
            val from = index * CHUNK_BYTES
            val chunk = data.copyOfRange(from, min(from + CHUNK_BYTES, data.size))
            val context = chunkContext(info.id, index, chunkCount)
            storage.putChunk(info.id, index, with.withKey(KeyPurpose.ATTACHMENTS) { key, generation -> cipher.seal(key, generation, chunk, context) })
        }
    }

    companion object {
        const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024
        const val CHUNK_BYTES = 256 * 1024

        internal fun chunkCount(sizeBytes: Long): Int = ((sizeBytes + CHUNK_BYTES - 1) / CHUNK_BYTES).toInt()

        private fun itemContext(id: String) = "MyVault/item/v1/$id".toByteArray(Charsets.UTF_8)
        private fun metaContext(id: String) = "MyVault/attachment-meta/v1/$id".toByteArray(Charsets.UTF_8)

        /** Binds a chunk to its attachment, position and total count, so chunks cannot be reordered or dropped. */
        private fun chunkContext(id: String, index: Int, count: Int) =
            "MyVault/attachment-chunk/v1/$id/".toByteArray(Charsets.UTF_8) + ByteBuffer.allocate(8).putInt(index).putInt(count).array()
    }
}
