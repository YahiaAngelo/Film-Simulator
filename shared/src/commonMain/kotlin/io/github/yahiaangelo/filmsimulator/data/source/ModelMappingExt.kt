package io.github.yahiaangelo.filmsimulator.data.source

import io.github.yahiaangelo.filmsimulator.FilmLut
import io.github.yahiaangelo.filmsimulator.data.source.network.NetworkFilmLut


/*
Converts Network film lut response to Local [FilmLut]
 */
fun NetworkFilmLut.toLocal() = FilmLut(
    name = name,
    category = category,
    image_url = thumbnail,
    lut_name = lutFile
)

fun List<NetworkFilmLut>.toLocal() = map(NetworkFilmLut::toLocal)