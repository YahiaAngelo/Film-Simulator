package io.github.yahiaangelo.filmsimulator.data.source.local

import io.github.yahiaangelo.filmsimulator.Database
import io.github.yahiaangelo.filmsimulator.util.AppContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appDBModule = module {
    singleOf(::AppDatabase)
    singleOf(::DriverFactory)
}

/**
 * Main endpoint for the app's local database
 */
internal class AppDatabase(driverFactory: DriverFactory) {
    val database = Database(driverFactory.createDriver(appContext = AppContext))
}