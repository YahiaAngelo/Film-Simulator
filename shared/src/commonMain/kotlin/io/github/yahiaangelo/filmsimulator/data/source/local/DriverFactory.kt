package io.github.yahiaangelo.filmsimulator.data.source.local

import app.cash.sqldelight.db.SqlDriver
import io.github.yahiaangelo.filmsimulator.util.AppContext

/**
 * A db driver interface to create a db driver suited for every platform
 */
expect class DriverFactory() {
    fun createDriver(appContext: AppContext): SqlDriver
}
