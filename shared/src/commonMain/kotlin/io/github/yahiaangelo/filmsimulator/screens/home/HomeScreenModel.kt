package screens.home

import androidx.compose.ui.graphics.ImageBitmap
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vinceglb.filekit.core.PlatformFile
import io.github.yahiaangelo.filmsimulator.FilmLut
import io.github.yahiaangelo.filmsimulator.data.source.FilmRepository
import io.github.yahiaangelo.filmsimulator.util.AppContext
import io.github.yahiaangelo.filmsimulator.util.fixImageOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.koin.dsl.module
import util.EDITED_IMAGE_FILE_NAME
import util.IMAGE_FILE_NAME
import util.saveImageFile
import util.saveImageToGallery

val homeScreenModule = module {
    factory { HomeScreenModel(get()) }
}

/**
 * UiState for the Main Screen
 */
data class HomeUiState(
    val image: String? = null,
    val lut: FilmLut? = null,
    val filmLutsList: List<FilmLut> = emptyList(),
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val userMessage: String? = null,
    val showBottomSheet: Boolean = false
)

/**
 * ViewModel for the Main Screen
 */
data class HomeScreenModel(val repository: FilmRepository) : ScreenModel {

    private val _uiState: MutableStateFlow<HomeUiState> = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        refresh()
    }
    private fun updateUiState(update: (HomeUiState) -> HomeUiState) {
        _uiState.value = update(_uiState.value)
    }

    private val _originalImage: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _editedImage: MutableStateFlow<String?> = MutableStateFlow(null)

    fun refresh() {
        screenModelScope.launch {
            try {
                updateUiState { it.copy(isLoading = true, loadingMessage = "Refreshing data...") }
                repository.refresh()
                val newFilmList = repository.getFilmsStream().first()
                updateUiState { it.copy(filmLutsList = newFilmList, userMessage = "Data refreshed successfully.") }
            } catch (e: Exception) {
                updateUiState { it.copy(userMessage = "Error refreshing data: ${e.message}") }
            } finally {
                updateUiState { it.copy(isLoading = false) }
            }
        }
    }

    fun selectFilmLut(filmLut: FilmLut) {
        _originalImage.value?.let { image ->
            screenModelScope.launch {
                try {
                    updateUiState { it.copy(isLoading = true, loadingMessage = "Applying Film LUT...") }
                    withContext(Dispatchers.IO) {
                        repository.applyFilmLut(scope = screenModelScope, filmLut = filmLut, image = image) {resultImage ->
                            screenModelScope.launch { _editedImage.emit(resultImage) }
                            updateUiState { it.copy(lut = filmLut) }
                            emitImage(resultImage)
                        }
                    }
                } catch (e: Exception) {
                    updateUiState { it.copy(userMessage = "Error applying LUT: ${e.message}") }
                } finally {
                    updateUiState { it.copy(isLoading = false, showBottomSheet = false) }
                }
            }
        }
    }

    fun onImagePickerResult(file: PlatformFile?) {
        file?.let {
            screenModelScope.launch {
                saveImageFile(IMAGE_FILE_NAME, it.readBytes())
                saveImageFile(EDITED_IMAGE_FILE_NAME, it.readBytes())
                fixImageOrientation(image = IMAGE_FILE_NAME)
                _originalImage.emit(IMAGE_FILE_NAME)
                _editedImage.emit(IMAGE_FILE_NAME)
                emitImage(IMAGE_FILE_NAME)
                _uiState.value.lut?.let { selectFilmLut(it) }
            }
        }
    }

    fun showFilmLutsBottomSheet() {
        updateUiState { it.copy(showBottomSheet = true) }
    }

    fun dismissFilmLutBottomSheet() {
        updateUiState { it.copy(showBottomSheet = false) }
    }

    fun emitImage(image: String) {
        updateUiState { it.copy(image = "$image?${Clock.System.now().epochSeconds}") }
    }

    fun snackbarMessageShown() {
        updateUiState { it.copy(userMessage = null) }
    }

    fun showOriginalImage(show: Boolean) {
        screenModelScope.launch {
            val targetImage = if (show) _originalImage.value else _editedImage.value
            targetImage?.let { emitImage(targetImage) }
        }
    }

    fun resetImage() {
        _originalImage.value?.let {originalImage ->
            updateUiState { it.copy(lut = null) }
            emitImage(originalImage)
        }
    }

    fun exportImage() {
        _editedImage.value?.let {
            screenModelScope.launch {
                try {
                    updateUiState { it.copy(isLoading = true, loadingMessage = "Exporting image...") }
                    saveImageToGallery(EDITED_IMAGE_FILE_NAME, appContext = AppContext)
                    updateUiState { it.copy(userMessage = "Image exported successfully.") }
                } catch (e: Exception) {
                    updateUiState { it.copy(userMessage = "Error exporting image: ${e.message}") }
                } finally {
                    updateUiState { it.copy(isLoading = false) }
                }
            }
        } ?: updateUiState { it.copy(userMessage = "Please choose an image first.") }
    }
}