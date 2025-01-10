package io.github.yahiaangelo.filmsimulator

class AndroidPlatform : Platform {
    override val name = PlatformName.ANDROID
}

actual fun getPlatform(): Platform = AndroidPlatform()