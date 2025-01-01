package io.github.yahiaangelo.filmsimulator.util

import platform.Foundation.NSBundle

actual class Platform actual constructor(appContext: AppContext) {
    actual fun getAppVersion(): String {
        return platform.Foundation.NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "Unknown"
    }
}