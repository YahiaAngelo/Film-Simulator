package io.github.yahiaangelo.filmsimulator

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform