package io.github.mimai114514.chemeilai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val START_DESTINATION_NEARBY = "nearby"
const val START_DESTINATION_FAVORITES = "favorites"

data class SettingsUiState(
    val cityName: String? = null,
    val startDestination: String = START_DESTINATION_NEARBY,
)

class SettingsViewModel(
    private val repository: CheLaileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    cityName = repository.currentCity()?.cityName,
                    startDestination = repository.startDestination(),
                )
            }
        }
    }

    fun setStartDestination(value: String) {
        viewModelScope.launch {
            repository.setStartDestination(value)
            _state.update { it.copy(startDestination = value) }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container.repository) }
        }
    }
}
