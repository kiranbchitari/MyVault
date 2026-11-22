package com.myvault.app

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.security.BiometricAvailability
import com.myvault.app.ui.home.BiometricSetupContent
import com.myvault.app.ui.home.SecurityUiState
import com.myvault.app.ui.onboarding.ConfirmPassphraseScreen
import com.myvault.app.ui.onboarding.CreatePassphraseScreen
import com.myvault.app.ui.onboarding.OnboardingUiState
import com.myvault.app.ui.onboarding.SecuritySetupScreen
import com.myvault.app.ui.onboarding.WelcomeScreen
import com.myvault.app.ui.theme.MyVaultTheme
import com.myvault.app.ui.unlock.UnlockContent
import com.myvault.app.ui.unlock.UnlockUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders every Phase 1 screen in both themes and checks that it composes and shows its title.
 * Each render is also saved as a PNG under the app's files/screens directory for visual review.
 */
@RunWith(AndroidJUnit4::class)
class ScreenRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    private class Screen(val name: String, val expectedText: String, val content: @Composable () -> Unit)

    private val screens = listOf(
        Screen("welcome", "Create my vault") { WelcomeScreen(onStart = {}) },
        Screen("create-passphrase", "Create your master passphrase") { CreatePassphraseScreen(onBack = {}, onContinue = {}) },
        Screen("confirm-passphrase", "Type it once more") { ConfirmPassphraseScreen(onBack = {}, matches = { true }, onConfirmed = {}) },
        Screen("security-setup", "Secure your vault") {
            SecuritySetupScreen(OnboardingUiState(), onBack = {}, onAutoLockSelected = {}, onCreate = {})
        },
        Screen("biometric-setup", "Enable biometric unlock") {
            BiometricSetupContent(
                uiState = SecurityUiState(biometricAvailability = BiometricAvailability.AVAILABLE),
                canEnroll = true,
                onEnable = {},
                onSkip = {},
            )
        },
        Screen("unlock", "Welcome back") {
            UnlockContent(UnlockUiState(biometricEnrolled = true), onUnlock = {}, onUseBiometrics = {})
        },
        Screen("unlock-error", "Incorrect passphrase. Please try again.") {
            UnlockContent(UnlockUiState(error = "Incorrect passphrase. Please try again."), onUnlock = {}, onUseBiometrics = {})
        },
    )

    @Test
    fun everyScreenRendersInLightAndDarkThemes() {
        val outputDir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screens").apply {
            deleteRecursively()
            mkdirs()
        }
        var dark by mutableStateOf(false)
        var index by mutableStateOf(0)
        compose.setContent {
            MyVaultTheme(darkTheme = dark) {
                screens[index].content()
            }
        }
        for (useDark in listOf(true, false)) {
            for (i in screens.indices) {
                dark = useDark
                index = i
                compose.waitForIdle()
                compose.onNodeWithText(screens[i].expectedText).assertIsDisplayed()
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                File(outputDir, "${if (useDark) "dark" else "light"}-${screens[i].name}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
