package com.myvault.app.security

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

sealed interface BiometricAuthResult {
    class Success(val cipher: Cipher) : BiometricAuthResult
    data object Cancelled : BiometricAuthResult
    data class Failed(val message: String) : BiometricAuthResult
}

/**
 * Shows the system biometric prompt for a Keystore-bound [cipher]. Only a successful strong
 * biometric match authorizes the cipher; there is no device-credential fallback, so the fallback
 * is always the master passphrase. Must be called on the main thread.
 */
suspend fun FragmentActivity.authenticate(
    cipher: Cipher,
    title: String,
    subtitle: String,
    negativeButton: String,
): BiometricAuthResult = suspendCancellableCoroutine { continuation ->
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            if (!continuation.isActive) return
            val authenticated = result.cryptoObject?.cipher
            continuation.resume(
                if (authenticated != null) BiometricAuthResult.Success(authenticated)
                else BiometricAuthResult.Failed("Biometric authentication did not return a key."),
            )
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (!continuation.isActive) return
            val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            continuation.resume(
                if (cancelled) BiometricAuthResult.Cancelled else BiometricAuthResult.Failed(errString.toString()),
            )
        }
    }
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(negativeButton)
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setConfirmationRequired(false)
        .build()
    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    continuation.invokeOnCancellation { prompt.cancelAuthentication() }
}
