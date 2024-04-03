package io.github.yahiaangelo.filmsimulator.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow


class SettingsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scaffoldState = rememberScaffoldState()
        val snackbarHostState = remember { SnackbarHostState() }


        val vm = getScreenModel<SettingsScreenModel>()
        val uiState by vm.uiState.collectAsState()



        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text("Settings", color = MaterialTheme.colorScheme.onSurface)
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            navigator.pop()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }
                    }
                )
            },
        ) { paddingValues ->

            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                SettingsSlider(
                    name = "Image Export Quality",
                    value = uiState.exportQuality.toFloat(),
                    steps = 4,
                    range = 25f..100f,
                    onValueChange = vm::updateExportQualitySettings
                )
            }
        }

        uiState.userMessage?.let { message ->
            LaunchedEffect(scaffoldState, vm, message) {
                snackbarHostState.showSnackbar(message)
                vm.snackbarMessageShown()
            }
        }

    }


    @Composable
    private fun SettingsSlider(
        name: String,
        value: Float,
        steps: Int,
        range: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit
    ) {

        Column {
            Text(
                style = MaterialTheme.typography.labelLarge,
                text = name
            )
            Slider(
                value = value,
                onValueChange = {
                    onValueChange(it)
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                    inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                steps = steps,
                valueRange = range
            )
            Text(text = value.toInt().toString())
        }

    }
}