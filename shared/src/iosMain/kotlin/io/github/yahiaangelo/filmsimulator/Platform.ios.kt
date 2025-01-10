package io.github.yahiaangelo.filmsimulator

import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name = PlatformName.IOS
}

actual fun getPlatform(): Platform = IOSPlatform()