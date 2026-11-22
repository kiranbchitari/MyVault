package com.myvault.app.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface DriveAuthorization {
    /** A short-lived OAuth access token limited to files this app created. */
    class Granted(val accessToken: String) : DriveAuthorization

    /** The user has to approve access; launch [consent] from an activity. */
    class ConsentRequired(val consent: PendingIntent) : DriveAuthorization
    class Failed(val message: String) : DriveAuthorization
}

/**
 * Google authorization for Drive through Google Identity Services, requesting only the
 * `drive.file` scope. MyVault never sees the Google password, and stores no token: a fresh one
 * is requested from Play services whenever a backup needs it.
 */
@Singleton
class DriveAuthorizer @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val request: AuthorizationRequest
        get() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveClient.SCOPE))).build()

    suspend fun authorize(): DriveAuthorization = try {
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        val consent = result.pendingIntent
        val token = result.accessToken
        when {
            result.hasResolution() && consent != null -> DriveAuthorization.ConsentRequired(consent)
            token != null -> DriveAuthorization.Granted(token)
            else -> DriveAuthorization.Failed("Google did not return access to Drive.")
        }
    } catch (e: Exception) {
        DriveAuthorization.Failed(describe(e))
    }

    /** Reads the result of the consent screen launched from [DriveAuthorization.ConsentRequired]. */
    fun fromConsentResult(data: Intent?): DriveAuthorization = try {
        val token = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
        if (token != null) DriveAuthorization.Granted(token) else DriveAuthorization.Failed("Access to Google Drive was not granted.")
    } catch (e: ApiException) {
        DriveAuthorization.Failed(describe(e))
    }

    /** Makes Play services forget a token, so the next request fetches a new one. */
    suspend fun clearToken(token: String) {
        runCatching { Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    private fun describe(e: Exception): String = when ((e as? ApiException)?.statusCode) {
        7 -> "No internet connection."
        10 -> "Google sign-in is not configured for this build of MyVault. See the README for the one-time Google Cloud setup."
        16 -> "Google Drive access was cancelled."
        else -> "Could not connect to Google Drive."
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWith(Result.failure(it)) }
        addOnCanceledListener { continuation.cancel() }
    }
}
