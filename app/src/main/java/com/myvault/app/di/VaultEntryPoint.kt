package com.myvault.app.di

import com.myvault.app.data.db.VaultDatabase
import com.myvault.app.data.SecuritySettingsRepository
import com.myvault.app.security.AutoLockManager
import com.myvault.app.util.SecureClipboard
import com.myvault.app.security.VaultSession
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Access to app singletons from code that Hilt does not construct (instrumented tests). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VaultEntryPoint {
    fun database(): VaultDatabase
    fun session(): VaultSession
    fun autoLockManager(): AutoLockManager
    fun secureClipboard(): SecureClipboard
    fun securitySettings(): SecuritySettingsRepository
}
