package io.github.yahiaangelo.filmsimulator.data.source.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkFilmLut(@SerialName("name") val name: String,
                          @SerialName("lut_file") val lutFile: String,
                          @SerialName("category") val category: String,
                          @SerialName("thumbnail") val thumbnail: String
)
