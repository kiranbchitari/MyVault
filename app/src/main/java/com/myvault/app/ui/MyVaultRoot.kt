package com.myvault.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvault.app.security.VaultState
import com.myvault.app.ui.components.VaultBackground
import com.myvault.app.ui.components.VaultLogo
import com.myvault.app.ui.home.BiometricSetupScreen
import com.myvault.app.ui.main.MainApp
import com.myvault.app.ui.onboarding.OnboardingFlow
import com.myvault.app.ui.unlock.UnlockScreen

private enum class RootDestination(val showsVaultContent: Boolean) {
    SPLASH(false), ONBOARDING(false), UNLOCK(false), BIOMETRIC_SETUP(true), HOME(true)
}

/**
 * Top-level switch driven only by the vault's state. Unlocked content exists in the composition
 * solely while the session is unlocked; there is no route that can reach it while locked.
 */
@Composable
fun MyVaultRoot(viewModel: RootViewModel = hiltViewModel()) {
    val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
    val biometricOfferPending by viewModel.biometricOfferPending.collectAsStateWithLifecycle()
    val restorePending by viewModel.restorePending.collectAsStateWithLifecycle()

    val destination = when (vaultState) {
        null -> RootDestination.SPLASH
        VaultState.NO_VAULT -> RootDestination.ONBOARDING
        VaultState.LOCKED -> RootDestination.UNLOCK
        VaultState.UNLOCKED -> if (biometricOfferPending) RootDestination.BIOMETRIC_SETUP else RootDestination.HOME
    }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            if (initialState.showsVaultContent && !targetState.showsVaultContent) {
                // Locking removes vault content immediately, with no fade-out frames.
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(tween(260)) togetherWith fadeOut(tween(140))
            }
        },
        label = "root",
    ) { target ->
        when (target) {
            RootDestination.SPLASH -> SplashScreen()
            RootDestination.ONBOARDING -> OnboardingFlow(
                onVaultCreationStarted = viewModel::offerBiometricsAfterSetup,
                onRestoreChosen = viewModel::restoreAfterSetup,
            )
            RootDestination.UNLOCK -> UnlockScreen()
            RootDestination.BIOMETRIC_SETUP -> BiometricSetupScreen(onDone = viewModel::biometricOfferHandled)
            RootDestination.HOME -> MainApp(startAtRestore = restorePending, onStartHandled = { viewModel.restoreAfterSetup(false) })
        }
    }
}

@Composable
private fun SplashScreen() {
    VaultBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            VaultLogo(size = 96.dp)
        }
    }
}
