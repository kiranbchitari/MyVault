package com.myvault.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private object Routes {
    const val WELCOME = "welcome"
    const val CREATE = "create"
    const val CONFIRM = "confirm"
    const val SECURITY = "security"
}

/** First-run flow: Welcome, Create passphrase, Confirm passphrase, Security setup. */
@Composable
fun OnboardingFlow(
    onVaultCreationStarted: () -> Unit,
    onRestoreChosen: (Boolean) -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Deriving the key takes a moment; don't let Back interrupt it half-way.
    BackHandler(enabled = uiState.creating) {}

    NavHost(
        navController = navController,
        startDestination = Routes.WELCOME,
        enterTransition = { slideInHorizontally(tween(280)) { it / 8 } + fadeIn(tween(280)) },
        exitTransition = { fadeOut(tween(140)) },
        popEnterTransition = { slideInHorizontally(tween(280)) { -it / 8 } + fadeIn(tween(280)) },
        popExitTransition = { slideOutHorizontally(tween(200)) { it / 8 } + fadeOut(tween(140)) },
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onStart = {
                    onRestoreChosen(false)
                    navController.navigate(Routes.CREATE)
                },
                onRestore = {
                    onRestoreChosen(true)
                    navController.navigate(Routes.CREATE)
                },
            )
        }
        composable(Routes.CREATE) {
            CreatePassphraseScreen(
                onBack = {
                    viewModel.clearCandidate()
                    navController.popBackStack()
                },
                onContinue = { passphrase ->
                    viewModel.setCandidate(passphrase)
                    navController.navigate(Routes.CONFIRM)
                },
            )
        }
        composable(Routes.CONFIRM) {
            ConfirmPassphraseScreen(
                onBack = { navController.popBackStack() },
                matches = viewModel::matchesCandidate,
                onConfirmed = { navController.navigate(Routes.SECURITY) },
            )
        }
        composable(Routes.SECURITY) {
            SecuritySetupScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onAutoLockSelected = viewModel::setAutoLock,
                onCreate = {
                    onVaultCreationStarted()
                    viewModel.createVault()
                },
            )
        }
    }
}
