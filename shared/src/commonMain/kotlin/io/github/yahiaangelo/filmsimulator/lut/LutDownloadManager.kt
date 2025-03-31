package io.github.yahiaangelo.filmsimulator.lut

import io.github.yahiaangelo.filmsimulator.data.source.FilmRepository
import io.github.yahiaangelo.filmsimulator.data.source.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.module

val lutDownloadManagerModule = module {
    single { LutDownloadManager(get(), get()) }
}

class LutDownloadManager(
    private val repository: FilmRepository,
    private val settingsRepository: SettingsRepository
) : KoinComponent {

    private val _uiState = MutableStateFlow(LutDownloadState())
    val uiState: StateFlow<LutDownloadState> = _uiState.asStateFlow()

    init {
        checkIfShouldShowDownloadDialog()
    }

    private fun checkIfShouldShowDownloadDialog() {
        val hasShownDialog = settingsRepository.getSettings().hasShownLutDownloadDialog
        if (!hasShownDialog) {
            _uiState.value = _uiState.value.copy(showDownloadDialog = true)
            settingsRepository.getSettings().hasShownLutDownloadDialog = true
        }
    }

    fun dismissDownloadDialog() {
        _uiState.value = _uiState.value.copy(showDownloadDialog = false)
    }

    fun confirmDownloadLuts(scope: CoroutineScope, onResult: (Boolean, String?) -> Unit) {
        _uiState.value = _uiState.value.copy(
            showDownloadDialog = false,
            showDownloadProgress = true
        )

        scope.launch {
            try {
                val success = repository.downloadAllLutCubes { current, total ->
                    _uiState.value = _uiState.value.copy(downloadProgress = current to total)
                }

                if (success) {
                    onResult(true, "All LUT files downloaded successfully")
                } else {
                    onResult(false, "Error downloading some LUT files")
                }
            } catch (e: Exception) {
                onResult(false, "Error downloading LUT files: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(showDownloadProgress = false)
            }
        }
    }

    fun showDownloadDialog() {
        _uiState.value = _uiState.value.copy(showDownloadDialog = true)
    }
}

data class LutDownloadState(
    val showDownloadDialog: Boolean = false,
    val showDownloadProgress: Boolean = false,
    val downloadProgress: Pair<Int, Int> = 0 to 0
)