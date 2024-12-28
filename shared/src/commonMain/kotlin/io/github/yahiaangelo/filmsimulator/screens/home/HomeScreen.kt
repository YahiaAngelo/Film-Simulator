package screens.home


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.rememberCoilZoomState
import com.seiko.imageloader.rememberImagePainter

import film_simulator.shared.generated.resources.Res
import film_simulator.shared.generated.resources.film
import film_simulator.shared.generated.resources.ic_image_add_24
import film_simulator.shared.generated.resources.search

import film_simulator.shared.generated.resources.select_image
import film_simulator.shared.generated.resources.select_your_film
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import io.github.yahiaangelo.filmsimulator.FilmLut
import io.github.yahiaangelo.filmsimulator.data.source.network.GITHUB_BASE_URL
import io.github.yahiaangelo.filmsimulator.screens.settings.SettingsScreen
import io.github.yahiaangelo.filmsimulator.view.AppScaffold
import io.github.yahiaangelo.filmsimulator.view.ProgressDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.FileSystem
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource


data class HomeScreen(
    val userMessage: String = ""
): Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val scaffoldState = rememberScaffoldState()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        val vm = getScreenModel<HomeScreenModel>()
        val uiState by vm.uiState.collectAsState()

        val singleImagePicker = rememberFilePickerLauncher(mode = PickerMode.Single, type = PickerType.Image, onResult = vm::onImagePickerResult)

        val homeScreenState = HomeUiState(
            image = uiState.image,
            selectedFilm = uiState.selectedFilm,
            isLoading = uiState.isLoading,
            loadingMessage = uiState.loadingMessage,
            showBottomSheet = uiState.showBottomSheet,
            filmLuts = uiState.filmLuts,
            userMessage = uiState.userMessage,
            onRefresh = vm::refresh,
            onImageChooseClick = singleImagePicker::launch,
            onFilmBoxClick = vm::showFilmLutsBottomSheet,
            onDismissRequest = vm::dismissFilmLutBottomSheet,
            onItemClick = vm::selectFilmLut,
            onVisibilityClick = vm::showOriginalImage,
            onImageResetClick = vm::resetImage,
            onSettingsClick = { navigator.push(SettingsScreen()) },
            onImageExportClick = vm::exportImage,
            snackbarMessageShown = vm::snackbarMessageShown
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

        homeScreenState.showBottomSheet.let {
            when (it) {
                BottomSheetState.COLLAPSED -> {}
                BottomSheetState.EXPANDED -> scope.launch { sheetState.expand() }
                BottomSheetState.HIDDEN -> scope.launch { sheetState.hide() }
            }
        }

        FilmLutsListBottomSheet(
            state = homeScreenState,
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
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(360.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                    onClick = state.onImageChooseClick
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        state.image?.let {
                            CoilZoomAsyncImage(
                                modifier = Modifier.fillMaxSize(),
                                zoomState = zoomState,
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data("${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/${it.substringBefore("?")}")
                                    .memoryCacheKey(it)
                                    .diskCacheKey(it)
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    .build(),
                                contentDescription = null,
                                scrollBar = null
                            )
                        } ?: IconButton(
                            modifier = Modifier.align(Alignment.Center).size(150.dp),
                            onClick = state.onImageChooseClick
                        ) {
                            Column {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_image_add_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(65.dp, 65.dp).align(Alignment.CenterHorizontally)
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
            Spacer(modifier = Modifier.size(23.dp))
            Divider(modifier = Modifier.padding(28.dp, 0.dp))
            Spacer(modifier = Modifier.size(23.dp))
            FilmLutBox(
                modifier = Modifier.fillMaxWidth(),
                selectedFilm = state.selectedFilm,
                onFilmBoxClick = state.onFilmBoxClick,
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FilmLutsListBottomSheet(
        state: HomeUiState,
        sheetState: SheetState,
    ) {
        val listState: LazyListState = rememberLazyListState()
        if (state.showBottomSheet == BottomSheetState.HIDDEN) return
        ModalBottomSheet(
            onDismissRequest = state.onDismissRequest,
            sheetState = sheetState,
            scrimColor = if (state.showBottomSheet == BottomSheetState.COLLAPSED) Color.Transparent else BottomSheetDefaults.ScrimColor,
            contentWindowInsets = { WindowInsets.ime }
        ) {
            if (state.showBottomSheet == BottomSheetState.COLLAPSED) {
                Column {
                    FilmLutBox(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        selectedFilm = state.selectedFilm ?: state.filmLuts.first(),
                        onFilmBoxClick = state.onFilmBoxClick
                    )
                    Spacer(modifier = Modifier.size(23.dp))
                }
            } else {
                FilmLutsList(
                    listState = listState,
                    filmLuts = state.filmLuts,
                    selectedFilm = state.selectedFilm,
                    onItemClick = {
                        state.onItemClick(it)
                    }
                )
            }
        }
    }


    @Composable
    private fun FilmLutBox(modifier: Modifier = Modifier, selectedFilm: FilmLut?, onFilmBoxClick: () -> Unit) {
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
                    Text(text = stringResource(Res.string.film),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 2.dp)
                    )

                    Text(text = selectedFilm?.name ?: stringResource(Res.string.select_your_film),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 2.dp)
                    )
                }
            }

        }
    }


    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun FilmLutsList(
        listState: LazyListState,
        filmLuts: List<FilmLut>,
        selectedFilm: FilmLut?,
        onItemClick: (film: FilmLut) -> Unit
    ) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredFilmLuts = filmLuts.filter {
            searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)
        }
        val sortedAndGrouped = filteredFilmLuts.groupBy { it.category }

        Column {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                placeholder = { Text(text = stringResource(Res.string.search)) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                },
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
            )

            LazyColumn(state = listState) {
                sortedAndGrouped.forEach { (category, films) ->
                    stickyHeader {
                        CategoryHeader(category)
                    }
                    items(films) { film ->
                        FilmItem(film = film, selectedFilm = selectedFilm, onItemClick = onItemClick)
                    }
                }
            }
        }
    }



    @Composable
    fun CategoryHeader(category: String) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = category,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 3.dp)
                    .fillMaxWidth()
            )
        }

    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun FilmItem(
        film: FilmLut,
        selectedFilm: FilmLut?,
        onItemClick: (film: FilmLut) -> Unit
    ) {
        val isSelected = film == selectedFilm
        val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface

        Surface(
            color = backgroundColor,
            onClick = { onItemClick(film) }
        ) {
            Row(modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth()) {
                Image(
                    painter = rememberImagePainter(GITHUB_BASE_URL + film.image_url),
                    contentDescription = film.name,
                    modifier = Modifier.height(64.dp).width(114.dp),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = film.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

}

