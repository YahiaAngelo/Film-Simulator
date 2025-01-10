package io.github.yahiaangelo.filmsimulator.data.source.local

import io.github.yahiaangelo.filmsimulator.screens.settings.DefaultPickerType


interface SettingsStorage {

    var exportQuality: Int
    var defaultPicker: DefaultPickerType
    fun defaultPickerListener(callback: (DefaultPickerType) -> Unit)

    fun cleanStorage()
}

enum class StorageKeys {
    EXPORT_QUALITY,
    DEFAULT_PICKER;
    val key get() = this.name
}