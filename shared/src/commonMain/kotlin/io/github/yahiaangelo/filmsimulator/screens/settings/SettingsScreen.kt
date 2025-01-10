package io.github.yahiaangelo.filmsimulator.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import film_simulator.shared.generated.resources.Res
import film_simulator.shared.generated.resources.app_version
import film_simulator.shared.generated.resources.contact
import film_simulator.shared.generated.resources.default_picker
import film_simulator.shared.generated.resources.developer
import film_simulator.shared.generated.resources.files
import film_simulator.shared.generated.resources.image_export_quality
import film_simulator.shared.generated.resources.images
import film_simulator.shared.generated.resources.cancel
import film_simulator.shared.generated.resources.settings
import film_simulator.shared.generated.resources.source_code
import io.github.yahiaangelo.filmsimulator.util.AppContext
import io.github.yahiaangelo.filmsimulator.util.Platform
import org.jetbrains.compose.resources.stringResource
import sh.calvin.autolinktext.rememberAutoLinkText


class SettingsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scaffoldState = rememberScaffoldState()
        val snackbarHostState = remember { SnackbarHostState() }
        val showDefaultPickerDialog = remember { mutableStateOf(false) }
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
                        Text(
                            stringResource(Res.string.settings),
                            color = MaterialTheme.colorScheme.onSurface
                        )
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
                    .fillMaxHeight(),
            ) {
                SettingsSlider(
                    name = stringResource(Res.string.image_export_quality),
                    value = uiState.exportQuality.toFloat(),
                    steps = 4,
                    range = 25f..100f,
                    onValueChange = vm::updateExportQualitySettings
                )
                Spacer(modifier = Modifier.padding(16.dp))
                ListItem(
                    modifier = Modifier.clickable {
                        showDefaultPickerDialog.value = true
                    },
                    text = {
                        Text(
                            text = stringResource(Res.string.default_picker),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    secondaryText = {
                        Text(
                            text = stringResource(uiState.defaultPicker.getString()),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
                Spacer(modifier = Modifier.padding(16.dp))
                Divider(color = MaterialTheme.colorScheme.secondaryContainer, thickness = 1.dp)
                AboutSection()
            }
        }

        uiState.userMessage?.let { message ->
            LaunchedEffect(scaffoldState, vm, message) {
                snackbarHostState.showSnackbar(message)
                vm.snackbarMessageShown()
            }
        }

        DefaultPickerDialog(showDefaultPickerDialog.value, onItemClick = vm::updateDefaultPickerSettings) {
            showDefaultPickerDialog.value = false
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    private fun SettingsSlider(
        name: String,
        value: Float,
        steps: Int,
        range: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit
    ) {

        ListItem {
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

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun AboutSection() {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            ListItem(
                text = {
                    Text(
                        text = stringResource(Res.string.app_version),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                secondaryText = {
                    Text(
                        text = Platform(AppContext).getAppVersion(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            )

            ListItem(
                text = {
                    Text(
                        text = stringResource(Res.string.developer),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                secondaryText = {
                    Text(
                        text = "YahiaAngelo",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            )

            ListItem(
                text = {
                    Text(
                        text = stringResource(Res.string.source_code),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                secondaryText = {
                    Text(
                        AnnotatedString.rememberAutoLinkText(
                            "https://github.com/YahiaAngelo".trimMargin()
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            )

            ListItem(
                text = {
                    Text(
                        text = stringResource(Res.string.contact),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                secondaryText = {
                    Text(
                        AnnotatedString.rememberAutoLinkText(
                            "https://x.com/YahiaDev".trimMargin()
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            )


        }
    }

    @OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun DefaultPickerDialog(
        show: Boolean,
        onItemClick: (DefaultPickerType) -> Unit,
        onDismiss: () -> Unit
    ) {
        if (show) {
            BasicAlertDialog(
                onDismissRequest = onDismiss
            ) {
                Surface(
                    modifier = Modifier.wrapContentWidth().wrapContentHeight(),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = AlertDialogDefaults.TonalElevation
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.default_picker),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ListItem(
                            text = {
                                Text(
                                    text = stringResource(Res.string.images),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            modifier = Modifier.clickable {
                                onItemClick(DefaultPickerType.IMAGES)
                                onDismiss()
                            }
                        )
                        ListItem(
                            text = {
                                Text(
                                    text = stringResource(Res.string.files),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            modifier = Modifier.clickable {
                                onItemClick(DefaultPickerType.FILES)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        TextButton(
                            modifier = Modifier.align(Alignment.End),
                            onClick = onDismiss

                        ) {
                            Text(
                                text = stringResource(Res.string.cancel),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}