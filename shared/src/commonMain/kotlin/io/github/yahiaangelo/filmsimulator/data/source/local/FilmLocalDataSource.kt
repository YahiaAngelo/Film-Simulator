package io.github.yahiaangelo.filmsimulator.data.source.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.github.yahiaangelo.filmsimulator.FilmLut
import io.github.yahiaangelo.filmsimulator.LutCube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import org.koin.dsl.module

val filmLocalDataSourceModule = module {
    single { FilmLocalDataSource(get()) }
}

internal class FilmLocalDataSource(appDatabase: AppDatabase){

    private val filmQueries = appDatabase.database.filmLutQueries
    private val lutQueries = appDatabase.database.lutCubeQueries


    internal fun observeFilms(): Flow<List<FilmLut>> {
        return filmQueries.selectAllFilmLuts()
            .asFlow()
            .mapToList(Dispatchers.IO)

    }

    internal fun getFilms(): List<FilmLut> {
        return filmQueries.selectAllFilmLuts().executeAsList()
    }

    internal fun getFilmStream(name: String): Flow<FilmLut?> {
        return filmQueries.selectByName(name).asFlow().mapToOneOrNull(Dispatchers.IO)
    }

    internal fun createFilmLuts(filmLuts: List<FilmLut>) {
        filmQueries.transaction {
            filmLuts.forEach {
                insertFilmLut(it)
            }
        }
    }

    internal fun clearFilmDatabase() {
        filmQueries.transaction {
            filmQueries .removeAllFilmLuts()
        }
    }

    private fun insertFilmLut(filmLut: FilmLut) {
        filmQueries.insertFilmLut(name = filmLut.name, category = filmLut.category, image_url = filmLut.image_url, lut_name = filmLut.lut_name)
    }


    internal fun getAllLutCubes(): Flow<List<LutCube>> {
        return lutQueries.selectAllLutCubes()
            .asFlow()
            .mapToList(Dispatchers.IO)

    }

    internal fun observeLutCubeByName(name: String): Flow<LutCube?> {
        return lutQueries.selectByName(name)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
    }

    internal fun getLutCubeByName(name: String): LutCube? {
        return lutQueries.selectByName(name)
            .executeAsOneOrNull()
    }

    internal fun createLutCubes(filmLuts: List<LutCube>) {
        lutQueries.transaction {
            filmLuts.forEach {
                insertLutCube(it)
            }
        }
    }

    internal fun clearLutDatabase() {
        lutQueries.transaction {
            lutQueries.removeAllLutCubes()
        }
    }

    private fun insertLutCube(lutCube: LutCube) {
        lutQueries.insertLutCube(name = lutCube.name, file_ = lutCube.file_)
    }
}