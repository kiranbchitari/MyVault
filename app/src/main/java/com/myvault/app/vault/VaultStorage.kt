package com.myvault.app.vault

class ItemRow(val id: String, val payload: ByteArray)

class AttachmentRow(val id: String, val itemId: String, val meta: ByteArray, val chunkCount: Int)

/**
 * Persistence for vault records. Everything that crosses this interface is ciphertext: the
 * storage layer never sees a title, a field or a file's contents.
 */
interface VaultStorage {
    /** Runs [block] atomically: either every write in it is committed or none is. */
    suspend fun <T> transaction(block: suspend () -> T): T

    suspend fun items(): List<ItemRow>
    suspend fun putItem(row: ItemRow)
    suspend fun deleteItem(id: String)
    suspend fun deleteAllItems()

    suspend fun attachments(): List<AttachmentRow>
    suspend fun putAttachment(row: AttachmentRow)
    suspend fun putChunk(attachmentId: String, index: Int, data: ByteArray)
    suspend fun chunk(attachmentId: String, index: Int): ByteArray?

    /** Removes the attachment and all of its chunks. */
    suspend fun deleteAttachment(id: String)
    suspend fun deleteAllAttachments()
}
