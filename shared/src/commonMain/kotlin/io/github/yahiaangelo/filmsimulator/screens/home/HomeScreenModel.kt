package screens.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vinceglb.filekit.core.PlatformFile
import io.github.vinceglb.filekit.core.extension
import io.github.yahiaangelo.filmsimulator.FavoriteLut
import io.github.yahiaangelo.filmsimulator.FilmLut
import io.github.yahiaangelo.filmsimulator.data.source.FilmRepository
import io.github.yahiaangelo.filmsimulator.data.source.SettingsRepository
import io.github.yahiaangelo.filmsimulator.data.source.toFavoriteLut
import io.github.yahiaangelo.filmsimulator.image.ImageAdjustments
import io.github.yahiaangelo.filmsimulator.image.export.ShaderExporter
import io.github.yahiaangelo.filmsimulator.screens.settings.DefaultPickerType
import io.github.yahiaangelo.filmsimulator.util.AppContext
import io.github.yahiaangelo.filmsimulator.util.convertImageToJpeg
import io.github.yahiaangelo.filmsimulator.util.fixImageOrientation
import io.github.yahiaangelo.filmsimulator.util.supportedImageExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.dsl.module
import util.EDITED_IMAGE_FILE_NAME
import util.IMAGE_FILE_NAME
import util.readImageFile
import util.saveImageFile
import util.saveImageToGallery

val homeScreenModule = module {
    factory { HomeScreenModel(get(), get()) }
}

/**
 * UiState for the Main Screen
 */
data class HomeUiState(
    val image: String? = null,
    val selectedFilm: FilmLut? = null,
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val showBottomSheet: BottomSheetState = BottomSheetState.HIDDEN,
    val defaultPickerType: DefaultPickerType = DefaultPickerType.IMAGES,
    val filmLuts: List<FilmLut> = emptyList(),
    val favoriteLuts: List<FavoriteLut> = emptyList(),
    val userMessage: String? = null,
    val showAdjustments: Boolean = true,
    val imageAdjustments: ImageAdjustments = ImageAdjustments(),
    val onRefresh: () -> Unit = {},
    val onImageChooseClick: () -> Unit = {},
    val onFilmBoxClick: () -> Unit = {},
    val onDismissRequest: () -> Unit = {},
    val onItemClick: (film: FilmLut) -> Unit = {},
    val onVisibilityClick: (Boolean) -> Unit = {},
    val onImageResetClick: () -> Unit = {},
    val onSettingsClick: () -> Unit = {},
    val onImageExportClick: () -> Unit = {},
    val snackbarMessageShown: () -> Unit = {},
    val onAddFavoriteClick: (FilmLut) -> Unit = {},
    val onRemoveFavoriteClick: (FilmLut) -> Unit = {},
    // Individual adjustment handlers
    val onContrastChange: (Float) -> Unit = {},
    val onBrightnessChange: (Float) -> Unit = {},
    val onSaturationChange: (Float) -> Unit = {},
    val onTemperatureChange: (Float) -> Unit = {},
    val onExposureChange: (Float) -> Unit = {},
    val onGrainChange: (Float) -> Unit = {},
    val onChromaticAberrationChange: (Float) -> Unit = {},
)

enum class BottomSheetState {
    COLLAPSED, EXPANDED, HIDDEN
}

/**
 * ViewModel for the Main Screen
 */
data class HomeScreenModel(val repository: FilmRepository, val settingsRepository: SettingsRepository) : ScreenModel {

