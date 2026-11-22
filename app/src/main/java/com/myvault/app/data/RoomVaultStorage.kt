package com.myvault.app.data

import androidx.room.withTransaction
import com.myvault.app.data.db.AttachmentChunkEntity
import com.myvault.app.data.db.EncryptedAttachmentEntity
import com.myvault.app.data.db.VaultDatabase
import com.myvault.app.data.db.VaultItemEntity
import com.myvault.app.vault.AttachmentRow
import com.myvault.app.vault.ItemRow
import com.myvault.app.vault.VaultStorage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomVaultStorage @Inject constructor(private val database: VaultDatabase) : VaultStorage {
    private val items = database.vaultItemDao()
    private val attachments = database.attachmentDao()

    override suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction { block() }

    override suspend fun items(): List<ItemRow> = items.all().map { ItemRow(it.id, it.payload) }
    override suspend fun putItem(row: ItemRow) = items.upsert(VaultItemEntity(row.id, row.payload))
    override suspend fun deleteItem(id: String) = items.delete(id)
    override suspend fun deleteAllItems() = items.deleteAll()

    override suspend fun attachments(): List<AttachmentRow> =
        attachments.all().map { AttachmentRow(it.id, it.itemId, it.meta, it.chunkCount) }

    override suspend fun putAttachment(row: AttachmentRow) =
        attachments.upsert(EncryptedAttachmentEntity(row.id, row.itemId, row.meta, row.chunkCount))

    override suspend fun putChunk(attachmentId: String, index: Int, data: ByteArray) =
        attachments.upsertChunk(AttachmentChunkEntity(attachmentId, index, data))

    override suspend fun chunk(attachmentId: String, index: Int): ByteArray? = attachments.chunk(attachmentId, index)

    override suspend fun deleteAttachment(id: String) = database.withTransaction {
        attachments.deleteChunks(id)
        attachments.deleteRow(id)
    }

    override suspend fun deleteAllAttachments() = database.withTransaction {
        attachments.deleteAllChunks()
        attachments.deleteAllRows()
    }
}
