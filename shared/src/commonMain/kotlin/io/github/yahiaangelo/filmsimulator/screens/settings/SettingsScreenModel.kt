package io.github.yahiaangelo.filmsimulator.screens.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import film_simulator.shared.generated.resources.Res
import film_simulator.shared.generated.resources.files
import film_simulator.shared.generated.resources.images
import io.github.yahiaangelo.filmsimulator.data.source.SettingsRepository
import io.github.yahiaangelo.filmsimulator.util.WhileUiSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.dsl.module

val settingsScreenModel = module {
    factory { SettingsScreenModel(get()) }
}

data class SettingsUiState(
    val exportQuality: Int = 0,
    val defaultPicker: DefaultPickerType = DefaultPickerType.IMAGES,
    val userMessage: String? = null,
)

enum class DefaultPickerType {
    IMAGES,
    FILES;

    fun getString(): StringResource {
        return when (this) {
            IMAGES -> Res.string.images
            FILES -> Res.string.files
        }
    }
}
class SettingsScreenModel(val repository: SettingsRepository): ScreenModel {

    private val _exportQuality: MutableStateFlow<Int> = MutableStateFlow(repository.getSettings().exportQuality)
    private val _defaultPicker: MutableStateFlow<DefaultPickerType> = MutableStateFlow(repository.getSettings().defaultPicker)
    private val _userMessage: MutableStateFlow<String?> = MutableStateFlow(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        _exportQuality, _defaultPicker, _userMessage
    ) { exportQuality, defaultPicker, userMessage ->
        SettingsUiState(
            exportQuality = exportQuality,
            defaultPicker = defaultPicker,
            userMessage = userMessage
        )
    }
        .stateIn(
            scope = screenModelScope,
            started = WhileUiSubscribed,
            initialValue = SettingsUiState()
        )



    fun snackbarMessageShown() {
        _userMessage.value = null
    }

    fun updateExportQualitySettings(quality: Float) {
        screenModelScope.launch {
            repository.getSettings().exportQuality = quality.toInt()
            _exportQuality.emit(quality.toInt())
        }
    }

    fun updateDefaultPickerSettings(defaultPicker: DefaultPickerType) {
        screenModelScope.launch {
            repository.getSettings().defaultPicker = defaultPicker
            _defaultPicker.emit(defaultPicker)
        }
    }
}