package io.github.yahiaangelo.filmsimulator.screens.home

import cafe.adriel.voyager.core.model.ScreenModel
import io.github.yahiaangelo.filmsimulator.data.source.FilmRepository
import io.github.yahiaangelo.filmsimulator.data.source.SettingsRepository
import io.github.yahiaangelo.filmsimulator.lut.LutDownloadManager
import io.github.yahiaangelo.filmsimulator.util.AppContext
import io.github.yahiaangelo.filmsimulator.util.FeatureFlags
import com.plusmobileapps.konnectivity.Konnectivity
import screens.home.HomeScreenModel

/**
 * Factory to create the appropriate HomeScreenModel based on feature flags
 */
object HomeScreenModelFactory {
    fun create(
        repository: FilmRepository,
        konnectivity: Konnectivity,
        settingsRepository: SettingsRepository,
        appContext: AppContext,
        lutDownloadManager: LutDownloadManager
    ): ScreenModel {
        return if (FeatureFlags.USE_REALTIME_PROCESSING) {
            // Use optimized real-time processing model
            HomeScreenModelOptimized(
                repository = repository,
                settingsRepository = settingsRepository,
                AppContext = appContext,
                lutDownloadManager = lutDownloadManager
            )
        } else {
            // Use existing file-based processing model
            HomeScreenModel(
                repository = repository,
                settingsRepository = settingsRepository,
                lutDownloadManager = lutDownloadManager
            )
        }
    }
}