package io.github.yahiaangelo.filmsimulator.data.source.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val httpClientModule = module {
    single { AppHttpClient() }
}

/**
 * Main Network Http client
 */
class AppHttpClient {
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                useAlternativeNames = false
            })
        }
    }
}