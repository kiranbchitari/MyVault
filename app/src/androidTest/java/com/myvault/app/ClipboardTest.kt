package com.myvault.app

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myvault.app.di.VaultEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val entryPoint = EntryPointAccessors.fromApplication(context, VaultEntryPoint::class.java)
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    @Test
    fun aCopiedSecretIsMarkedSensitiveAndClearedAfterTheConfiguredDelay() {
        // The clipboard can only be read by the app in the foreground.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val seconds = runBlocking {
                entryPoint.securitySettings().setClipboardClear(15)
                entryPoint.secureClipboard().copy("Password", "Demo-Only!Clipboard-41", sensitive = true)
            }
            assertEquals(15, seconds)

            scenario.onActivity {
                assertTrue(clipboard.hasPrimaryClip())
                assertEquals("Demo-Only!Clipboard-41", clipboard.primaryClip!!.getItemAt(0).text.toString())
                assertTrue(clipboard.primaryClipDescription!!.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
            }

            val deadline = SystemClock.elapsedRealtime() + 25_000
            var cleared = false
            while (!cleared && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(500)
                scenario.onActivity { cleared = !clipboard.hasPrimaryClip() || clipboard.primaryClip?.getItemAt(0)?.text.isNullOrEmpty() }
            }
            assertTrue("the secret was still on the clipboard after the delay", cleared)
        }
    }

    @Test
    fun nonSensitiveValuesAreNotScheduledForClearing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val seconds = runBlocking { entryPoint.secureClipboard().copy("Username", "alex.rivera", sensitive = false) }
            assertEquals(0, seconds)
            scenario.onActivity {
                assertEquals("alex.rivera", clipboard.primaryClip!!.getItemAt(0).text.toString())
                assertFalse(clipboard.primaryClipDescription!!.extras?.getBoolean("android.content.extra.IS_SENSITIVE") ?: false)
            }
        }
    }
}
