package screens.home


import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.rememberCoilZoomState

import film_simulator.shared.generated.resources.Res
import film_simulator.shared.generated.resources.film
import film_simulator.shared.generated.resources.ic_film_bw
import film_simulator.shared.generated.resources.ic_film_colorslide
import film_simulator.shared.generated.resources.ic_film_fujixtransiii
import film_simulator.shared.generated.resources.ic_film_negative_new
import film_simulator.shared.generated.resources.ic_film_negative_old
import film_simulator.shared.generated.resources.ic_film_instant_pro
import film_simulator.shared.generated.resources.ic_film_negative_color
import film_simulator.shared.generated.resources.ic_film_instant_consumer
import film_simulator.shared.generated.resources.ic_film_print
import film_simulator.shared.generated.resources.ic_image_add_24
import film_simulator.shared.generated.resources.image_24

import film_simulator.shared.generated.resources.select_image
import film_simulator.shared.generated.resources.select_your_film
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import io.github.yahiaangelo.filmsimulator.FavoriteLut
import io.github.yahiaangelo.filmsimulator.FilmLut
import io.github.yahiaangelo.filmsimulator.PlatformName
import io.github.yahiaangelo.filmsimulator.data.source.network.GITHUB_BASE_URL
import io.github.yahiaangelo.filmsimulator.getAndroidSdkVersion
import io.github.yahiaangelo.filmsimulator.getPlatform
import io.github.yahiaangelo.filmsimulator.image.ImageWithAdjustments
import io.github.yahiaangelo.filmsimulator.screens.settings.DefaultPickerType
import io.github.yahiaangelo.filmsimulator.screens.settings.SettingsScreen
import io.github.yahiaangelo.filmsimulator.util.getScreenHeight
import io.github.yahiaangelo.filmsimulator.util.getScreenWidth
import io.github.yahiaangelo.filmsimulator.util.supportedImageExtensions
import io.github.yahiaangelo.filmsimulator.view.AppScaffold
import io.github.yahiaangelo.filmsimulator.view.CenteredSettingsSlider
import io.github.yahiaangelo.filmsimulator.view.LutDownloadDialog
import io.github.yahiaangelo.filmsimulator.view.LutDownloadProgressDialog
import io.github.yahiaangelo.filmsimulator.view.ProgressDialog
import io.github.yahiaangelo.filmsimulator.view.SettingsSlider
import kotlinx.coroutines.delay
import okio.FileSystem
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import util.THUMBNAILS_DIR
import util.systemTemporaryPath


