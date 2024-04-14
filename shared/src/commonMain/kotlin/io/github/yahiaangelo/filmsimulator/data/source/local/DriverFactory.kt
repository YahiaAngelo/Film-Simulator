package io.github.yahiaangelo.filmsimulator.data.source.local

import app.cash.sqldelight.db.SqlDriver

/**
 * A db driver interface to create a db driver suited for every platform
 */
expect class DriverFactory {
    fun createDriver(): SqlDriver
}
