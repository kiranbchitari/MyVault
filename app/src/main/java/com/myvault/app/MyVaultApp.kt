package com.myvault.app

import android.app.Application
import com.myvault.app.security.AutoLockManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyVaultApp : Application() {

    @Inject
    lateinit var autoLockManager: AutoLockManager

    override fun onCreate() {
        super.onCreate()
        autoLockManager.start()
    }
}
