package screens.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.plusmobileapps.konnectivity.Konnectivity
import io.github.vinceglb.filekit.core.PlatformFile
import io.github.vinceglb.filekit.core.extension
import io.github.yahiaangelo.filmsimulator.FavoriteLut
import io.github.yahiaangelo.filmsimulator.FilmLut
import io.github.yahiaangelo.filmsimulator.data.source.FilmRepository
import io.github.yahiaangelo.filmsimulator.data.source.SettingsRepository
import io.github.yahiaangelo.filmsimulator.data.source.toFavoriteLut
import io.github.yahiaangelo.filmsimulator.image.ImageAdjustments
import io.github.yahiaangelo.filmsimulator.image.SkiaImageProcessor
import io.github.yahiaangelo.filmsimulator.lut.LutDownloadManager
import io.github.yahiaangelo.filmsimulator.screens.settings.DefaultPickerType
import io.github.yahiaangelo.filmsimulator.util.AppContext
import io.github.yahiaangelo.filmsimulator.util.convertImageToJpeg
import io.github.yahiaangelo.filmsimulator.util.fixImageOrientation
import io.github.yahiaangelo.filmsimulator.util.supportedImageExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.dsl.module
import util.EDITED_IMAGE_FILE_NAME
import util.IMAGE_FILE_NAME
import util.ORIGINAL_IMAGE_FILE_PREFIX
import util.THUMBNAILS_DIR
import util.createDirectory
import util.readImageFile
import util.saveImageFile
import util.saveImageToGallery
import kotlin.coroutines.cancellation.CancellationException

val homeScreenModule = module {
    factory { HomeScreenModel(get(), get(), get()) }
}

/** Debounce window before re-rendering the preview after a slider change. */
private const val PREVIEW_DEBOUNCE_MS = 25L

/**
 * Source formats that the Skia pipeline can't decode at full quality on its own —
 * they need to be transcoded to JPEG first via the platform converter. For RAW
 * formats this also ensures we read the embedded full-size preview rather than
 * the tiny thumbnail BitmapFactory returns by default (Pixel DNGs etc.).
 */
private val formatsNeedingConversion = setOf(
    "heic", "heif",
    "dng", "raw", "cr2", "nef", "orf", "arw", "raf", "pef", "sr2", "rw2",
)

/** JPEG quality used for the preview pipeline — lower than export to keep encode fast. */
private const val PREVIEW_QUALITY = 80

/**
 * UiState for the Main Screen
 */
data class HomeUiState(
    val previewImage: ByteArray? = null,
    val previewToken: Long = 0L,
    val selectedFilm: FilmLut? = null,
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    /**
     * 0..1 progress for the loading dialog. When null, the dialog falls back
     * to its indeterminate spinner; when set, it renders a linear progress
     * bar driven by this value. Only the export path populates this — other
     * loading states (refresh, LUT load) leave it null.
     */
    val loadingProgress: Float? = null,
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
    val onShadowsChange: (Float) -> Unit = {},
    val onHighlightsChange: (Float) -> Unit = {},
    val onSaturationChange: (Float) -> Unit = {},
    val onTemperatureChange: (Float) -> Unit = {},
    val onExposureChange: (Float) -> Unit = {},
    val onGrainChange: (Float) -> Unit = {},
    val onChromaticAberrationChange: (Float) -> Unit = {},
    val onLutIntensityChange: (Float) -> Unit = {},
    val showDownloadDialog: Boolean = false,
    val showDownloadProgress: Boolean = false,
    val downloadProgress: Pair<Int, Int> = 0 to 0,
    val onDownloadLutsConfirm: () -> Unit = {},
    val onDownloadLutsDismiss: () -> Unit = {},
    val filmThumbnails: Map<String, String> = emptyMap(),
    )

enum class BottomSheetState {
    COLLAPSED, EXPANDED, HIDDEN
}

