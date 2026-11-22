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

    @Volatile
    private var externalFlowUntil = 0L

    fun start() {
        if (started) return
        started = true

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // A system screen the user opened from MyVault (file picker, Google consent) is not
                // "leaving the app". The inactivity timer and screen-off lock still apply meanwhile.
                if (SystemClock.elapsedRealtime() < externalFlowUntil) return
                session.lock()
            }

            override fun onStart(owner: LifecycleOwner) {
                externalFlowUntil = 0
            }
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

    /**
     * Call just before launching a system screen for a result. The vault then stays unlocked
     * while that screen is in front, for at most [EXTERNAL_FLOW_GRACE_MS] or the auto-lock
     * timeout, whichever comes first.
     */
    fun expectExternalFlow() {
        timer.touch()
        externalFlowUntil = SystemClock.elapsedRealtime() + EXTERNAL_FLOW_GRACE_MS
    }

    /** Called for every touch or key event delivered to the activity. */
    fun onUserInteraction() = timer.touch()

    private companion object {
        const val EXTERNAL_FLOW_GRACE_MS = 3 * 60 * 1000L
    }
}
