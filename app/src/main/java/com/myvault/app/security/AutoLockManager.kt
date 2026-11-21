package com.myvault.app.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.myvault.app.data.SecuritySettingsRepository
import com.myvault.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locks the vault when the app leaves the foreground, when the screen turns off, and after a
 * configurable period without interaction. The first two are not configurable.
 */
@Singleton
class AutoLockManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val session: VaultSession,
    private val settings: SecuritySettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val timer = InactivityTimer(SystemClock::elapsedRealtime)
    private var started = false

    fun start() {
        if (started) return
        started = true

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = session.lock()
        })

        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = session.lock()
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        scope.launch {
            val timeoutMs = settings.settings.map { it.autoLockTimeoutSeconds * 1000L }.distinctUntilChanged()
            combine(session.isUnlocked, timeoutMs) { unlocked, timeout -> unlocked to timeout }
                .collectLatest { (unlocked, timeout) ->
                    if (unlocked) {
                        timer.awaitTimeout(timeout)
                        session.lock()
                    }
                }
        }
    }

    /** Called for every touch or key event delivered to the activity. */
    fun onUserInteraction() = timer.touch()
}
