package io.github.yahiaangelo.filmsimulator.data.source.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.yahiaangelo.filmsimulator.Database
import io.github.yahiaangelo.filmsimulator.util.AppContext

actual class DriverFactory {
    actual fun createDriver(appContext: AppContext): SqlDriver {
        return AndroidSqliteDriver(Database.Schema, appContext.get()!!, "film.db")
    }
}