package io.github.yahiaangelo.filmsimulator.data.source.local

import io.github.yahiaangelo.filmsimulator.screens.settings.DefaultPickerType


interface SettingsStorage {

    var exportQuality: Int
    var defaultPicker: DefaultPickerType
    var hasShownLutDownloadDialog: Boolean
    fun defaultPickerListener(callback: (DefaultPickerType) -> Unit)

    fun cleanStorage()
}

enum class StorageKeys {
    EXPORT_QUALITY,
    HAS_SHOWN_LUT_DOWNLOAD_DIALOG,
    DEFAULT_PICKER;
    val key get() = this.name
}