package io.github.yahiaangelo.filmsimulator.data.source

import io.github.yahiaangelo.filmsimulator.data.source.local.SettingsStorage
import org.koin.dsl.bind
import org.koin.dsl.module

val settingsRepoModule = module {
    single { SettingsRepositoryImpl(get()) } bind SettingsRepository::class
}
class SettingsRepositoryImpl(
    private val settingsStorage: SettingsStorage
): SettingsRepository {
    override fun getSettings(): SettingsStorage = settingsStorage

}