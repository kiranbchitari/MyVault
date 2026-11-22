package com.myvault.app.vault

import com.myvault.app.backup.BackupKeyManager
import com.myvault.app.backup.BackupManager
import com.myvault.app.security.FakeVaultMetadataStore
import com.myvault.app.security.KeyRotator
import com.myvault.app.security.VaultManager
import com.myvault.app.security.VaultSession
import com.myvault.crypto.Argon2Params
import com.myvault.crypto.PassphraseKeyslot
import com.myvault.crypto.VaultCipher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** In-memory [VaultStorage] with real transaction rollback. */
class FakeVaultStorage : VaultStorage {
    var items = LinkedHashMap<String, ByteArray>()
    var attachments = LinkedHashMap<String, AttachmentRow>()
    var chunks = LinkedHashMap<Pair<String, Int>, ByteArray>()
    private var depth = 0

    /** Every stored byte, for checking that no plaintext reached storage. */
    fun allBytes(): ByteArray =
        (items.entries.flatMap { listOf(it.key.toByteArray(), it.value) } +
            attachments.values.map { it.meta } + chunks.values).fold(ByteArray(0)) { acc, b -> acc + b }

    override suspend fun <T> transaction(block: suspend () -> T): T {
        if (depth > 0) return block()
        val snapshot = Triple(LinkedHashMap(items), LinkedHashMap(attachments), LinkedHashMap(chunks))
        depth++
        try {
            return block()
        } catch (t: Throwable) {
            items = snapshot.first
            attachments = snapshot.second
            chunks = snapshot.third
            throw t
        } finally {
            depth--
        }
    }

    override suspend fun items() = items.map { ItemRow(it.key, it.value) }
    override suspend fun putItem(row: ItemRow) { items[row.id] = row.payload }
    override suspend fun deleteItem(id: String) { items.remove(id) }
    override suspend fun deleteAllItems() = items.clear()
    override suspend fun attachments() = attachments.values.toList()
    override suspend fun putAttachment(row: AttachmentRow) { attachments[row.id] = row }
    override suspend fun putChunk(attachmentId: String, index: Int, data: ByteArray) { chunks[attachmentId to index] = data }
    override suspend fun chunk(attachmentId: String, index: Int) = chunks[attachmentId to index]
    override suspend fun deleteAttachment(id: String) {
        attachments.remove(id)
        chunks.keys.filter { it.first == id }.forEach(chunks::remove)
    }
    override suspend fun deleteAllAttachments() {
        attachments.clear()
        chunks.clear()
    }
}

class MutableClock(var now: Long = 1_800_000_000_000) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
    override fun millis() = now
}

/** A complete vault wired together over in-memory fakes: one instance models one device. */
@OptIn(ExperimentalCoroutinesApi::class)
class TestVault(scope: TestScope, val clock: MutableClock = MutableClock()) {
    val random = SecureRandom()
    val cipher = VaultCipher(random)
    val store = FakeVaultMetadataStore()
    val storage = FakeVaultStorage()
    val session = VaultSession()
    private val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
    private val params = Argon2Params(memoryKiB = 256, iterations = 1, parallelism = 1)
    private val keyslot = PassphraseKeyslot(random, cipher)

    val manager = VaultManager(store, session, keyslot, random, params, clock, dispatcher)
    val repository = VaultRepository(storage, session, cipher, clock, dispatcher, scope.backgroundScope)
    val backupKeys = BackupKeyManager(store, session, cipher, random, params, dispatcher)
    val backups = BackupManager(repository, backupKeys, cipher, random, clock, dispatcher)
    val rotator = KeyRotator(store, session, repository, backupKeys, keyslot, random, params, clock, dispatcher)

    suspend fun create(passphrase: String = MASTER) {
        manager.createVault(passphrase.toCharArray())
        repository.awaitLoaded()
    }

    suspend fun unlock(passphrase: String = MASTER) {
        manager.unlock(passphrase.toCharArray())
        repository.awaitLoaded()
    }

    companion object {
        const val MASTER = "tidal-orchid-lantern-42"
        const val BACKUP = "granite-harbor-willow-81"
    }
}

object SampleItems {
    fun password(id: String = "p1", title: String = "Example Bank Portal") = VaultItem(
        id = id, type = ItemType.PASSWORD, title = title,
        fields = listOf(
            ItemField("username", "Username", "kiran.demo"),
            ItemField("email", "Email", "demo@example.com"),
            ItemField("password", "Password", "S3cr3t-Horse-Battery!", FieldKind.SECRET),
            ItemField("url", "Website URL", "https://portal.example.com", FieldKind.URL),
            ItemField("c1", "Security question", "First school", FieldKind.TEXT, custom = true),
        ),
        notes = "Renew every March", tags = listOf("finance", "personal"), createdAt = 0, updatedAt = 0,
    )

    fun card(id: String = "c1") = VaultItem(
        id = id, type = ItemType.CREDIT_CARD, title = "Travel Rewards Card",
        fields = listOf(
            ItemField("holder", "Cardholder name", "K DEMO"),
            ItemField("bank", "Bank", "Example Bank"),
            ItemField("network", "Card network", "Visa"),
            ItemField("last4", "Last four digits", "4242"),
            ItemField("expiry", "Expiry date", "08/29"),
        ),
        createdAt = 0, updatedAt = 0,
    )

    fun note(id: String = "n1") = VaultItem(
        id = id, type = ItemType.SECURE_NOTE, title = "Locker combination",
        fields = listOf(ItemField("body", "Note", "Left 12, right 30, left 7\nSpare key with neighbour", FieldKind.MULTILINE)),
        tags = listOf("home"), favorite = true, createdAt = 0, updatedAt = 0,
    )

    fun identity(id: String = "i1") = VaultItem(
        id = id, type = ItemType.IDENTITY, title = "Passport",
        fields = listOf(
            ItemField("number", "Document number", "Z1234567", FieldKind.SECRET),
            ItemField("holder", "Holder name", "Kiran Demo"),
            ItemField("expiry", "Expiry date", "14/02/2034"),
        ),
        createdAt = 0, updatedAt = 0,
    )

    fun wifi(id: String = "w1") = VaultItem(
        id = id, type = ItemType.WIFI, title = "Home Fibre 5G",
        fields = listOf(ItemField("password", "Password", "mango-river-2291", FieldKind.SECRET), ItemField("location", "Location", "Living room")),
        createdAt = 0, updatedAt = 0,
    )

    val all get() = listOf(password(), card(), note(), identity(), wifi())
}
