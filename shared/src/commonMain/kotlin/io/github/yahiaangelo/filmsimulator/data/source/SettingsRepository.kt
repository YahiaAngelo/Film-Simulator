package io.github.yahiaangelo.filmsimulator.data.source

import io.github.yahiaangelo.filmsimulator.data.source.local.SettingsStorage

interface SettingsRepository {

    fun getSettings(): SettingsStorage
}