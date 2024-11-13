package io.github.yahiaangelo.filmsimulator.data.source.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.github.yahiaangelo.filmsimulator.Database
import io.github.yahiaangelo.filmsimulator.util.AppContext

actual class DriverFactory {
    actual fun createDriver(appContext: AppContext): SqlDriver {
        return NativeSqliteDriver(Database.Schema, "film.db")
    }
}