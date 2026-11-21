package com.myvault.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Android Keystore key that protects the biometric copy of the vault master key.
 *
 * The key never leaves secure hardware, and every single use must be authorized by a Class 3
 * (strong) biometric: the ciphers returned here are unusable until BiometricPrompt has
 * authenticated them. Enrolling a new fingerprint or face permanently invalidates the key, which
 * sends the user back to the master passphrase.
 */
@Singleton
class BiometricKeystore @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    /** Replaces any existing key. Throws if the device has no enrolled strong biometric. */
    fun createKey() {
        deleteKey()
        val strongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        try {
            generate(strongBox)
        } catch (e: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    /** @throws android.security.keystore.KeyPermanentlyInvalidatedException after a biometric change */
    fun encryptCipher(): Cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }

    /** @throws android.security.keystore.KeyPermanentlyInvalidatedException after a biometric change */
    fun decryptCipher(iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv)) }

    fun hasKey(): Boolean = keyStore.containsAlias(KEY_ALIAS)

    fun deleteKey() {
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun key(): SecretKey =
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: throw IllegalStateException("Biometric key is missing")

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Timeout 0: authentication is required for every use of the key.
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
                if (strongBox) setIsStrongBoxBacked(true)
            }
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "myvault.biometric.vmk.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
