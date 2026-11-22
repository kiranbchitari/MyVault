package com.myvault.app.backup

import com.myvault.app.di.KdfDispatcher
import com.myvault.app.security.KeyPurpose
import com.myvault.app.security.VaultMetadata
import com.myvault.app.security.VaultMetadataStore
import com.myvault.app.security.VaultSession
import com.myvault.crypto.Argon2Params
import com.myvault.crypto.AuthenticationFailedException
import com.myvault.crypto.PassphraseKeyslot
import com.myvault.crypto.SecretBytes
import com.myvault.crypto.VaultCipher
import com.myvault.crypto.VaultCryptoException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The backup key: a random 256-bit key, separate from the vault master key, that encrypts
 * backups. It is stored twice, both times wrapped:
 *
 *  - under the **backup passphrase** (Argon2id). This slot is copied into every backup file, so a
 *    backup can be opened on any device with the backup passphrase alone.
 *  - under a key derived from the **vault master key**, so the app can make backups while
 *    unlocked without asking for the backup passphrase each time.
 *
 * Neither copy depends on the Android Keystore.
 */
@Singleton
class BackupKeyManager @Inject constructor(
    private val store: VaultMetadataStore,
    private val session: VaultSession,
    private val cipher: VaultCipher,
    private val random: SecureRandom,
    private val kdfParams: Argon2Params,
    @param:KdfDispatcher private val dispatcher: CoroutineDispatcher,
) {
    private val keyslot = PassphraseKeyslot(random, cipher, PassphraseKeyslot.BACKUP_LABEL)

    val configured: Flow<Boolean> = store.observe().map { it?.backupPassphraseSlot != null }.distinctUntilChanged()

    suspend fun isConfigured(): Boolean = store.load()?.backupPassphraseSlot != null

    /**
     * Sets or changes the backup passphrase. The backup key itself is kept when one exists, so
     * changing the passphrase never invalidates the vault's ability to make backups; backups
     * made earlier still open with the passphrase that was current when they were made.
     */
    suspend fun setPassphrase(passphrase: CharArray) = withContext(dispatcher) {
        val metadata = checkNotNull(store.load()) { "No vault" }
        val keyId = metadata.backupKeyId ?: ByteArray(BackupFormat.ID_BYTES).also(random::nextBytes)
        val key = unwrap(metadata) ?: SecretBytes.random(VaultCipher.KEY_BYTES, random)
        try {
            val slot = keyslot.create(passphrase, key, keyId, kdfParams)
            val reopened = keyslot.open(passphrase, slot, keyId)
            try {
                check(reopened.contentEquals(key))
            } finally {
                reopened.close()
            }
            store.updateBackupKey(keyId, slot, wrap(session, key, keyId))
        } finally {
            key.close()
        }
    }

    /** Runs [block] with the backup key. The vault must be unlocked. */
    suspend fun <T> withBackupKey(block: suspend (key: SecretBytes, keyId: ByteArray, slot: ByteArray) -> T): T {
        val metadata = checkNotNull(store.load()) { "No vault" }
        val keyId = metadata.backupKeyId ?: throw BackupNotConfiguredException()
        val slot = metadata.backupPassphraseSlot ?: throw BackupNotConfiguredException()
        val key = unwrap(metadata) ?: throw BackupNotConfiguredException()
        try {
            return block(key, keyId, slot)
        } finally {
            key.close()
        }
    }

    /** Opens a backup file's key with the backup passphrase. The caller closes the result. */
    suspend fun open(passphrase: CharArray, header: BackupHeader): SecretBytes = withContext(dispatcher) {
        try {
            keyslot.open(passphrase, header.slot, header.keyId)
        } catch (_: AuthenticationFailedException) {
            throw WrongBackupPassphraseException()
        } catch (_: VaultCryptoException) {
            throw CorruptedBackupException()
        }
    }

    /**
     * After restoring into a vault that has no backup passphrase yet, keep using the restored
     * backup's key and passphrase so the new device is protected by backups straight away.
     */
    suspend fun adoptIfUnconfigured(key: SecretBytes, header: BackupHeader) {
        if (isConfigured()) return
        store.updateBackupKey(header.keyId, header.slot, wrap(session, key, header.keyId))
    }

    /** The backup key re-wrapped for a new master key, used during master-key rotation. */
    fun rewrapFor(next: VaultSession, metadata: VaultMetadata): ByteArray? {
        val keyId = metadata.backupKeyId ?: return null
        val key = unwrap(metadata) ?: return null
        return key.use { wrap(next, it, keyId) }
    }

    private fun unwrap(metadata: VaultMetadata): SecretBytes? {
        val keyId = metadata.backupKeyId ?: return null
        val wrapped = metadata.backupKeyWrapped ?: return null
        return session.withKey(KeyPurpose.BACKUP_KEY_WRAP) { wrapKey, _ -> SecretBytes.wrap(cipher.open(wrapKey, wrapped, context(keyId))) }
    }

    private fun wrap(with: VaultSession, key: SecretBytes, keyId: ByteArray): ByteArray =
        with.withKey(KeyPurpose.BACKUP_KEY_WRAP) { wrapKey, generation ->
            key.access { cipher.seal(wrapKey, generation, it, context(keyId)) }
        }

    private fun context(keyId: ByteArray) = "MyVault/backup-key/v1".toByteArray(Charsets.US_ASCII) + keyId
}
