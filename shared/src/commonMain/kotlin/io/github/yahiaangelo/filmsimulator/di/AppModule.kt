package di


import io.github.yahiaangelo.filmsimulator.data.source.filmRepoModule
import io.github.yahiaangelo.filmsimulator.data.source.local.appDBModule
import io.github.yahiaangelo.filmsimulator.data.source.local.filmLocalDataSourceModule
import io.github.yahiaangelo.filmsimulator.data.source.network.filmNetworkDataSourceModule
import io.github.yahiaangelo.filmsimulator.data.source.network.httpClientModule
import screens.home.homeScreenModule


fun appModule() = listOf(
    homeScreenModule,
    httpClientModule,
    appDBModule,
    filmLocalDataSourceModule,
    filmNetworkDataSourceModule,
    filmRepoModule
)