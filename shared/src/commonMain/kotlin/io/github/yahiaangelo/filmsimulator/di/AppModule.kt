package di


import io.github.yahiaangelo.filmsimulator.data.source.filmRepoModule
import io.github.yahiaangelo.filmsimulator.data.source.local.appDBModule
import io.github.yahiaangelo.filmsimulator.data.source.local.filmLocalDataSourceModule
import io.github.yahiaangelo.filmsimulator.data.source.local.settingsStorageImplModule
import io.github.yahiaangelo.filmsimulator.data.source.network.filmNetworkDataSourceModule
import io.github.yahiaangelo.filmsimulator.data.source.network.httpClientModule
import io.github.yahiaangelo.filmsimulator.data.source.settingsRepoModule
import io.github.yahiaangelo.filmsimulator.lut.lutDownloadManagerModule
import io.github.yahiaangelo.filmsimulator.screens.settings.settingsScreenModel
import screens.home.homeScreenModule

/**
 * DI modules
 */
fun appModule() = listOf(
    homeScreenModule,
    httpClientModule,
    appDBModule,
    filmLocalDataSourceModule,
    filmNetworkDataSourceModule,
    filmRepoModule,
    settingsScreenModel,
    settingsRepoModule,
    settingsStorageImplModule,
    lutDownloadManagerModule,
)