    private val _uiState: MutableStateFlow<HomeUiState> = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        refresh()
        addSettingsListeners()
    }

    private fun updateUiState(update: (HomeUiState) -> HomeUiState) {
        _uiState.value = update(_uiState.value)
    }

    private val _originalImage: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _editedImage: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _currentAdjustments: MutableStateFlow<ImageAdjustments> = MutableStateFlow(ImageAdjustments())
    private val shaderExporter = ShaderExporter()



    fun refresh() {
        screenModelScope.launch {
            try {
                updateUiState { it.copy(isLoading = true, loadingMessage = "Refreshing data...") }
                repository.refresh()
                val newFilmList = repository.getFilmsStream().first()
                val newFavoriteList = repository.getFavoriteFilmsStream().first()
                updateUiState { it.copy(filmLuts = newFilmList, favoriteLuts = newFavoriteList, defaultPickerType = settingsRepository.getSettings().defaultPicker, userMessage = "Data refreshed successfully.") }
            } catch (e: Exception) {
                updateUiState { it.copy(userMessage = "Error refreshing data: ${e.message}") }
            } finally {
                updateUiState { it.copy(isLoading = false) }
            }
        }
    }

    // Image adjustment methods
    fun adjustContrast(value: Float) {
        updateImageAdjustment { it.copy(contrast = value) }
    }

    fun adjustBrightness(value: Float) {
        updateImageAdjustment { it.copy(brightness = value) }
    }

    fun adjustSaturation(value: Float) {
        updateImageAdjustment { it.copy(saturation = value) }
    }

    fun adjustTemperature(value: Float) {
        updateImageAdjustment { it.copy(temperature = value) }
    }

    fun adjustExposure(value: Float) {
        updateImageAdjustment { it.copy(exposure = value) }
    }

    fun addGrain(value: Float) {
        updateImageAdjustment { it.copy(grain = value) }
    }

    fun addChromaticAberration(value: Float) {
        updateImageAdjustment { it.copy(chromaticAberration = value) }
    }

    private fun updateImageAdjustment(update: (ImageAdjustments) -> ImageAdjustments) {
        _currentAdjustments.value = update(_currentAdjustments.value)
        updateUiState { it.copy(imageAdjustments = _currentAdjustments.value) }
    }

    fun selectFilmLut(filmLut: FilmLut) {
        _originalImage.value?.let { image ->
            screenModelScope.launch {
                try {
                    updateUiState {
                        it.copy(
                            isLoading = true,
                            loadingMessage = "Applying Film LUT..."
                        )
                    }
                    withContext(Dispatchers.IO) {
                        repository.applyFilmLut(
                            scope = screenModelScope,
                            filmLut = filmLut,
                            image = image,
                            onComplete = { resultImage ->
                                screenModelScope.launch { _editedImage.emit(resultImage) }
                                updateUiState { it.copy(selectedFilm = filmLut) }
                                emitImage(resultImage)
                            },
                            onError = { error ->
                                updateUiState { it.copy(userMessage = "Error applying LUT: $error") }
                            })
                    }
                } catch (e: Exception) {
                    updateUiState { it.copy(userMessage = "Error applying LUT: ${e.message}") }
                } finally {
                    updateUiState { it.copy(isLoading = false, showBottomSheet = BottomSheetState.COLLAPSED) }
                }
            }
        }
    }

    fun onImagePickerResult(file: PlatformFile?) {
        file?.let { platformFile ->
            if (!supportedImageExtensions.contains(platformFile.extension.lowercase())) {
                updateUiState { it.copy(userMessage = "Unsupported image type.") }
                return
            }
            screenModelScope.launch {
                saveImageFile(IMAGE_FILE_NAME, platformFile.readBytes())
                saveImageFile(EDITED_IMAGE_FILE_NAME, platformFile.readBytes())
                if (arrayOf("heic", "heif").contains(platformFile.extension.lowercase())) {
                    convertImageToJpeg(IMAGE_FILE_NAME)
                }
                fixImageOrientation(image = IMAGE_FILE_NAME)
                _originalImage.emit(IMAGE_FILE_NAME)
                _editedImage.emit(IMAGE_FILE_NAME)

                // Reset adjustments when new image is loaded
                _currentAdjustments.emit(ImageAdjustments())
                updateUiState { it.copy(imageAdjustments = ImageAdjustments()) }

                emitImage(IMAGE_FILE_NAME)
                _uiState.value.selectedFilm?.let { selectFilmLut(it) }
            }
        }
    }

    fun showFilmLutsBottomSheet() {
        updateUiState { it.copy(showBottomSheet = BottomSheetState.EXPANDED) }
    }

    fun dismissFilmLutBottomSheet() {
        updateUiState { it.copy(showBottomSheet = BottomSheetState.HIDDEN) }
    }

    private fun emitImage(image: String) {
        updateUiState { it.copy(image = "$image?${Clock.System.now().epochSeconds}") }
    }

    fun snackbarMessageShown() {
        updateUiState { it.copy(userMessage = null) }
    }

    fun showOriginalImage(show: Boolean) {
        updateUiState { it.copy(showAdjustments = !show)}
        screenModelScope.launch {
            val targetImage = if (show) _originalImage.value else _editedImage.value
            targetImage?.let { emitImage(targetImage) }
        }
    }

    fun resetImage() {
        _originalImage.value?.let { originalImage ->
            updateUiState { it.copy(selectedFilm = null) }
            // Reset all adjustments
            _currentAdjustments.value = ImageAdjustments()
            updateUiState { it.copy(imageAdjustments = ImageAdjustments()) }
            emitImage(originalImage)
        }
    }

    fun exportImage() {
        _editedImage.value?.let { imagePath ->
            screenModelScope.launch {
                try {
                    updateUiState {
                        it.copy(
                            isLoading = true,
                            loadingMessage = "Processing image with effects..."
                        )
                    }

                    updateUiState { it.copy(loadingMessage = "Exporting...") }

                    // Load the source bitmap
                    val bytes = withContext(Dispatchers.IO) {
                        readImageFile(EDITED_IMAGE_FILE_NAME)
                    }

                    // Convert bytes to ImageBitmap
                    val sourceBitmap = bytes.decodeToImageBitmap()
                    // Apply all adjustments and save directly
                    val success = shaderExporter.applyAdjustmentsAndSave(
                        sourceBitmap = sourceBitmap,
                        outputPath = EDITED_IMAGE_FILE_NAME,
                        adjustments = _currentAdjustments.value,
                        quality = settingsRepository.getSettings().exportQuality
                    )

                    if (!success) {
                        throw Exception("Failed to save processed image")
                    }

                    updateUiState { it.copy(loadingMessage = "Saving to gallery...") }

                    // Now save to gallery
                    saveImageToGallery(EDITED_IMAGE_FILE_NAME, appContext = AppContext)

                    updateUiState { it.copy(userMessage = "Image exported successfully with all effects applied.") }
                } catch (e: Exception) {
                    updateUiState { it.copy(userMessage = "Error exporting image: ${e.message}") }
                } finally {
                    updateUiState { it.copy(isLoading = false) }
                }
            }
        } ?: updateUiState { it.copy(userMessage = "Please choose an image first.") }
    }

    fun addFavoriteFilm(filmLut: FilmLut) {
        screenModelScope.launch {
            try {
                val newFavoriteLutList = repository.addFavoriteFilm(filmLut.toFavoriteLut())
                updateUiState { it.copy(favoriteLuts = newFavoriteLutList, userMessage = "Added to favorites.") }
            } catch (e: Exception) {
                updateUiState { it.copy(userMessage = "Error adding to favorites: ${e.message}") }
            }
        }
    }

    fun removeFavoriteFilm(filmLut: FilmLut) {
        screenModelScope.launch {
            try {
                val newFavoriteLutList = repository.removeFavoriteFilm(filmLut.name)
                updateUiState { it.copy(favoriteLuts = newFavoriteLutList, userMessage = "Removed from favorites.") }
            } catch (e: Exception) {
                updateUiState { it.copy(userMessage = "Error removing from favorites: ${e.message}") }
            }
        }
    }

    private fun addSettingsListeners() {
        settingsRepository.getSettings().defaultPickerListener { defaultPicker ->
            updateUiState { it.copy(defaultPickerType = defaultPicker) }
        }
    }
}