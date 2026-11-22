package com.myvault.app.data

import com.myvault.app.data.db.SecuritySettingsDao
import com.myvault.app.data.db.SecuritySettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class SecuritySettings(
    val autoLockTimeoutSeconds: Int = DEFAULT_AUTO_LOCK_SECONDS,
    val clipboardClearSeconds: Int = DEFAULT_CLIPBOARD_CLEAR_SECONDS,
    /** Ask for the passphrase or a biometric again before revealing or copying a secret. */
    val reauthToReveal: Boolean = false,
    val autoBackup: Boolean = false,
    val driveConnected: Boolean = false,
    val vaultChangedAt: Long = 0,
) {
    companion object {
        const val DEFAULT_AUTO_LOCK_SECONDS = 60
        const val DEFAULT_CLIPBOARD_CLEAR_SECONDS = 30

        /** There is deliberately no "never" option. */
        val AUTO_LOCK_OPTIONS_SECONDS = listOf(30, 60, 120, 300)
        val CLIPBOARD_OPTIONS_SECONDS = listOf(15, 30, 60, 120)
    }
}

@Singleton
class SecuritySettingsRepository @Inject constructor(private val dao: SecuritySettingsDao) {
    private val mutex = Mutex()

    val settings: Flow<SecuritySettings> = dao.observe().map { it?.toModel() ?: SecuritySettings() }

    suspend fun current(): SecuritySettings = dao.get()?.toModel() ?: SecuritySettings()

    suspend fun setAutoLockTimeout(seconds: Int) {
        require(seconds in SecuritySettings.AUTO_LOCK_OPTIONS_SECONDS) { "Unsupported auto-lock timeout" }
        update { it.copy(autoLockTimeoutSeconds = seconds) }
    }

    suspend fun setClipboardClear(seconds: Int) {
        require(seconds in SecuritySettings.CLIPBOARD_OPTIONS_SECONDS) { "Unsupported clipboard duration" }
        update { it.copy(clipboardClearSeconds = seconds) }
    }

    suspend fun setReauthToReveal(enabled: Boolean) = update { it.copy(reauthToReveal = enabled) }

    suspend fun setAutoBackup(enabled: Boolean) = update { it.copy(autoBackup = enabled) }

    suspend fun setDriveConnected(connected: Boolean) =
        update { it.copy(driveConnected = connected, autoBackup = it.autoBackup && connected) }

    suspend fun markVaultChanged(atEpochMs: Long) = update { it.copy(vaultChangedAt = atEpochMs) }

    private suspend fun update(transform: (SecuritySettings) -> SecuritySettings) = mutex.withLock {
        val next = transform(current())
        dao.upsert(
            SecuritySettingsEntity(
                autoLockTimeoutSeconds = next.autoLockTimeoutSeconds,
                clipboardClearSeconds = next.clipboardClearSeconds,
                reauthToReveal = next.reauthToReveal,
                autoBackup = next.autoBackup,
                driveConnected = next.driveConnected,
                vaultChangedAt = next.vaultChangedAt,
            ),
        )
    }

    private fun SecuritySettingsEntity.toModel() = SecuritySettings(
        autoLockTimeoutSeconds = autoLockTimeoutSeconds.takeIf { it in SecuritySettings.AUTO_LOCK_OPTIONS_SECONDS }
            ?: SecuritySettings.DEFAULT_AUTO_LOCK_SECONDS,
        clipboardClearSeconds = clipboardClearSeconds.takeIf { it in SecuritySettings.CLIPBOARD_OPTIONS_SECONDS }
            ?: SecuritySettings.DEFAULT_CLIPBOARD_CLEAR_SECONDS,
        reauthToReveal = reauthToReveal,
        autoBackup = autoBackup,
        driveConnected = driveConnected,
        vaultChangedAt = vaultChangedAt,
    )
}
