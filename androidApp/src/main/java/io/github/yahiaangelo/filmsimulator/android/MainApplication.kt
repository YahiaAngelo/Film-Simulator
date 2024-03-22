package io.github.yahiaangelo.filmsimulator.android

import android.app.Application
import di.appModule
import io.github.yahiaangelo.filmsimulator.FilmSimulator
import io.github.yahiaangelo.filmsimulator.FilmSimulatorConfig
import io.github.yahiaangelo.filmsimulator.util.AppContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MainApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@MainApplication)
            androidLogger()
            modules(appModule())
        }

        FilmSimulator.initialize(
            FilmSimulatorConfig(
                appContext = AppContext.apply { set(applicationContext) }
            )
        )
    }
}