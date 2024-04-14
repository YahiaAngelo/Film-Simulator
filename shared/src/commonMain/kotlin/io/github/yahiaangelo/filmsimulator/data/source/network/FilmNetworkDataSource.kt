package io.github.yahiaangelo.filmsimulator.data.source.network

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val filmNetworkDataSourceModule = module {
    single { FilmNetworkDataSource(get()) }
}
// Film Luts Github Repo
const val GITHUB_BASE_URL = "https://github.com/YahiaAngelo/Film-Luts/raw/main/"

class FilmNetworkDataSource(appHttpClient: AppHttpClient) {

    private val httpClient = appHttpClient.httpClient

    suspend fun getFilmLuts(): List<NetworkFilmLut> {
        return Json.decodeFromString<FilmLutsJsonResponse>(httpClient.get("${GITHUB_BASE_URL}film_luts.json").bodyAsText()).filmLuts
    }

    suspend fun getLutCube(lutFile: String): ByteArray {
        return httpClient.get("$GITHUB_BASE_URL$lutFile").body()
    }
}