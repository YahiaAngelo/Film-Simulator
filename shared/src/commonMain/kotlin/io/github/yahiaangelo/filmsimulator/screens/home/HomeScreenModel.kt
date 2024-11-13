package screens.home

import androidx.compose.ui.graphics.ImageBitmap
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.preat.peekaboo.image.picker.toImageBitmap
import io.github.yahiaangelo.filmsimulator.FilmLut
import io.github.yahiaangelo.filmsimulator.data.source.FilmRepository
import io.github.yahiaangelo.filmsimulator.util.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.dsl.module
import util.saveImageToGallery

val homeScreenModule = module {
    factory { HomeScreenModel(get()) }
}

/**
 * UiState for the Main Screen
 */
data class HomeUiState(
    val image: ImageBitmap? = null,
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

    private val _originalImage: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)
    private val _editedImage: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)

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
                        repository.applyFilmLut(scope = screenModelScope, filmLut = filmLut, imageBitmap = image) {resultImage ->
                            screenModelScope.launch { _editedImage.emit(resultImage) }
                            updateUiState { it.copy(image = resultImage, lut = filmLut) }
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

    fun onImagePickerResult(byteArrays: List<ByteArray>) {
        byteArrays.firstOrNull()?.let {
            screenModelScope.launch {
                val image = it.toImageBitmap()
                _originalImage.emit(image)
                _editedImage.emit(image)
                updateUiState { it.copy(image = image) }
            }
        }
    }

    fun showFilmLutsBottomSheet() {
        updateUiState { it.copy(showBottomSheet = true) }
    }

    fun dismissFilmLutBottomSheet() {
        updateUiState { it.copy(showBottomSheet = false) }
    }

    fun snackbarMessageShown() {
        updateUiState { it.copy(userMessage = null) }
    }

    fun showOriginalImage(show: Boolean) {
        screenModelScope.launch {
            val targetImage = if (show) _originalImage.value else _editedImage.value
            targetImage?.let { updateUiState { it.copy(image = targetImage) } }
        }
    }

    fun resetImage() {
        _originalImage.value?.let {originalImage ->
            updateUiState { it.copy(image = originalImage, lut = null) }
        }
    }

    fun exportImage() {
        _editedImage.value?.let {
            screenModelScope.launch {
                try {
                    updateUiState { it.copy(isLoading = true, loadingMessage = "Exporting image...") }
                    saveImageToGallery(it, appContext = AppContext)
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