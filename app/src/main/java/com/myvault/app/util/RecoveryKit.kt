package com.myvault.app.util

import com.myvault.app.security.VaultMetadata
import com.myvault.crypto.Argon2Params
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The recovery kit: a plain-text sheet that tells you exactly what you need to get back into
 * your vault, with space to write the passphrases by hand. It deliberately contains no
 * passphrase, no key and no wrapped key: only non-secret identifiers and instructions.
 */
object RecoveryKit {
    const val FILE_NAME = "MyVault_Recovery_Kit.txt"
    private val date = DateTimeFormatter.ofPattern("d MMM yyyy")

    /** A short fingerprint to tell vaults apart; it reveals nothing about the keys. */
    fun vaultFingerprint(vaultId: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(vaultId).take(6).joinToString("-") { "%02X".format(it) }

    fun render(
        metadata: VaultMetadata,
        kdf: Argon2Params,
        backupConfigured: Boolean,
        lastBackupAt: Long?,
        lastBackupName: String?,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        fun day(ms: Long) = date.format(Instant.ofEpochMilli(ms).atZone(zone))
        val line = "-".repeat(64)
        return buildString {
            appendLine("MYVAULT RECOVERY KIT")
            appendLine("Generated ${day(now)}")
            appendLine(line)
            appendLine("Keep this sheet somewhere safe and offline, away from your phone.")
            appendLine("It contains no passwords and no encryption keys. On its own it")
            appendLine("cannot open your vault.")
            appendLine()
            appendLine("YOUR VAULT")
            appendLine("  Vault fingerprint : ${vaultFingerprint(metadata.vaultId)}")
            appendLine("  Created           : ${day(metadata.createdAtEpochMs)}")
            appendLine("  Passphrase set    : ${day(metadata.passphraseChangedAtEpochMs)}")
            appendLine("  Encryption        : AES-256-GCM")
            appendLine("  Key derivation    : Argon2id, ${kdf.memoryKiB / 1024} MiB, ${kdf.iterations} passes")
            appendLine("  Key generation    : ${metadata.keyGeneration}")
            appendLine()
            appendLine("YOUR BACKUPS")
            appendLine("  Backup passphrase : ${if (backupConfigured) "set" else "NOT SET - you have no usable backup yet"}")
            appendLine("  Last backup       : ${lastBackupAt?.let { "${day(it)}  (${lastBackupName.orEmpty()})" } ?: "none"}")
            appendLine("  Backup files      : MyVault_Backup_<date>_<time>.vault")
            appendLine()
            appendLine("WHAT YOU NEED TO RECOVER")
            appendLine("  To open the vault on this phone:")
            appendLine("    - your MASTER PASSPHRASE")
            appendLine("  To move to a new phone, or after losing this one:")
            appendLine("    - a backup file (.vault), from Google Drive or wherever you saved it")
            appendLine("    - your BACKUP PASSPHRASE")
            appendLine()
            appendLine("HOW TO RESTORE")
            appendLine("  1. Install MyVault on the new phone.")
            appendLine("  2. On the welcome screen choose \"Restore from a backup\" and create a")
            appendLine("     master passphrase for that phone.")
            appendLine("  3. Pick the backup file, or connect Google Drive and choose one.")
            appendLine("  4. Enter the backup passphrase.")
            appendLine()
            appendLine("WRITE YOUR PASSPHRASES HERE, BY HAND")
            appendLine("  Master passphrase : ______________________________________________")
            appendLine()
            appendLine("  Backup passphrase : ______________________________________________")
            appendLine()
            appendLine(line)
            appendLine("If you lose the master passphrase AND have no backup you can open,")
            appendLine("your data cannot be recovered by anyone. There is no reset.")
        }
    }
}
