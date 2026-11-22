package com.myvault.app

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myvault.app.security.BiometricKeystore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Properties of the installed app that the security model depends on. */
@RunWith(AndroidJUnit4::class)
class AppSecurityConfigTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun onlyTheExpectedPermissionsAreRequested() {
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        // INTERNET exists solely for the optional Google Drive upload of already-encrypted backups.
        assertTrue(Manifest.permission.INTERNET in requested)
        val unexpected = requested.filter { permission ->
            listOf("STORAGE", "LOCATION", "CONTACTS", "CAMERA", "RECORD_AUDIO", "READ_PHONE", "SMS", "CALL_LOG", "BLUETOOTH", "MEDIA").any { it in permission }
        }
        assertTrue("Unexpected permissions: $unexpected", unexpected.isEmpty())
    }

    @Test
    fun cleartextNetworkTrafficIsBlocked() {
        assertFalse(android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }

    @Test
    fun systemBackupIsDisabled() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun activityWindowBlocksScreenshots() {
        assumeFalse("built with -Pmyvault.allowScreenshots=true", BuildConfig.ALLOW_SCREENSHOTS)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
    }

    @Test
    fun biometricKeyIsNeverCreatedWithoutBiometricProtection() {
        val keystore = BiometricKeystore(context)
        val canAuthenticate = androidx.biometric.BiometricManager.from(context)
            .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        assumeTrue(
            "a strong biometric is enrolled on this device; covered by biometricKeyRequiresAuthenticationForEveryUse",
            canAuthenticate != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS,
        )
        // With nothing enrolled the Keystore must refuse, rather than fall back to an unprotected key.
        assertThrows(Exception::class.java) { keystore.createKey() }
        assertFalse(keystore.hasKey())
    }

    @Test
    fun biometricKeyRequiresAuthenticationForEveryUse() {
        val canAuthenticate = androidx.biometric.BiometricManager.from(context)
            .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        assumeTrue("no strong biometric enrolled on this device", canAuthenticate == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS)
        val keystore = BiometricKeystore(context)
        try {
            keystore.createKey()
            assertTrue(keystore.hasKey())
            // No BiometricPrompt has authorized this cipher, so using it must fail.
            assertThrows(Exception::class.java) { keystore.encryptCipher().doFinal(ByteArray(32)) }
        } finally {
            keystore.deleteKey()
        }
    }
}
