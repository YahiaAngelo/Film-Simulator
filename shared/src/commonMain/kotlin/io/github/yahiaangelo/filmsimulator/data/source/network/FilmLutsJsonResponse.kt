package io.github.yahiaangelo.filmsimulator.data.source.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FilmLutsJsonResponse(
    @SerialName("filmLUTs") val filmLuts: List<NetworkFilmLut>
)