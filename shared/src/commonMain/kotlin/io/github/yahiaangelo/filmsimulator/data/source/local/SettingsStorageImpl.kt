package io.github.yahiaangelo.filmsimulator.data.source.local

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.observable.makeObservable
import com.russhwolf.settings.set
import io.github.yahiaangelo.filmsimulator.PlatformName
import io.github.yahiaangelo.filmsimulator.getPlatform
import io.github.yahiaangelo.filmsimulator.screens.settings.DefaultPickerType
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

    @OptIn(ExperimentalSettingsApi::class)
    private val observableSettings: ObservableSettings by lazy { settings.makeObservable() }

    override var hasShownLutDownloadDialog: Boolean
        get() = observableSettings[StorageKeys.HAS_SHOWN_LUT_DOWNLOAD_DIALOG.key] ?: false
        set(value) {
            observableSettings[StorageKeys.HAS_SHOWN_LUT_DOWNLOAD_DIALOG.key] = value
        }

    override var exportQuality: Int
        get() = observableSettings[StorageKeys.EXPORT_QUALITY.key] ?: 90
        set(value) {
            observableSettings[StorageKeys.EXPORT_QUALITY.key] = value
        }
    override var defaultPicker: DefaultPickerType
        get() = DefaultPickerType.valueOf(
            observableSettings[StorageKeys.DEFAULT_PICKER.key]
                ?: if (getPlatform().name == PlatformName.IOS) DefaultPickerType.IMAGES.name else DefaultPickerType.FILES.name
        )
        set(value) {
            observableSettings[StorageKeys.DEFAULT_PICKER.key] = value.name
        }

    override fun defaultPickerListener(callback: (DefaultPickerType) -> Unit) {
        observableSettings.addStringListener(
            StorageKeys.DEFAULT_PICKER.key,
            defaultValue = DefaultPickerType.IMAGES.name,
            callback = { newValue ->
                callback(DefaultPickerType.valueOf(newValue))
            })
    }

    override fun cleanStorage() {
        observableSettings.clear()
    }


}