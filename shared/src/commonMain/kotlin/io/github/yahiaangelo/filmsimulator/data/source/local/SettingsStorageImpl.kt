package io.github.yahiaangelo.filmsimulator.data.source.local

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import org.koin.dsl.bind
import org.koin.dsl.module

val settingsStorageImplModule = module {
    single { SettingsStorageImpl() } bind SettingsStorage::class
}

/**
 * Key-Value [SettingsStorage] implementation for App's settings
 */
class SettingsStorageImpl : SettingsStorage {

    private val settings: Settings by lazy { Settings() }
    override var exportQuality: Int
        get() = settings[StorageKeys.EXPORT_QUALITY.key] ?: 90
        set(value) {
            settings[StorageKeys.EXPORT_QUALITY.key] = value
        }

    override fun cleanStorage() {
        settings.clear()
    }


}