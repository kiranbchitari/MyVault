package com.myvault.app

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
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
import androidx.test.espresso.Espresso
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    private val secret = "Demo-Only!Quartz-Lantern-58"
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
        waitFor("text \"$text\"", timeoutMs) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForTag(tag: String, timeoutMs: Long = 10_000) =
        waitFor("tag \"$tag\"", timeoutMs) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    /**
     * Clicks a control that sits above the on-screen keyboard. Typing brings the keyboard up and
     * the layout slides for a few hundred milliseconds; a tap aimed at where the button used to
     * be would miss. So: dismiss the keyboard, wait until the button stops moving, then click.
     */
    private fun SemanticsNodeInteraction.clickWhenSettled() {
        Espresso.closeSoftKeyboard()
        var previous: Rect? = null
        compose.waitUntil(8_000) {
            val now = fetchSemanticsNode().boundsInWindow
            val stable = now == previous
            previous = now
            if (!stable) Thread.sleep(150)
            stable
        }
        performClick()
    }

    /** Like waitUntil, but a timeout reports what was actually on screen. */
    private fun waitFor(what: String, timeoutMs: Long, condition: () -> Boolean) {
        try {
            compose.waitUntil(timeoutMs, condition)
        } catch (e: ComposeTimeoutException) {
            val screen = runCatching { compose.onAllNodes(isRoot()).printToString(maxDepth = 40) }.getOrDefault("(unavailable)")
            throw AssertionError("Timed out after $timeoutMs ms waiting for $what. On screen:\n${screen.take(6_000)}", e)
        }
    }

    /** Scrolls the dashboard until the tagged row is on screen; it lists favorites below the categories. */
    private fun showOnHome(tag: String) {
        compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag(tag))
        compose.onAllNodesWithTag(tag).onFirst().assertIsDisplayed()
    }

    private fun homeTop() {
        compose.onNodeWithTag("home-list").performScrollToIndex(0)
    }

    /** What is on the clipboard. Read on the main thread, with the app in front, as Android requires. */
    private fun clipboardText(): String? {
        var text: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            text = context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
        }
        return text
    }

    /** A field's value on the detail screen. Its row is one tappable (copy) element, so the text sits in the unmerged tree. */
    private fun valueOf(key: String) = compose.onNodeWithTag("value-$key", useUnmergedTree = true)

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
        compose.onNodeWithText("Continue").assertIsEnabled().clickWhenSettled()

        // Confirm passphrase: a mismatch is caught
        waitForText("Type it once more")
        assertEquals("the confirm field must start empty", "", passphraseFieldText())
        compose.onNodeWithTag("passphrase").performTextInput("tidal-orchid-lantern-43")
        compose.onNodeWithText("Continue").clickWhenSettled()
        waitForText("The passphrases don't match.")
        compose.onNodeWithTag("passphrase").performTextClearance()
        compose.onNodeWithTag("passphrase").performTextInput(passphrase)
        compose.onNodeWithText("Continue").clickWhenSettled()

        // Security setup: creation requires acknowledging that there is no reset
        waitForText("Secure your vault")
        compose.onNodeWithText("Create vault").assertIsNotEnabled()
        assertNull("nothing may be stored before the user confirms", runBlocking { entryPoint.database().vaultMetadataDao().get() })
        compose.onNodeWithTag("autolock-120").performScrollTo().performClick()
        compose.onNodeWithTag("acknowledge").performScrollTo().performClick()
        compose.onNodeWithText("Create vault").assertIsEnabled().clickWhenSettled()

        // Biometric offer, then the unlocked vault opens on an empty dashboard
        waitForTag("biometric-setup", timeoutMs = 60_000)
        assertTrue(session.isUnlocked.value)
        val skip = if (compose.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty()) "Not now" else "Continue"
        compose.onNodeWithText(skip).performClick()
        waitForTag("home")
        waitForText("Your vault is empty")

        val stored = runBlocking { entryPoint.database().vaultMetadataDao().get() }
        assertNotNull(stored)
        assertNull(stored!!.biometricSlot)
        assertEquals(120, runBlocking { entryPoint.database().securitySettingsDao().get() }!!.autoLockTimeoutSeconds)

        // Quick Add: create a password through its dedicated form
        compose.onNodeWithTag("quick-add").performClick()
        waitForTag("pick-PASSWORD")
        compose.onNodeWithTag("pick-PASSWORD").performClick()
        waitForTag("item-edit")
        compose.onNodeWithTag("save").clickWhenSettled()
        waitForText("Website or app name is required.")
        compose.onNodeWithTag("field-title").performScrollTo().performTextInput("Northwind Mail")
        compose.onNodeWithTag("field-username").performScrollTo().performTextInput("alex.rivera")
        compose.onNodeWithTag("field-password").performScrollTo().performTextInput(secret)
        // Tags are an optional extra: one tap adds the row
        compose.onNodeWithTag("add-tags").performScrollTo().performClick()
        compose.onNodeWithTag("field-tags").performScrollTo().performTextInput("email, personal")
        compose.onNodeWithTag("save").clickWhenSettled()

        // Item details: the secret is masked until revealed
        waitForTag("item-detail")
        waitForText("Northwind Mail")
        valueOf("password").assertTextEquals("••••••••••")
        assertTrue(compose.onAllNodesWithText(secret).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("reveal-password").performClick()
        valueOf("password").assertTextEquals(secret)
        compose.onNodeWithTag("reveal-password").performClick()
        valueOf("password").assertTextEquals("••••••••••")

        // One tap on the main button copies the password, flagged sensitive, without revealing it
        compose.onNodeWithTag("copy-main").performClick()
        waitFor("the password on the clipboard", 5_000) { clipboardText() == secret }
        valueOf("password").assertTextEquals("••••••••••")
        // ...and so does tapping the row of any other field
        compose.onNodeWithTag("row-username").performClick()
        waitFor("the username on the clipboard", 5_000) { clipboardText() == "alex.rivera" }
        compose.onNodeWithTag("favorite").performClick()

        // The stored row is ciphertext: no title, username, tag or secret is readable in it
        val rows = runBlocking { entryPoint.database().vaultItemDao().all() }
        assertEquals(1, rows.size)
        for (plain in listOf("Northwind", "alex.rivera", secret, "personal", "PASSWORD")) {
            assertFalse("\"$plain\" is readable in the database row", String(rows[0].payload, Charsets.ISO_8859_1).contains(plain))
        }

        // Edit it
        compose.onNodeWithTag("edit").performClick()
        waitForTag("item-edit")
        compose.onNodeWithTag("add-notes").performScrollTo().performClick()
        compose.onNodeWithTag("field-notes").performScrollTo().performTextInput("Renew in March")
        compose.onNodeWithTag("save").clickWhenSettled()
        waitForTag("item-detail")
        waitForText("Renew in March")

        // Dashboard shows it as a favorite; universal search finds it by title, tag and field, but never by secret
        compose.onNodeWithContentDescription("Back").performClick()
        waitForTag("home")
        showOnHome("item-Northwind Mail")
        // The list copies the password in one tap, without opening the item
        compose.onAllNodesWithTag("quick-copy-Northwind Mail").onFirst().performClick()
        waitFor("the password on the clipboard", 5_000) { clipboardText() == secret }
        homeTop()
        compose.onNodeWithTag("search-bar").performClick()
        waitForTag("search")
        for (query in listOf("northwind", "personal", "alex", "march", "passwords mail")) {
            compose.onNodeWithTag("search-field").performTextClearance()
            compose.onNodeWithTag("search-field").performTextInput(query)
            waitForTag("item-Northwind Mail")
        }
        compose.onNodeWithTag("search-field").performTextClearance()
        compose.onNodeWithTag("search-field").performTextInput("Quartz")
        waitForText("No matches")
        compose.onNodeWithContentDescription("Back").performClick()
        waitForTag("home")

        // Tabs: categories, favorites, settings
        compose.onNodeWithTag("tab-categories").performClick()
        waitForTag("categories")
        compose.onNodeWithTag("category-PASSWORD").performClick()
        waitForTag("category-details")
        waitForTag("item-Northwind Mail")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("tab-favorites").performClick()
        waitForTag("favorites")
        waitForTag("item-Northwind Mail")
        compose.onNodeWithTag("tab-settings").performClick()
        waitForTag("settings")
        compose.onNodeWithTag("tab-home").performClick()
        waitForTag("home")
        homeTop()

        // Manual lock: unlocked content leaves the screen immediately
        compose.onNodeWithTag("lock").performClick()
        waitForTag("unlock-screen")
        assertFalse(session.isUnlocked.value)
        assertTrue(compose.onAllNodesWithTag("main-app").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Northwind Mail").fetchSemanticsNodes().isEmpty())

        // Wrong passphrase
        compose.onNodeWithTag("passphrase").performTextInput("tidal-orchid-lantern-43")
        compose.onNodeWithText("Unlock").clickWhenSettled()
        waitForText("Incorrect passphrase. Please try again.", timeoutMs = 60_000)
        assertFalse(session.isUnlocked.value)
        assertEquals("the field is cleared after an attempt", "", passphraseFieldText())

        // Correct passphrase
        compose.onNodeWithTag("passphrase").performTextInput(passphrase)
        compose.onNodeWithText("Unlock").clickWhenSettled()
        waitForTag("home", timeoutMs = 60_000)
        assertTrue(session.isUnlocked.value)
        waitForTag("shortcut-PASSWORD")
        showOnHome("item-Northwind Mail")

        // Leaving the app locks the vault
        scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(10_000) { !session.isUnlocked.value }
        scenario.moveToState(Lifecycle.State.RESUMED)
        waitForTag("unlock-screen")
        assertTrue(compose.onAllNodesWithTag("main-app").fetchSemanticsNodes().isEmpty())

        // The passphrase is not kept in saved instance state across activity recreation
        compose.onNodeWithTag("passphrase").performTextInput(passphrase)
        scenario.recreate()
        waitForTag("unlock-screen")
        assertEquals("", passphraseFieldText())
        assertFalse(session.isUnlocked.value)

        // Nothing this process logged contains the passphrase
        val logs = Runtime.getRuntime().exec(arrayOf("logcat", "-d")).inputStream.bufferedReader().use { it.readText() }
        assertFalse("passphrase found in logcat", logs.contains(passphrase))
        assertFalse("item secret found in logcat", logs.contains(secret))
        assertFalse("item title found in logcat", logs.contains("Northwind Mail"))
    }
}
