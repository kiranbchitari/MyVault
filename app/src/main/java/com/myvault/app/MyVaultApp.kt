package com.myvault.app

import android.app.Application
import com.myvault.app.backup.BackupCoordinator
import com.myvault.app.security.AutoLockManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyVaultApp : Application() {

    @Inject
    lateinit var autoLockManager: AutoLockManager

    @Inject
    lateinit var backupCoordinator: BackupCoordinator

    override fun onCreate() {
        super.onCreate()
        autoLockManager.start()
        backupCoordinator.start()
    }
}
