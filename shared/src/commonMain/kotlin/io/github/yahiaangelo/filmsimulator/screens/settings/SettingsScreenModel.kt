package io.github.yahiaangelo.filmsimulator.screens.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.yahiaangelo.filmsimulator.data.source.SettingsRepository
import io.github.yahiaangelo.filmsimulator.util.WhileUiSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.dsl.module

val settingsScreenModel = module {
    factory { SettingsScreenModel(get()) }
}

data class SettingsUiState(
    val exportQuality: Int = 0,
    val userMessage: String? = null,
)
class SettingsScreenModel(val repository: SettingsRepository): ScreenModel {

    private val _exportQuality: MutableStateFlow<Int> = MutableStateFlow(repository.getSettings().exportQuality)
    private val _userMessage: MutableStateFlow<String?> = MutableStateFlow(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        _exportQuality, _userMessage
    ) { exportQuality, userMessage ->
        SettingsUiState(
            exportQuality = exportQuality,
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
}