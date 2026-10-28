package com.arbdev.devbox

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

val devBoxAppModule = module {
    // Definisi dependensi Koin untuk arsitektur terstruktur & bebas memory leak
    single { com.arbdev.devbox.data.ChrootManager }
    single { com.arbdev.devbox.terminal.TerminalSessionManager }
    single { com.arbdev.devbox.data.RootExecutor }
}

class DevBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(this@DevBoxApplication)
                modules(devBoxAppModule)
            }
        } catch (_: Exception) {}
    }
}
