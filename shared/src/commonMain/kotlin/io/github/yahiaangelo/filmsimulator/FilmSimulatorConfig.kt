package io.github.yahiaangelo.filmsimulator

import io.github.yahiaangelo.filmsimulator.util.AppContext

class FilmSimulatorConfig(
    val appContext: AppContext
)

object FilmSimulator {
    fun initialize(config: FilmSimulatorConfig) {
        val commonContext = config.appContext
    }
}