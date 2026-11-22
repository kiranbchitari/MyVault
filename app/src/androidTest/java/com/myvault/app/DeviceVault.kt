package com.myvault.app

import android.content.Context
import androidx.room.Room
import com.myvault.app.backup.BackupKeyManager
import com.myvault.app.backup.BackupManager
import com.myvault.app.data.RoomVaultMetadataStore
import com.myvault.app.data.RoomVaultStorage
import com.myvault.app.data.db.Migrations
import com.myvault.app.data.db.VaultDatabase
import com.myvault.app.security.KeyRotator
import com.myvault.app.security.VaultManager
import com.myvault.app.security.VaultSession
import com.myvault.app.vault.FieldKind
import com.myvault.app.vault.ItemField
import com.myvault.app.vault.ItemType
import com.myvault.app.vault.VaultItem
import com.myvault.app.vault.VaultRepository
import com.myvault.crypto.Argon2Params
import com.myvault.crypto.PassphraseKeyslot
import com.myvault.crypto.VaultCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.security.SecureRandom
import java.time.Clock

/** The production classes wired by hand over a real Room database file: one instance per "device". */
class DeviceVault(private val context: Context, private val databaseName: String) : AutoCloseable {
    private val random = SecureRandom()
    private val cipher = VaultCipher(random)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val keyslot = PassphraseKeyslot(random, cipher)
    private val clock = Clock.systemUTC()

    val database: VaultDatabase = Room.databaseBuilder(context, VaultDatabase::class.java, databaseName).addMigrations(*Migrations.ALL).build()
    val session = VaultSession()
    private val store = RoomVaultMetadataStore(database.vaultMetadataDao())
    val manager = VaultManager(store, session, keyslot, random, Argon2Params.DEFAULT, clock, Dispatchers.Default)
    val repository = VaultRepository(RoomVaultStorage(database), session, cipher, clock, Dispatchers.IO, scope)
    val backupKeys = BackupKeyManager(store, session, cipher, random, Argon2Params.DEFAULT, Dispatchers.Default)
    val backups = BackupManager(repository, backupKeys, cipher, random, clock, Dispatchers.IO)
    val rotator = KeyRotator(store, session, repository, backupKeys, keyslot, random, Argon2Params.DEFAULT, clock, Dispatchers.Default)

    suspend fun create(passphrase: String) {
        manager.createVault(passphrase.toCharArray())
        repository.awaitLoaded()
    }

    suspend fun unlock(passphrase: String) {
        manager.unlock(passphrase.toCharArray())
        repository.awaitLoaded()
    }

    /** Every byte SQLite has written for this database, including the write-ahead log. */
    fun rawDatabaseBytes(): ByteArray {
        database.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        return context.getDatabasePath(databaseName).parentFile!!
            .listFiles { file -> file.name.startsWith(databaseName) }!!
            .fold(ByteArray(0)) { acc, file -> acc + file.readBytes() }
    }

    override fun close() {
        session.lock()
        scope.cancel()
        database.close()
    }

    companion object {
        fun items(): List<VaultItem> = listOf(
            VaultItem(
                id = "p1", type = ItemType.PASSWORD, title = "Northwind Mail",
                fields = listOf(
                    ItemField("username", "Username", "alex.rivera"),
                    ItemField("password", "Password", "Demo-Only!Quartz-Lantern-58", FieldKind.SECRET),
                    ItemField("c1", "Recovery hint", "First school", custom = true),
                ),
                notes = "Renew in March", tags = listOf("email", "personal"), favorite = true, createdAt = 0, updatedAt = 0,
            ),
            VaultItem(
                id = "b1", type = ItemType.BANK_ACCOUNT, title = "Example National Bank",
                fields = listOf(ItemField("holder", "Account holder", "Alex Rivera"), ItemField("number", "Account number", "000123456789", FieldKind.SECRET), ItemField("ifsc", "IFSC", "EXMP0001234")),
                createdAt = 0, updatedAt = 0,
            ),
            VaultItem(
                id = "c1", type = ItemType.CREDIT_CARD, title = "Travel Rewards",
                fields = listOf(ItemField("network", "Card network", "Visa"), ItemField("last4", "Last four digits", "0000"), ItemField("expiry", "Expiry date", "09/29")),
                createdAt = 0, updatedAt = 0,
            ),
            VaultItem(
                id = "i1", type = ItemType.IDENTITY, title = "Passport",
                fields = listOf(ItemField("number", "Document number", "X0000000", FieldKind.SECRET), ItemField("holder", "Holder name", "Alex Rivera")),
                createdAt = 0, updatedAt = 0,
            ),
            VaultItem(
                id = "n1", type = ItemType.SECURE_NOTE, title = "Bike lock",
                fields = listOf(ItemField("body", "Note", "Combination 0-0-0-0\nSpare key in the blue box", FieldKind.MULTILINE)),
                tags = listOf("home"), createdAt = 0, updatedAt = 0,
            ),
            VaultItem(
                id = "x1", type = ItemType.CUSTOM, title = "Gym membership", customTypeName = "Membership",
                fields = listOf(ItemField("f1", "Member number", "M-000-204", custom = true), ItemField("f2", "Locker code", "4417", FieldKind.SECRET, custom = true)),
                createdAt = 0, updatedAt = 0,
            ),
        )

        val readableSecrets = listOf(
            "Northwind Mail", "alex.rivera", "Demo-Only!Quartz-Lantern-58", "First school", "Renew in March", "personal",
            "Example National Bank", "000123456789", "EXMP0001234", "Travel Rewards", "Passport", "X0000000",
            "Bike lock", "Spare key in the blue box", "Gym membership", "Membership", "M-000-204", "passport-scan.png",
        )
    }
}

fun ByteArray.containsBytes(needle: ByteArray): Boolean {
    if (needle.isEmpty() || needle.size > size) return false
    outer@ for (i in 0..size - needle.size) {
        for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
        return true
    }
    return false
}
