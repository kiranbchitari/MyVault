package com.myvault.app

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myvault.app.di.VaultEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end through the real app: first-run setup, lock, wrong and right passphrase, and the
 * lifecycle-driven auto-lock, using the production Hilt graph, database and Argon2 parameters.
 */
@RunWith(AndroidJUnit4::class)
class VaultFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val entryPoint = EntryPointAccessors.fromApplication(context, VaultEntryPoint::class.java)
    private val session = entryPoint.session()
    private val passphrase = "tidal-orchid-lantern-42"
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun startFromAFreshInstall() {
        session.lock()
        entryPoint.database().clearAllTables()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        session.lock()
        entryPoint.database().clearAllTables()
    }

    private fun waitForText(text: String, timeoutMs: Long = 10_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForTag(tag: String, timeoutMs: Long = 10_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun passphraseFieldText(): String =
        compose.onNodeWithTag("passphrase").fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    @Test
    fun setUpLockAndUnlock() {
        // Welcome
        waitForText("Create my vault")
        assertNull(runBlocking { entryPoint.database().vaultMetadataDao().get() })
        compose.onNodeWithText("Create my vault").performClick()

        // Create passphrase: weak choices cannot continue
        waitForText("Create your master passphrase")
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithTag("passphrase").performTextInput("password1234")
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithTag("passphrase").performTextClearance()
        compose.onNodeWithTag("passphrase").performTextInput(passphrase)
        compose.onNodeWithText("Continue").assertIsEnabled().performClick()

        // Confirm passphrase: a mismatch is caught
        waitForText("Type it once more")
        assertEquals("the confirm field must start empty", "", passphraseFieldText())
        compose.onNodeWithTag("passphrase").performTextInput("tidal-orchid-lantern-43")
        compose.onNodeWithText("Continue").performClick()
        waitForText("The passphrases don't match.")
        compose.onNodeWithTag("passphrase").performTextClearance()
        compose.onNodeWithTag("passphrase").performTextInput(passphrase)
        compose.onNodeWithText("Continue").performClick()

        // Security setup: creation requires acknowledging that there is no reset
        waitForText("Secure your vault")
        compose.onNodeWithText("Create vault").assertIsNotEnabled()
        assertNull("nothing may be stored before the user confirms", runBlocking { entryPoint.database().vaultMetadataDao().get() })
        compose.onNodeWithTag("autolock-120").performScrollTo().performClick()
        compose.onNodeWithTag("acknowledge").performScrollTo().performClick()
        compose.onNodeWithText("Create vault").assertIsEnabled().performClick()

        // Biometric offer, then the unlocked vault
        waitForTag("biometric-setup", timeoutMs = 60_000)
        assertTrue(session.isUnlocked.value)
        val skip = if (compose.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty()) "Not now" else "Continue"
        compose.onNodeWithText(skip).performClick()
        waitForTag("vault-status")

        val stored = runBlocking { entryPoint.database().vaultMetadataDao().get() }
        assertNotNull(stored)
        assertNull(stored!!.biometricSlot)
        assertEquals(120, runBlocking { entryPoint.database().securitySettingsDao().get() }!!.autoLockTimeoutSeconds)

        // Manual lock
        compose.onNodeWithTag("lock").performClick()
        waitForTag("unlock-screen")
        assertFalse(session.isUnlocked.value)
        assertTrue(compose.onAllNodesWithTag("vault-status").fetchSemanticsNodes().isEmpty())

        // Wrong passphrase
        compose.onNodeWithTag("passphrase").performTextInput("tidal-orchid-lantern-43")
        compose.onNodeWithText("Unlock").performClick()
        waitForText("Incorrect passphrase. Please try again.", timeoutMs = 60_000)
        assertFalse(session.isUnlocked.value)
        assertEquals("the field is cleared after an attempt", "", passphraseFieldText())

        // Correct passphrase
        compose.onNodeWithTag("passphrase").performTextInput(passphrase)
        compose.onNodeWithText("Unlock").performClick()
        waitForTag("vault-status", timeoutMs = 60_000)
        assertTrue(session.isUnlocked.value)

        // Leaving the app locks the vault
        scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(10_000) { !session.isUnlocked.value }
        scenario.moveToState(Lifecycle.State.RESUMED)
        waitForTag("unlock-screen")
        assertTrue(compose.onAllNodesWithTag("vault-status").fetchSemanticsNodes().isEmpty())

        // The passphrase is not kept in saved instance state across activity recreation
        compose.onNodeWithTag("passphrase").performTextInput(passphrase)
        scenario.recreate()
        waitForTag("unlock-screen")
        assertEquals("", passphraseFieldText())
        assertFalse(session.isUnlocked.value)

        // Nothing this process logged contains the passphrase
        val logs = Runtime.getRuntime().exec(arrayOf("logcat", "-d")).inputStream.bufferedReader().use { it.readText() }
        assertFalse("passphrase found in logcat", logs.contains(passphrase))
    }
}
