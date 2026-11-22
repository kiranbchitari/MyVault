package com.myvault.app.di

import android.content.Context
import androidx.room.Room
import com.myvault.app.data.RoomVaultMetadataStore
import com.myvault.app.data.RoomVaultStorage
import com.myvault.app.data.db.BackupMetadataDao
import com.myvault.app.data.db.Migrations
import com.myvault.app.data.db.SecuritySettingsDao
import com.myvault.app.data.db.VaultDatabase
import com.myvault.app.data.db.VaultMetadataDao
import com.myvault.app.security.VaultMetadataStore
import com.myvault.crypto.Argon2Params
import com.myvault.app.vault.VaultStorage
import com.myvault.crypto.PassphraseKeyslot
import com.myvault.crypto.PasswordGenerator
import com.myvault.crypto.VaultCipher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.security.SecureRandom
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun secureRandom(): SecureRandom = SecureRandom()

    @Provides
    @Singleton
    fun vaultCipher(random: SecureRandom): VaultCipher = VaultCipher(random)

    @Provides
    @Singleton
    fun passphraseKeyslot(random: SecureRandom, cipher: VaultCipher): PassphraseKeyslot = PassphraseKeyslot(random, cipher)

    @Provides
    fun kdfParams(): Argon2Params = Argon2Params.DEFAULT

    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): VaultDatabase =
        // No destructive fallback: a schema this build cannot migrate must fail loudly rather
        // than silently delete the vault.
        Room.databaseBuilder(context, VaultDatabase::class.java, VaultDatabase.FILE_NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun vaultMetadataDao(database: VaultDatabase): VaultMetadataDao = database.vaultMetadataDao()

    @Provides
    fun securitySettingsDao(database: VaultDatabase): SecuritySettingsDao = database.securitySettingsDao()

    @Provides
    fun backupMetadataDao(database: VaultDatabase): BackupMetadataDao = database.backupMetadataDao()

    @Provides
    @Singleton
    fun passwordGenerator(random: SecureRandom): PasswordGenerator = PasswordGenerator(random)

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @KdfDispatcher
    fun kdfDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StoreModule {
    @Binds
    abstract fun vaultMetadataStore(impl: RoomVaultMetadataStore): VaultMetadataStore

    @Binds
    abstract fun vaultStorage(impl: RoomVaultStorage): VaultStorage
}
