package io.github.yahiaangelo.filmsimulator.data.source.local

import io.github.yahiaangelo.filmsimulator.screens.settings.DefaultPickerType
import io.github.yahiaangelo.filmsimulator.screens.settings.ExportFormat


interface SettingsStorage {

    var exportQuality: Int
    var exportFormat: ExportFormat
    var defaultPicker: DefaultPickerType
    var hasShownLutDownloadDialog: Boolean
    fun defaultPickerListener(callback: (DefaultPickerType) -> Unit)

    fun cleanStorage()
}

enum class StorageKeys {
    EXPORT_QUALITY,
    EXPORT_FORMAT,
    HAS_SHOWN_LUT_DOWNLOAD_DIALOG,
    DEFAULT_PICKER;
    val key get() = this.name
}