data class HomeScreen(
    val userMessage: String = ""
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val scaffoldState = rememberScaffoldState()
        val sheetState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.PartiallyExpanded
            )
        )
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        val vm = getScreenModel<HomeScreenModel>()
        val uiState by vm.uiState.collectAsState()

        val singleImagePicker = rememberFilePickerLauncher(
            type = when (uiState.defaultPickerType) {
                DefaultPickerType.IMAGES -> PickerType.Image
                DefaultPickerType.FILES -> PickerType.File(supportedImageExtensions.toList())
            }, mode = PickerMode.Single, onResult = vm::onImagePickerResult
        )


        val homeScreenState = HomeUiState(
            image = uiState.image,
            selectedFilm = uiState.selectedFilm,
            isLoading = uiState.isLoading,
            loadingMessage = uiState.loadingMessage,
            showBottomSheet = uiState.showBottomSheet,
            filmLuts = uiState.filmLuts,
            favoriteLuts = uiState.favoriteLuts,
            userMessage = uiState.userMessage,
            imageAdjustments = uiState.imageAdjustments,
            showAdjustments = uiState.showAdjustments,
            onRefresh = vm::refresh,
            onImageChooseClick = singleImagePicker::launch,
            onFilmBoxClick = vm::showFilmLutsBottomSheet,
            onDismissRequest = vm::dismissFilmLutBottomSheet,
            onItemClick = vm::selectFilmLut,
            onVisibilityClick = vm::showOriginalImage,
            onImageResetClick = vm::resetImage,
            onSettingsClick = { navigator.push(SettingsScreen()) },
            onImageExportClick = vm::exportImage,
            snackbarMessageShown = vm::snackbarMessageShown,
            onAddFavoriteClick = vm::addFavoriteFilm,
            onRemoveFavoriteClick = vm::removeFavoriteFilm,
            // Image adjustment handlers
            onContrastChange = vm::adjustContrast,
            onBrightnessChange = vm::adjustBrightness,
            onSaturationChange = vm::adjustSaturation,
            onTemperatureChange = vm::adjustTemperature,
            onExposureChange = vm::adjustExposure,
            onGrainChange = vm::addGrain,
            onChromaticAberrationChange = vm::addChromaticAberration,
            showDownloadDialog = uiState.showDownloadDialog,
            showDownloadProgress = uiState.showDownloadProgress,
            downloadProgress = uiState.downloadProgress,
            onDownloadLutsConfirm = vm::confirmDownloadLuts,
            onDownloadLutsDismiss = vm::dismissDownloadDialog,
            filmThumbnails = uiState.filmThumbnails
        )

        AppScaffold(
            onVisibilityClick = homeScreenState.onVisibilityClick,
            onImageChooseClick = homeScreenState.onImageChooseClick,
            onImageResetClick = homeScreenState.onImageResetClick,
            onSettingsClick = homeScreenState.onSettingsClick,
            onImageExportClick = homeScreenState.onImageExportClick,
            snackbarHostState = snackbarHostState
        ) { innerPadding ->
            HomeContent(
                state = homeScreenState,
                modifier = Modifier.padding(innerPadding)
            )
        }

        LutDownloadDialog(
            isVisible = homeScreenState.showDownloadDialog,
            onDismiss = homeScreenState.onDownloadLutsDismiss,
            onConfirm = homeScreenState.onDownloadLutsConfirm
        )

        LutDownloadProgressDialog(
            isVisible = homeScreenState.showDownloadProgress,
            current = homeScreenState.downloadProgress.first,
            total = homeScreenState.downloadProgress.second,
            onDismiss = homeScreenState.onDownloadLutsDismiss
        )


        FilmLutsListBottomSheet(
            state = homeScreenState,
            viewModel = vm,
            sheetState = sheetState,
        )

        homeScreenState.userMessage?.let { message ->
            LaunchedEffect(scaffoldState, vm, message) {
                snackbarHostState.showSnackbar(message)
                homeScreenState.snackbarMessageShown()
            }
        }

        if (homeScreenState.isLoading) {
            ProgressDialog(loadingMessage = homeScreenState.loadingMessage)
        }
    }

    @Composable
    private fun HomeContent(
        state: HomeUiState,
        modifier: Modifier = Modifier
    ) {
        val zoomState = rememberCoilZoomState()
        Column(modifier = modifier.padding(horizontal = 18.dp)) {
            Spacer(modifier = Modifier.size(23.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(360.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                    onClick = state.onImageChooseClick
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        state.image?.let {
                            // Wrap the image with adjustments
                            ImageWithAdjustments(
                                adjustments = state.imageAdjustments,
                                modifier = Modifier
                                    .fillMaxSize(),
                                applyModifiers = state.showAdjustments
                            ) {
                                CoilZoomAsyncImage(
                                    modifier = Modifier.fillMaxSize(),
                                    zoomState = zoomState,
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(
                                            "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/${
                                                it.substringBefore(
                                                    "?"
                                                )
                                            }"
                                        )
                                        .memoryCacheKey(it)
                                        .diskCacheKey(it)
                                        .diskCachePolicy(CachePolicy.DISABLED)
                                        .build(),
                                    contentDescription = null,
                                    scrollBar = null
                                )
                            }

                        } ?: IconButton(
                            modifier = Modifier.align(Alignment.Center).size(150.dp),
                            onClick = state.onImageChooseClick
                        ) {
                            Column {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_image_add_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(65.dp, 65.dp)
                                        .align(Alignment.CenterHorizontally)
                                )
                                Text(
                                    text = stringResource(Res.string.select_image),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(weight = 1f, fill = false)
            ) {
                Spacer(modifier = Modifier.size(23.dp))
                Divider(modifier = Modifier.padding(28.dp, 0.dp))
                Spacer(modifier = Modifier.size(23.dp))
                FilmLutBox(
                    modifier = Modifier.fillMaxWidth(),
                    selectedFilm = state.selectedFilm,
                    onFilmBoxClick = state.onFilmBoxClick,
                )
                Spacer(modifier = Modifier.size(16.dp))

                // Image adjustment sliders
                AdjustmentsSection(
                    state = state,
                    modifier = Modifier.fillMaxWidth()
                )
            }

        }
    }

    @Composable
    private fun AdjustmentsSection(
        state: HomeUiState,
        modifier: Modifier = Modifier
    ) {
        Box(modifier = modifier) {
            // Image adjustment sliders
            Column {
                CenteredSettingsSlider(
                    name = "Exposure",
                    value = state.imageAdjustments.exposure,
                    onValueChange = state.onExposureChange,
                    range = -20f..20f,
                    steps = 1
                )
                CenteredSettingsSlider(
                    name = "Temperature",
                    value = state.imageAdjustments.temperature,
                    onValueChange = state.onTemperatureChange,
                    range = -20f..20f,
                    steps = 1
                )
                CenteredSettingsSlider(
                    name = "Contrast",
                    value = state.imageAdjustments.contrast,
                    onValueChange = state.onContrastChange,
                    range = -20f..20f,
                    steps = 1
                )
                CenteredSettingsSlider(
                    name = "Brightness",
                    value = state.imageAdjustments.brightness,
                    onValueChange = state.onBrightnessChange,
                    range = -20f..20f,
                    steps = 1
                )
                CenteredSettingsSlider(
                    name = "Saturation",
                    value = state.imageAdjustments.saturation,
                    onValueChange = state.onSaturationChange,
                    range = -20f..20f,
                    steps = 1
                )
                SettingsSlider(
                    name = "Grain",
                    value = state.imageAdjustments.grain,
                    onValueChange = state.onGrainChange,
                    range = 0f..10f,
                    steps = 20
                )
                SettingsSlider(
                    name = "Chromatic Aberration",
                    value = state.imageAdjustments.chromaticAberration,
                    onValueChange = state.onChromaticAberrationChange,
                    range = 0f..10f,
                    steps = 10
                )
            }

            // Overlay for Android < 13 (TIRAMISU)
            if (getPlatform().name == PlatformName.ANDROID && getAndroidSdkVersion() < 33) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.8f)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Image adjustments require Android 13 and above",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FilmLutsListBottomSheet(
        state: HomeUiState,
        viewModel: HomeScreenModel,
        sheetState: BottomSheetScaffoldState,
    ) {
        val listState: LazyListState = rememberLazyListState()
        val focusManager = LocalFocusManager.current

        if (state.showBottomSheet == BottomSheetState.HIDDEN) return

        // Clear focus when bottom sheet state changes to avoid keyboard issues
        LaunchedEffect(sheetState.bottomSheetState.currentValue) {
            focusManager.clearFocus()
        }

        BottomSheetScaffold(
            scaffoldState = sheetState,
            sheetPeekHeight = getScreenHeight() * 0.45f,
            sheetSwipeEnabled = true,
            sheetDragHandle = {},
            sheetContent = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding() // Handle keyboard properly
                ) {
                    FilmLutsList(
                        state = state,
                        viewModel = viewModel,
                        listState = listState,
                        filmLuts = state.filmLuts,
                        favoriteLuts = state.favoriteLuts,
                        selectedFilm = state.selectedFilm,
                        onItemClick = {
                            state.onItemClick(it)
                            focusManager.clearFocus() // Clear focus when selecting a film
                        },
                        onAddFavoriteClick = state.onAddFavoriteClick,
                        onRemoveFavoriteClick = state.onRemoveFavoriteClick,
                        onDismissRequest = {
                            focusManager.clearFocus()
                            state.onDismissRequest()
                        }
                    )
                }
            }
        ) {
            // Content under the bottom sheet (empty in this case)
        }
    }


    @Composable
    private fun FilmLutBox(
        modifier: Modifier = Modifier,
        selectedFilm: FilmLut?,
        onFilmBoxClick: () -> Unit
    ) {
        Box(modifier = modifier) {
            OutlinedCard(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(80.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = CardDefaults.outlinedCardBorder(),
                onClick = onFilmBoxClick
            ) {

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(Res.string.film),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 2.dp)
                    )

                    Text(
                        text = selectedFilm?.name ?: stringResource(Res.string.select_your_film),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 2.dp)
                    )
                }
            }

        }
    }


    @OptIn(
        ExperimentalFoundationApi::class,
        ExperimentalMaterial3Api::class,
        ExperimentalMaterialApi::class
    )
    @Composable
    private fun FilmLutsList(
        state: HomeUiState,
        viewModel: HomeScreenModel,
        listState: LazyListState,
        filmLuts: List<FilmLut>,
        favoriteLuts: List<FavoriteLut>,
        selectedFilm: FilmLut?,
        onItemClick: (film: FilmLut) -> Unit,
        onAddFavoriteClick: (FilmLut) -> Unit,
        onRemoveFavoriteClick: (FilmLut) -> Unit,
        onDismissRequest: () -> Unit
    ) {
        var searchQuery by remember { mutableStateOf("") }
        var isSearchActive by remember { mutableStateOf(false) }
        var selectedGroup by remember { mutableStateOf<String?>(null) }
        var tabsState by remember { mutableStateOf(0) }
        val titles = listOf("All", "Favorites")

        // Filter films based on search query and favorites tab
        val filteredFilmLuts = filmLuts.filter {
            (searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)) &&
                    (tabsState != 1 || favoriteLuts.any { favorite -> favorite.name == it.name })
        }

        val favoriteFilms = filmLuts.filter { film ->
            favoriteLuts.any { favorite -> favorite.name == film.name }
        }
        val sortedAndGrouped = filteredFilmLuts.groupBy { it.category }
        val groupsList = sortedAndGrouped.keys.toList()

        // Handle keyboard appearance
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current

        Column {
            // Top bar with tabs and search
            if (selectedGroup == null) {
                if (isSearchActive) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = {
                            isSearchActive = false
                            searchQuery = ""
                            focusManager.clearFocus()
                        },
                        focusRequester = focusRequester
                    )

                    LaunchedEffect(isSearchActive) {
                        if (isSearchActive) {
                            delay(100) // Short delay to ensure the UI is ready
                            focusRequester.requestFocus()
                        }
                    }
                } else {
                    CategoryTabsHeader(
                        tabsState = tabsState,
                        titles = titles,
                        onTabSelected = { tabsState = it },
                        onSearchClick = { isSearchActive = true },
                        onDismissRequest = onDismissRequest
                    )
                }
            } else {
                // Group header with back button
                GroupHeader(
                    groupName = selectedGroup!!,
                    onBackClick = { selectedGroup = null }
                )
            }

            AnimatedContent(
                targetState = Triple(selectedGroup, tabsState, isSearchActive),
                label = "Content Animation"
            ) { (targetGroup, currentTab, searching) ->
                when {
                    // Show search results in grid when search is active
                    searching && searchQuery.isNotEmpty() -> {
                        FilmsGrid(
                            films = filteredFilmLuts,
                            selectedFilm = selectedFilm,
                            onItemClick = onItemClick,
                            favoriteLuts = favoriteLuts,
                            onAddFavoriteClick = onAddFavoriteClick,
                            onRemoveFavoriteClick = onRemoveFavoriteClick,
                            thumbnails = state.filmThumbnails
                        )
                    }
                    // Show specific group films in grid
                    targetGroup != null -> {
                        // Generate thumbnails for the selected group when it's selected
                        LaunchedEffect(targetGroup) {
                            viewModel.generateThumbnailsForGroup(targetGroup)
                        }

                        FilmsGrid(
                            films = sortedAndGrouped[targetGroup] ?: emptyList(),
                            selectedFilm = selectedFilm,
                            onItemClick = onItemClick,
                            favoriteLuts = favoriteLuts,
                            onAddFavoriteClick = onAddFavoriteClick,
                            onRemoveFavoriteClick = onRemoveFavoriteClick,
                            thumbnails = state.filmThumbnails
                        )
                    }
                    // Show favorites tab
                    currentTab == 1 -> {
                        FilmsGrid(
                            films = favoriteFilms,
                            selectedFilm = selectedFilm,
                            onItemClick = onItemClick,
                            favoriteLuts = favoriteLuts,
                            onAddFavoriteClick = onAddFavoriteClick,
                            onRemoveFavoriteClick = onRemoveFavoriteClick,
                            thumbnails = state.filmThumbnails
                        )
                    }
                    // Show empty search results message
                    searching && searchQuery.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(getScreenHeight() * 0.45f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Type to search for films",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Show categories list
                    else -> {
                        CategoriesList(
                            groupsList = groupsList,
                            sortedAndGrouped = sortedAndGrouped,
                            onGroupClick = { selectedGroup = it },
                            listState = listState
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchBar(
        query: String,
        onQueryChange: (String) -> Unit,
        onClose: () -> Unit,
        focusRequester: FocusRequester
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close search"
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        "Search films...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                singleLine = true,
                colors = androidx.compose.material.TextFieldDefaults.outlinedTextFieldColors(
                    textColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        // Close keyboard but keep search active
                        focusRequester.freeFocus()
                        //LocalFocusManager.current.clearFocus()
                    }
                )
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CategoryTabsHeader(
        tabsState: Int,
        titles: List<String>,
        onTabSelected: (Int) -> Unit,
        onSearchClick: () -> Unit,
        onDismissRequest: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(color = MaterialTheme.colorScheme.background),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismissRequest) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }

            PrimaryTabRow(
                selectedTabIndex = tabsState,
                modifier = Modifier.weight(1f)
            ) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = tabsState == index,
                        onClick = { onTabSelected(index) },
                        text = {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            }
        }
    }

    @Composable
    private fun GroupHeader(
        groupName: String,
        onBackClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Back",
                    modifier = Modifier.rotate(180f)
                )
            }
            Text(
                text = groupName,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    @Composable
    private fun FilmsGrid(
        films: List<FilmLut>,
        selectedFilm: FilmLut?,
        onItemClick: (film: FilmLut) -> Unit,
        favoriteLuts: List<FavoriteLut>,
        onAddFavoriteClick: (FilmLut) -> Unit,
        onRemoveFavoriteClick: (FilmLut) -> Unit,
        thumbnails: Map<String, String>
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(getScreenHeight() * 0.45f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(count = films.size) { index ->
                val film = films[index]
                FilmItem(
                    film = film,
                    selectedFilm = selectedFilm,
                    onItemClick = onItemClick,
                    isFavorite = favoriteLuts.any { it.name == film.name },
                    onFavoriteClick = {
                        if (favoriteLuts.any { it.name == film.name }) {
                            onRemoveFavoriteClick(film)
                        } else {
                            onAddFavoriteClick(film)
                        }
                    },
                    thumbnails = thumbnails,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    private fun CategoriesList(
        groupsList: List<String>,
        sortedAndGrouped: Map<String, List<FilmLut>>,
        onGroupClick: (String) -> Unit,
        listState: LazyListState
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .wrapContentHeight()
                .height(getScreenHeight() * 0.45f)
                .padding(bottom = 40.dp)
        ) {
            items(groupsList) { group ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    onClick = { onGroupClick(group) }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = when (group) {
                                "Print" -> painterResource(Res.drawable.ic_film_print)
                                "Negative Old" -> painterResource(Res.drawable.ic_film_negative_old)
                                "Negative New" -> painterResource(Res.drawable.ic_film_negative_new)
                                "Bw" -> painterResource(Res.drawable.ic_film_bw)
                                "Colorslide" -> painterResource(Res.drawable.ic_film_colorslide)
                                "Fujixtransiii" -> painterResource(Res.drawable.ic_film_fujixtransiii)
                                "Instant Pro" -> painterResource(Res.drawable.ic_film_instant_pro)
                                "Negative Color" -> painterResource(Res.drawable.ic_film_negative_color)
                                "Instant Consumer" -> painterResource(Res.drawable.ic_film_instant_consumer)
                                else -> painterResource(Res.drawable.ic_film_print)
                            },
                            contentDescription = null,
                            modifier = Modifier
                                .height(80.dp)
                                .width(80.dp),
                        )
                        Text(
                            text = group,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = sortedAndGrouped[group]?.size.toString(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "enter",
                        )
                    }
                }
            }
        }
    }


    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun FilmItem(
        film: FilmLut,
        selectedFilm: FilmLut?,
        onItemClick: (film: FilmLut) -> Unit,
        isFavorite: Boolean,
        onFavoriteClick: () -> Unit,
        thumbnails: Map<String, String> = emptyMap(),
        modifier: Modifier = Modifier
    ) {
        val isSelected = film == selectedFilm
        val cardBorderColor = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceVariant

        OutlinedCard(
            modifier = modifier
                .height(getScreenHeight() * 0.25f)
                .padding(4.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = cardBorderColor
                    )
                ),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            onClick = { onItemClick(film) }
        ) {
            Column(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))

            ) {
                // Thumbnail container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(4f)
                ) {
                    // Use the thumbnail if available, otherwise fall back to the GitHub image
                    val thumbnailPath = thumbnails[film.lut_name]
                    if (thumbnailPath.isNullOrEmpty().not()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                .data("$systemTemporaryPath/$THUMBNAILS_DIR/$thumbnailPath")
                                .crossfade(true)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build(),
                            placeholder = painterResource(Res.drawable.image_24),
                            fallback = painterResource(Res.drawable.image_24),
                            error = painterResource(Res.drawable.image_24),
                            contentDescription = film.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))

                        )
                    } else {
                        // Fallback to GitHub image
                        AsyncImage(
                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                .data("${GITHUB_BASE_URL}${film.image_url}")
                                .crossfade(true)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = film.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Favorite button overlaid on the top right of the image
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(18.dp)
                            .size(22.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Film name
                Box(
                    modifier = Modifier.weight(1F).clip(RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = film.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

}

