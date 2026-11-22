package com.myvault.app.util

import com.myvault.app.security.VaultMetadata
import com.myvault.crypto.Argon2Params
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Base64

class RecoveryKitTest {
    private val metadata = VaultMetadata(
        vaultId = ByteArray(16) { (it + 1).toByte() },
        formatVersion = 1,
        keyGeneration = 3,
        passphraseSlot = ByteArray(113) { (it * 3).toByte() },
        biometricSlot = ByteArray(62) { (it * 5).toByte() },
        createdAtEpochMs = 1_795_000_000_000,
        passphraseChangedAtEpochMs = 1_795_100_000_000,
        backupKeyId = ByteArray(16) { 9 },
        backupPassphraseSlot = ByteArray(113) { (it * 7).toByte() },
        backupKeyWrapped = ByteArray(66) { (it * 11).toByte() },
    )

    private fun render(backupConfigured: Boolean = true) =
        RecoveryKit.render(metadata, Argon2Params.DEFAULT, backupConfigured, 1_795_271_520_000, "MyVault_Backup_2026-11-21_1432.vault", 1_795_300_000_000, ZoneOffset.UTC)

    @Test
    fun `the kit explains what is needed to recover`() {
        val kit = render()
        for (expected in listOf("MASTER PASSPHRASE", "BACKUP PASSPHRASE", "HOW TO RESTORE", "AES-256-GCM", "Argon2id, 64 MiB, 3 passes", "Key generation    : 3", "MyVault_Backup_2026-11-21_1432.vault", "There is no reset")) {
            assertTrue("missing \"$expected\"", kit.contains(expected))
        }
        assertTrue(render(backupConfigured = false).contains("NOT SET"))
    }

    @Test
    fun `the kit contains no key material in any encoding`() {
        val kit = render()
        val secrets = listOf(metadata.passphraseSlot, metadata.biometricSlot!!, metadata.backupPassphraseSlot!!, metadata.backupKeyWrapped!!, metadata.vaultId, metadata.backupKeyId!!)
        for (secret in secrets) {
            val hex = secret.joinToString("") { "%02x".format(it) }
            assertFalse(kit.contains(hex.take(16), ignoreCase = true))
            assertFalse(kit.contains(Base64.getEncoder().encodeToString(secret).take(12)))
            assertFalse(kit.contains(Base64.getUrlEncoder().encodeToString(secret).take(12)))
        }
    }

    @Test
    fun `the vault fingerprint is a one-way hash of the vault id`() {
        val fingerprint = RecoveryKit.vaultFingerprint(metadata.vaultId)
        assertEquals(17, fingerprint.length)
        assertEquals(fingerprint, RecoveryKit.vaultFingerprint(metadata.vaultId))
        assertNotEquals(fingerprint, RecoveryKit.vaultFingerprint(ByteArray(16)))
        assertFalse(fingerprint.replace("-", "").equals("0102030405060708", ignoreCase = true).or(fingerprint.startsWith("01-02-03")))
    }
}
