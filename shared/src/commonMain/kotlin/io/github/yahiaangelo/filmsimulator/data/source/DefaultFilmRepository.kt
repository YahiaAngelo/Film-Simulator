package io.github.yahiaangelo.filmsimulator.data.source

import androidx.compose.ui.graphics.ImageBitmap
import io.github.yahiaangelo.filmsimulator.FilmLut
import io.github.yahiaangelo.filmsimulator.LutCube
import io.github.yahiaangelo.filmsimulator.data.source.local.FilmLocalDataSource
import io.github.yahiaangelo.filmsimulator.data.source.network.FilmNetworkDataSource
import io.github.yahiaangelo.filmsimulator.util.readPixels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.dsl.bind
import org.koin.dsl.module
import util.apply3dLut
import util.readImageFile
import util.saveImageFile
import util.saveLutFile

val filmRepoModule = module {
    single { DefaultFilmRepository(get(), get()) } bind FilmRepository::class
}
internal class DefaultFilmRepository(
    private val networkDataSource: FilmNetworkDataSource,
    private val localDataSource: FilmLocalDataSource
): FilmRepository {

    override fun getFilmsStream(): Flow<List<FilmLut>> {
        return localDataSource.observeFilms()
    }

    override suspend fun getFilms(forceUpdate: Boolean): List<FilmLut> {
        if (forceUpdate) refresh()
        return withContext(Dispatchers.IO) {
            localDataSource.getFilms()
        }
    }

    override suspend fun refresh() {
        downloadFilmLuts()
    }

    override suspend fun downloadFilmLuts(): List<FilmLut> {
        return withContext(Dispatchers.IO) {
            val remoteFilms = networkDataSource.getFilmLuts()
            val localFilms = remoteFilms.toLocal()
            localDataSource.clearFilmDatabase()
            localDataSource.createFilmLuts(localFilms)
            localFilms
        }
    }

    override fun getFilmStream(name: String): Flow<FilmLut?> {
        return localDataSource.getFilmStream(name = name)
    }

    override suspend fun createFilm(name: String, category: String, thumbnail: String, lut: String) {
        withContext(Dispatchers.IO) {
            localDataSource.createFilmLuts(listOf(
                FilmLut(name = name, category = category, image_url = thumbnail, lut_name = lut)
            ))
        }
    }

    override suspend fun getLutCube(name: String): LutCube? {
        return withContext(Dispatchers.IO) {
            localDataSource.getLutCubeByName(name = name)
        }
    }

    override suspend fun saveLutCube(name: String, lutFile: ByteArray) {
        withContext(Dispatchers.IO) {
            localDataSource.createLutCubes(listOf(
                LutCube(name = name, file_ = lutFile)
            ))
        }
    }

    override suspend fun downloadLutCube(name: String) {
        withContext(Dispatchers.IO) {
            val lutFile = networkDataSource.getLutCube(name)
            saveLutCube(name = name, lutFile = lutFile)
        }
    }


    override suspend fun applyFilmLut(scope: CoroutineScope, filmLut: FilmLut, imageBitmap: ImageBitmap, onComplete: (ImageBitmap) -> Unit){
       var lutCube = getLutCube(filmLut.lut_name)
        if (lutCube == null) {
            downloadLutCube(filmLut.lut_name)
            lutCube = getLutCube(filmLut.lut_name)
        }

        applyLutFile(scope = scope, lutCube = lutCube!!, imageBitmap = imageBitmap) {
            onComplete(it)
        }
    }

    private suspend fun applyLutFile(scope: CoroutineScope, lutCube: LutCube, imageBitmap: ImageBitmap, onComplete: (ImageBitmap) -> Unit) {
        withContext(Dispatchers.IO) {
            val inputFile = "image.jpeg".also { saveImageFile(fileName = it, image = imageBitmap.readPixels()) }
            val lutFile = "lut.cube".also { saveLutFile(fileName = it, lut = lutCube.file_) }
            val outputFile = "image-new.jpeg"
            apply3dLut(inputFile = inputFile, lutFile = lutFile, outputFile = outputFile) {
                scope.launch {
                    val resultImage = withContext(Dispatchers.IO) {
                        readImageFile(outputFile)
                    }
                    onComplete(resultImage)
                }
            }
        }
    }
}