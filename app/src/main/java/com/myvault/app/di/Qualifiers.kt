package com.myvault.app.di

import javax.inject.Qualifier

/** Dispatcher for blocking storage and Keystore calls. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatcher for the CPU- and memory-heavy Argon2id derivation. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KdfDispatcher

/** Scope that lives as long as the process. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
