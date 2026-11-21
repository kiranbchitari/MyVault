package com.myvault.app

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.di.VaultEntryPoint
import com.myvault.crypto.SecretBytes
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class AutoLockOnDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val session = EntryPointAccessors.fromApplication(context, VaultEntryPoint::class.java).session()
    private val power = context.getSystemService(PowerManager::class.java)

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    @Test
    fun turningTheScreenOffLocksTheVault() {
        session.open(SecretBytes.random(32, SecureRandom()))
        val liveKey = session.withMasterKey { it }
        assertTrue(session.isUnlocked.value)
        try {
            shell("input keyevent KEYCODE_SLEEP")
            assertTrue("vault still unlocked after screen off", waitUntil(10_000) { !session.isUnlocked.value })
            assertTrue("master key was not wiped", liveKey.isDestroyed)
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            waitUntil(10_000) { power.isInteractive }
            session.lock()
        }
        assertFalse(session.isUnlocked.value)
    }
}
