package io.github.yahiaangelo.filmsimulator.data.source

import io.github.yahiaangelo.filmsimulator.FavoriteLut
import io.github.yahiaangelo.filmsimulator.FilmLut
import io.github.yahiaangelo.filmsimulator.LutCube
import io.github.yahiaangelo.filmsimulator.data.source.local.FilmLocalDataSource
import io.github.yahiaangelo.filmsimulator.data.source.network.FilmNetworkDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.koin.dsl.bind
import org.koin.dsl.module
import util.EDITED_IMAGE_FILE_NAME
import util.apply3dLut
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


    override suspend fun applyFilmLut(scope: CoroutineScope, filmLut: FilmLut, image: String, onComplete: (String) -> Unit, onError: (String) -> Unit){
       var lutCube = getLutCube(filmLut.lut_name)
        if (lutCube == null) {
            downloadLutCube(filmLut.lut_name)
            lutCube = getLutCube(filmLut.lut_name)
        }

        applyLutFile(scope = scope, lutCube = lutCube!!, image = image, onComplete = {
            onComplete(it)
        }, onError = onError)
    }

    private suspend fun applyLutFile(scope: CoroutineScope, lutCube: LutCube, image: String, onComplete: (String) -> Unit, onError: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val lutFile = "lut.cube".also { saveLutFile(fileName = it, lut = lutCube.file_) }
            //TODO refactor this function to take input and output file names
            val outputFile = EDITED_IMAGE_FILE_NAME
            apply3dLut(
                inputFile = image,
                lutFile = lutFile,
                outputFile = outputFile,
                onComplete = { onComplete(outputFile) },
                onError = onError)
        }
    }

    override fun getFavoriteFilmsStream(): Flow<List<FavoriteLut>> {
        return localDataSource.observeFavoriteLuts()
    }

    override suspend fun getFavoriteFilms(): List<FavoriteLut> {
        return withContext(Dispatchers.IO) {
            localDataSource.getFavoriteLuts()
        }
    }

    override fun getFavoriteFilmStream(name: String): Flow<FavoriteLut?> {
        return localDataSource.getFavoriteLutStream(name)
    }

    override suspend fun addFavoriteFilm(filmLut: FavoriteLut): List<FavoriteLut> {
        return withContext(Dispatchers.IO) {
            localDataSource.addFavoriteLut(filmLut)
        }
    }

    override suspend fun removeFavoriteFilm(name: String): List<FavoriteLut> {
        return withContext(Dispatchers.IO) {
            localDataSource.removeFavoriteLut(name)
        }
    }

    override suspend fun clearFavoriteFilms() {
        withContext(Dispatchers.IO) {
            localDataSource.clearFavoriteDatabase()
        }
    }
}