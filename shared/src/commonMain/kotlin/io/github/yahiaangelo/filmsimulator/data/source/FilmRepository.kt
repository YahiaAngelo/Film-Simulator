package io.github.yahiaangelo.filmsimulator.data.source

import androidx.compose.ui.graphics.ImageBitmap
import io.github.yahiaangelo.filmsimulator.FilmLut
import io.github.yahiaangelo.filmsimulator.LutCube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Interface to the film data layer.
 */
interface FilmRepository {

    fun getFilmsStream(): Flow<List<FilmLut>>

    suspend fun getFilms(forceUpdate: Boolean = false): List<FilmLut>

    suspend fun refresh()

    suspend fun downloadFilmLuts(): List<FilmLut>

    fun getFilmStream(name: String): Flow<FilmLut?>

    suspend fun createFilm(name: String, category: String, thumbnail: String, lut: String)

    suspend fun getLutCube(name: String): LutCube?

    suspend fun saveLutCube(name: String, lutFile: ByteArray)

    suspend fun downloadLutCube(name: String)

    suspend fun applyFilmLut(scope: CoroutineScope, filmLut: FilmLut, imageBitmap: ImageBitmap, onComplete: (ImageBitmap) -> Unit)
}