package screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.preat.peekaboo.image.picker.toImageBitmap
import io.github.yahiaangelo.filmsimulator.FilmLut
import io.github.yahiaangelo.filmsimulator.FilmSimulatorConfig
import io.github.yahiaangelo.filmsimulator.data.source.FilmRepository
import io.github.yahiaangelo.filmsimulator.util.AppContext
import io.github.yahiaangelo.filmsimulator.util.Async
import io.github.yahiaangelo.filmsimulator.util.WhileUiSubscribed
import io.github.yahiaangelo.filmsimulator.util.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    private val _image: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)
    private val _originalImage: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)
    private val _editedImage: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)
    private val _filmLut: MutableStateFlow<FilmLut?> = MutableStateFlow(null)
    private val _userMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _isLoading = MutableStateFlow(false)
    private val _loadingMessage: MutableStateFlow<String> = MutableStateFlow("")
    private val _showBottomSheet = MutableStateFlow(false)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _filmLutsList = repository.getFilmsStream()
        .flatMapConcat { filmList ->
            if (filmList.isEmpty()) {
                // If the list is empty, attempt to download new Film Luts and then retry fetching the list
                flow<Async<List<FilmLut>>> {
                    _loadingMessage.emit("Updating Luts List")
                    emit(Async.Loading)
                    try {
                        repository.downloadFilmLuts() // Download new Film Luts.
                        val newFilmList = repository.getFilmsStream().first() // Retry fetching the list
                        emit(Async.Success(newFilmList)) // Emit the new list
                    } catch (e: Exception) {
                        emit(Async.Error("Error while downloading or loading Film Luts: ${e.message}"))
                    }
                }
            } else {
                // If the list is not empty, proceed as normal
                flowOf(Async.Success(filmList))
            }
        }
        .catch { emit(Async.Error("Error while loading Film Luts")) }


    val uiState: StateFlow<HomeUiState> = combine(
        _image, _filmLut, _userMessage, _isLoading, _loadingMessage, _showBottomSheet, _filmLutsList
    ) { image, filmLut, userMessage, isLoading, loadingMessage, showBottomSheet, filmLutsList ->

        when (filmLutsList) {
            Async.Loading -> {
                HomeUiState(isLoading = true, loadingMessage = loadingMessage)
            }

            is Async.Error -> {
                HomeUiState(userMessage = filmLutsList.errorMessage)
            }

            is Async.Success -> {
                HomeUiState(
                    image = image,
                    lut = filmLut,
                    filmLutsList = filmLutsList.data,
                    isLoading = false,
                    userMessage = userMessage,
                    showBottomSheet = showBottomSheet
                    )
            }
        }
    }
        .stateIn(
            scope = screenModelScope,
            started = WhileUiSubscribed,
            initialValue = HomeUiState(isLoading = true)
        )


    fun refresh() {
        screenModelScope.launch {
            repository.refresh()
        }
    }


    fun selectFilmLut(filmLut: FilmLut) {
        screenModelScope.launch {
            _originalImage.value?.let {
                _loadingMessage.emit("Applying Film Lut")
                _isLoading.emit(true)
                repository.applyFilmLut(scope = screenModelScope, filmLut = filmLut, imageBitmap = it) {
                    screenModelScope.launch {
                        _isLoading.emit(false)
                        _image.emit(it)
                        _editedImage.emit(it)
                    }
                }
            }
            _filmLut.emit(filmLut)
            _showBottomSheet.emit(false)
        }

    }

    fun onImagePickerResult(byteArrays: List<ByteArray>) {
        byteArrays.firstOrNull()?.let {
            screenModelScope.launch {
                val image = it.toImageBitmap()
                _image.emit(image)
                _originalImage.emit(image)
                _filmLut.value?.let { selectFilmLut(it) }
            }
        }
    }

    fun showFilmLutsBottomSheet() {
        screenModelScope.launch {
            _showBottomSheet.emit(true)
        }
    }

    fun dismissFilmLutBottomSheet() {
        screenModelScope.launch { _showBottomSheet.emit(false) }
    }

    fun snackbarMessageShown() {
        _userMessage.value = null
    }

    fun showOriginalImage(show: Boolean) {
        screenModelScope.launch {
            _originalImage.value?.let { originalImage ->
                if (show)
                    _image.emit(originalImage)
                else _editedImage.value.let { editedImage ->
                    _image.emit(editedImage)
                }
            }
        }
    }

    fun resetImage() {
        screenModelScope.launch {
            _originalImage.value?.let {
                _image.emit(it)
            }
        }
    }

    fun exportImage() {
        _editedImage.value?.let {
            screenModelScope.launch { saveImageToGallery(it, appContext = AppContext) }
        }
    }
}



