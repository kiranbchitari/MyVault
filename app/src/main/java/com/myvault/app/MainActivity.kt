package com.myvault.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.myvault.app.security.AutoLockManager
import com.myvault.app.ui.MyVaultRoot
import com.myvault.app.ui.theme.MyVaultTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Single activity. A FragmentActivity because androidx.biometric's prompt requires one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var autoLockManager: AutoLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!BuildConfig.ALLOW_SCREENSHOTS) {
            // Blocks screenshots, screen recording and casting, and blanks the Recents preview.
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        }
        setContent {
            MyVaultTheme {
                MyVaultRoot()
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        autoLockManager.onUserInteraction()
    }
}
