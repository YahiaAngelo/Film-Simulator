package io.github.yahiaangelo.filmsimulator.view

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import film_simulator.shared.generated.resources.Res
import film_simulator.shared.generated.resources.app_name
import film_simulator.shared.generated.resources.ic_reset_image_24
import film_simulator.shared.generated.resources.ic_settings_24
import film_simulator.shared.generated.resources.ic_upload_24
import film_simulator.shared.generated.resources.ic_visibility_24
import film_simulator.shared.generated.resources.ic_visibility_off_24
import film_simulator.shared.generated.resources.image_24
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    onVisibilityClick: (show: Boolean) -> Unit,
    onImageChooseClick: () -> Unit,
    onImageResetClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onImageExportClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable (innerPadding: PaddingValues) -> Unit,

) {
    var showOriginalImage by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = {
                       SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text(stringResource(Res.string.app_name), color = MaterialTheme.colorScheme.onSurface)
                },
                actions = {
                    IconButton(onClick = {
                        showOriginalImage = !showOriginalImage
                        onVisibilityClick(showOriginalImage)
                    }) {
                        Icon(
                            painter = painterResource(if (showOriginalImage) Res.drawable.ic_visibility_off_24 else Res.drawable.ic_visibility_24),
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                actions = {
                    IconButton(onClick = onImageChooseClick) {
                        Icon(
                            painter = painterResource(Res.drawable.image_24),
                            contentDescription = null
                        )
                    }
                    IconButton(onClick = onImageResetClick) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_reset_image_24),
                            contentDescription = null
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_settings_24),
                            contentDescription = null
                        )
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.secondary,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp),
                        onClick = onImageExportClick,
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_upload_24),
                            null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        content = content
    )
}