/**
 * ViewModel for the Main Screen.
 *
 * Preview pipeline: the screen model holds the decoded source image bytes and the
 * currently selected LUT bytes. Every change to either (or to [ImageAdjustments])
 * triggers a debounced re-render through [SkiaImageProcessor] at a preview-friendly
 * resolution; the resulting JPEG bytes are pushed into [HomeUiState.previewImage]
 * for Coil to display. Export reuses the same processor at full resolution.
 */
data class HomeScreenModel(
    val repository: FilmRepository,
    val settingsRepository: SettingsRepository,
    val lutDownloadManager: LutDownloadManager,
) : ScreenModel {

    private val _uiState: MutableStateFlow<HomeUiState> = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState
    private val konnectivity = Konnectivity()

    private val processor = SkiaImageProcessor()
    private val previewTrigger: MutableStateFlow<PreviewRequest?> = MutableStateFlow(null)
    private var previewVersion = 0L
    private var sourceBytes: ByteArray? = null
    private var currentLutBytes: ByteArray? = null
    private var currentAdjustments: ImageAdjustments = ImageAdjustments()
    private var currentThumbnailJob: Job? = null
    /** Filename in app cache holding the user's unmodified original picked image, with original extension. */
    private var originalImageFileName: String? = null

    private data class PreviewRequest(
        val sourceKey: Int,
        val lutKey: Int,
        val adjustments: ImageAdjustments,
        val showOriginal: Boolean,
    )

    init {
        refresh()
        addSettingsListeners()
        screenModelScope.launch {
            lutDownloadManager.uiState.collect { downloadState ->
                updateUiState {
                    it.copy(
                        showDownloadDialog = downloadState.showDownloadDialog,
                        showDownloadProgress = downloadState.showDownloadProgress,
                        downloadProgress = downloadState.downloadProgress,
                    )
                }
            }
        }
        startPreviewLoop()
    }


    private fun updateUiState(update: (HomeUiState) -> HomeUiState) {
        _uiState.value = update(_uiState.value)
    }


    fun refresh() {
        screenModelScope.launch {
            try {
                updateUiState { it.copy(isLoading = true, loadingMessage = "Refreshing data...") }
                if (konnectivity.isConnected) {
                    repository.refresh()
                }
                val newFilmList = repository.getFilmsStream().first()
                val newFavoriteList = repository.getFavoriteFilmsStream().first()
                updateUiState {
                    it.copy(
                        filmLuts = newFilmList,
                        favoriteLuts = newFavoriteList,
                        defaultPickerType = settingsRepository.getSettings().defaultPicker,
                        userMessage = "Data refreshed successfully.",
                    )
                }
            } catch (e: Exception) {
                updateUiState { it.copy(userMessage = "Error refreshing data: ${e.message}") }
            } finally {
                updateUiState { it.copy(isLoading = false) }
            }
        }
    }


    fun dismissDownloadDialog() {
        lutDownloadManager.dismissDownloadDialog()
    }

    fun confirmDownloadLuts() {
        lutDownloadManager.confirmDownloadLuts(screenModelScope) { success, message ->
            message?.let {
                updateUiState { it.copy(userMessage = message) }
            }
        }
    }

    fun adjustContrast(value: Float) = updateImageAdjustment { it.copy(contrast = value) }
    fun adjustShadows(value: Float) = updateImageAdjustment { it.copy(shadows = value) }
    fun adjustHighlights(value: Float) = updateImageAdjustment { it.copy(highlights = value) }
    fun adjustSaturation(value: Float) = updateImageAdjustment { it.copy(saturation = value) }
    fun adjustTemperature(value: Float) = updateImageAdjustment { it.copy(temperature = value) }
    fun adjustExposure(value: Float) = updateImageAdjustment { it.copy(exposure = value) }
    fun addGrain(value: Float) = updateImageAdjustment { it.copy(grain = value) }
    fun addChromaticAberration(value: Float) = updateImageAdjustment { it.copy(chromaticAberration = value) }
    fun adjustLutIntensity(value: Float) = updateImageAdjustment { it.copy(lutIntensity = value) }

    private fun updateImageAdjustment(update: (ImageAdjustments) -> ImageAdjustments) {
        currentAdjustments = update(currentAdjustments)
        updateUiState { it.copy(imageAdjustments = currentAdjustments) }
        requestPreview()
    }

    fun selectFilmLut(filmLut: FilmLut) {
        screenModelScope.launch {
            try {
                updateUiState { it.copy(isLoading = true, loadingMessage = "Loading Film LUT...") }
                val lutBytes = withContext(Dispatchers.IO) { repository.getLutBytes(filmLut) }
                if (lutBytes == null) {
                    updateUiState { it.copy(userMessage = "Error loading LUT.") }
                    return@launch
                }
                currentLutBytes = lutBytes
                updateUiState { it.copy(selectedFilm = filmLut) }
                requestPreview()
            } catch (e: Exception) {
                updateUiState { it.copy(userMessage = "Error applying LUT: ${e.message}") }
            } finally {
                updateUiState { it.copy(isLoading = false, showBottomSheet = BottomSheetState.COLLAPSED) }
            }
        }
    }


    fun generateThumbnailsForGroup(category: String) {
        currentThumbnailJob?.cancel()

        if (sourceBytes == null) return
        currentThumbnailJob = screenModelScope.launch {
            try {
                createDirectory(THUMBNAILS_DIR)

                val films = repository.getFilms(false).filter { it.category == category }
                val thumbnails = _uiState.value.filmThumbnails.toMutableMap()

                for (film in films) {
                    if (thumbnails.containsKey(film.lut_name) || !isActive) continue

                    val thumbnailPath = repository.generateLutThumbnail(film, IMAGE_FILE_NAME)
                    thumbnails[film.lut_name] = thumbnailPath
                    updateUiState { it.copy(filmThumbnails = thumbnails.toMap()) }
                }

                updateUiState { it.copy(filmThumbnails = thumbnails.toMap()) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                updateUiState { it.copy(isLoading = false) }
            }
        }
    }

    private fun generateFilmThumbnails() {
        if (sourceBytes == null) return
        screenModelScope.launch {
            try {
                createDirectory(THUMBNAILS_DIR)

                val favoriteFilms = repository.getFavoriteFilms()
                val favoriteLutNames = favoriteFilms.map { it.name }
                val filmsToProcess = repository.getFilms(false)
                    .filter { favoriteLutNames.contains(it.name) }

                val thumbnails = mutableMapOf<String, String>()

                for (film in filmsToProcess) {
                    val thumbnailPath = repository.generateLutThumbnail(film, IMAGE_FILE_NAME)
                    thumbnails[film.lut_name] = thumbnailPath
                }

                updateUiState { it.copy(filmThumbnails = thumbnails.toMap()) }
            } catch (e: Exception) {
                updateUiState { it.copy(userMessage = "Error generating thumbnails: ${e.message}") }
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
                val originalBytes = platformFile.readBytes()
                // Stash the raw original (with original extension) so export can read its
                // EXIF / detect format when "original format" export is selected.
                val originalName = "$ORIGINAL_IMAGE_FILE_PREFIX.${platformFile.extension.lowercase()}"
                saveImageFile(originalName, originalBytes)
                originalImageFileName = originalName

                saveImageFile(IMAGE_FILE_NAME, originalBytes)
                if (formatsNeedingConversion.contains(platformFile.extension.lowercase())) {
                    convertImageToJpeg(IMAGE_FILE_NAME)
                }
                fixImageOrientation(image = IMAGE_FILE_NAME)

                val bytes = withContext(Dispatchers.IO) { readImageFile(IMAGE_FILE_NAME) }
                sourceBytes = bytes
                processor.clearCache()

                // Reset everything that was tied to the previous image.
                currentAdjustments = ImageAdjustments()
                updateUiState {
                    it.copy(
                        imageAdjustments = ImageAdjustments(),
                        filmThumbnails = emptyMap(),
                    )
                }

                generateFilmThumbnails()
                requestPreview()
            }
        }
    }

    fun showFilmLutsBottomSheet() {
        updateUiState { it.copy(showBottomSheet = BottomSheetState.EXPANDED) }
    }

    fun dismissFilmLutBottomSheet() {
        updateUiState { it.copy(showBottomSheet = BottomSheetState.HIDDEN) }
    }

    fun snackbarMessageShown() {
        updateUiState { it.copy(userMessage = null) }
    }

    fun showOriginalImage(show: Boolean) {
        updateUiState { it.copy(showAdjustments = !show) }
        requestPreview()
    }

    fun resetImage() {
        currentAdjustments = ImageAdjustments()
        currentLutBytes = null
        updateUiState {
            it.copy(
                selectedFilm = null,
                imageAdjustments = ImageAdjustments(),
            )
        }
        requestPreview()
    }

    fun exportImage() {
        val bytes = sourceBytes ?: run {
            updateUiState { it.copy(userMessage = "Please choose an image first.") }
            return
        }
        screenModelScope.launch {
            try {
                updateUiState {
                    it.copy(
                        isLoading = true,
                        loadingMessage = "Processing image with effects...",
                        loadingProgress = 0f,
                    )
                }

                val exportedBytes = processor.process(
                    imageBytes = bytes,
                    lutBytes = currentLutBytes,
                    adjustments = currentAdjustments,
                    maxDimension = null,
                    quality = settingsRepository.getSettings().exportQuality,
                    grainSeed = (kotlin.random.Random.nextFloat() * 1000f),
                    highQualityGrain = true,
                    onProgress = { p ->
                        updateUiState { it.copy(loadingProgress = p) }
                    },
                ) ?: throw IllegalStateException("Failed to process image")

                withContext(Dispatchers.IO) {
                    saveImageFile(EDITED_IMAGE_FILE_NAME, exportedBytes)
                }

                // Switch back to indeterminate spinner for the gallery save —
                // PhotoKit/MediaStore don't expose progress, so a fake bar
                // there would be misleading.
                updateUiState {
                    it.copy(loadingMessage = "Saving to gallery...", loadingProgress = null)
                }
                saveImageToGallery(
                    image = EDITED_IMAGE_FILE_NAME,
                    appContext = AppContext,
                    format = settingsRepository.getSettings().exportFormat,
                    originalImage = originalImageFileName,
                )

                updateUiState { it.copy(userMessage = "Image exported successfully with all effects applied.") }
            } catch (e: Exception) {
                updateUiState { it.copy(userMessage = "Error exporting image: ${e.message}") }
            } finally {
                updateUiState { it.copy(isLoading = false, loadingProgress = null) }
            }
        }
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

    private fun requestPreview() {
        val source = sourceBytes ?: return
        previewTrigger.value = PreviewRequest(
            sourceKey = source.contentHashCode(),
            lutKey = currentLutBytes?.contentHashCode() ?: 0,
            adjustments = currentAdjustments,
            showOriginal = !_uiState.value.showAdjustments,
        )
    }

    @OptIn(FlowPreview::class)
    private fun startPreviewLoop() {
        screenModelScope.launch {
            previewTrigger
                .filterNotNull()
                .debounce(PREVIEW_DEBOUNCE_MS)
                .collectLatest { request ->
                    val source = sourceBytes ?: return@collectLatest
                    val bytes = withContext(Dispatchers.Default) {
                        if (request.showOriginal) {
                            processor.process(
                                imageBytes = source,
                                lutBytes = null,
                                adjustments = ImageAdjustments(),
                                maxDimension = SkiaImageProcessor.PREVIEW_MAX_DIMENSION,
                                quality = PREVIEW_QUALITY,
                            )
                        } else {
                            processor.process(
                                imageBytes = source,
                                lutBytes = currentLutBytes,
                                adjustments = currentAdjustments,
                                maxDimension = SkiaImageProcessor.PREVIEW_MAX_DIMENSION,
                                quality = PREVIEW_QUALITY,
                            )
                        }
                    } ?: return@collectLatest
                    val token = ++previewVersion
                    updateUiState { it.copy(previewImage = bytes, previewToken = token) }
                }
        }
    }
}