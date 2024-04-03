package io.github.yahiaangelo.filmsimulator.data.source.local


interface SettingsStorage {

    var exportQuality: Int

    fun cleanStorage()
}

enum class StorageKeys {
    EXPORT_QUALITY;

    val key get() = this.name
}