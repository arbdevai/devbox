package com.arbdev.devbox.backend

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Modul Koin untuk Flavor No-Root.
 * Menyediakan implementasi PRootBackend userspace.
 */
val backendModule = module {
    single<LinuxEnvironment> { PRootBackend(androidContext()) }
}
