package io.github.yahiaangelo.filmsimulator.data.source.local

import app.cash.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun createDriver(): SqlDriver
}
