package com.arbdev.devbox.backend

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Modul Koin untuk Flavor Root.
 * Menyediakan implementasi ChrootBackend native.
 */
val backendModule = module {
    single<LinuxEnvironment> { ChrootBackend(androidContext()) }
}
