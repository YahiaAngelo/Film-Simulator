package io.github.yahiaangelo.filmsimulator.util

expect class Platform(appContext: AppContext) {
    fun getAppVersion(): String
}