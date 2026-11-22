package com.myvault.app.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myvault.app.data.SecuritySettingsRepository
import com.myvault.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies values to the clipboard and clears sensitive ones again after the configured delay.
 *
 * Sensitive clips are flagged so Android hides them from the clipboard preview. The clear is
 * scheduled twice: an in-process timer for accuracy, and WorkManager in case the process is
 * killed first. Android does not let a background app read the clipboard, so the clear cannot
 * check whether the clip is still ours: anything copied in the meantime is cleared with it.
 */
@Singleton
class SecureClipboard @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SecuritySettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var pendingClear: Job? = null

    /** Returns the number of seconds after which a sensitive value will be cleared, or 0. */
    suspend fun copy(label: String, value: String, sensitive: Boolean): Int {
        val clip = ClipData.newPlainText(label, value)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                } else {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        pendingClear?.cancel()
        if (!sensitive) return 0

        val seconds = settings.current().clipboardClearSeconds
        pendingClear = scope.launch {
            delay(seconds * 1000L)
            clear(context)
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            CLEAR_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ClipboardClearWorker>().setInitialDelay(seconds.toLong(), TimeUnit.SECONDS).build(),
        )
        return seconds
    }

    companion object {
        const val CLEAR_WORK = "myvault-clipboard-clear"

        fun clear(context: Context) {
            runCatching { context.getSystemService(ClipboardManager::class.java).clearPrimaryClip() }
        }
    }
}

class ClipboardClearWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SecureClipboard.clear(applicationContext)
        return Result.success()
    }
}
