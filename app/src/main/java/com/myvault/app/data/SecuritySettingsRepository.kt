package com.myvault.app.data

import com.myvault.app.data.db.SecuritySettingsDao
import com.myvault.app.data.db.SecuritySettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class SecuritySettings(
    val autoLockTimeoutSeconds: Int = DEFAULT_AUTO_LOCK_SECONDS,
    val clipboardClearSeconds: Int = DEFAULT_CLIPBOARD_CLEAR_SECONDS,
) {
    companion object {
        const val DEFAULT_AUTO_LOCK_SECONDS = 60
        const val DEFAULT_CLIPBOARD_CLEAR_SECONDS = 30

        /** There is deliberately no "never" option. */
        val AUTO_LOCK_OPTIONS_SECONDS = listOf(30, 60, 120, 300)
    }
}

@Singleton
class SecuritySettingsRepository @Inject constructor(private val dao: SecuritySettingsDao) {

    val settings: Flow<SecuritySettings> = dao.observe().map { it?.toModel() ?: SecuritySettings() }

    suspend fun setAutoLockTimeout(seconds: Int) {
        require(seconds in SecuritySettings.AUTO_LOCK_OPTIONS_SECONDS) { "Unsupported auto-lock timeout" }
        val current = dao.get()?.toModel() ?: SecuritySettings()
        dao.upsert(
            SecuritySettingsEntity(
                autoLockTimeoutSeconds = seconds,
                clipboardClearSeconds = current.clipboardClearSeconds,
            ),
        )
    }

    private fun SecuritySettingsEntity.toModel() = SecuritySettings(
        autoLockTimeoutSeconds = autoLockTimeoutSeconds.takeIf { it in SecuritySettings.AUTO_LOCK_OPTIONS_SECONDS }
            ?: SecuritySettings.DEFAULT_AUTO_LOCK_SECONDS,
        clipboardClearSeconds = clipboardClearSeconds,
    )
}
