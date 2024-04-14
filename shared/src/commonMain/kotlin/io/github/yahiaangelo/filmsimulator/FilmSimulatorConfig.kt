package io.github.yahiaangelo.filmsimulator

import io.github.yahiaangelo.filmsimulator.util.AppContext

/**
 * Config file used to initiate context for the androidMain module
 */
class FilmSimulatorConfig(
    val appContext: AppContext
)

object FilmSimulator {
    fun initialize(config: FilmSimulatorConfig) {
        val commonContext = config.appContext
    }
}