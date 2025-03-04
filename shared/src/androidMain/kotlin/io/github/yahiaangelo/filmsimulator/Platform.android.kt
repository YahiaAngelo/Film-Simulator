package io.github.yahiaangelo.filmsimulator

import android.os.Build

class AndroidPlatform : Platform {
    override val name = PlatformName.ANDROID
}

actual fun getPlatform(): Platform = AndroidPlatform()
actual fun getAndroidSdkVersion(): Int {
    return Build.VERSION.SDK_INT

}