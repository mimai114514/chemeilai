package io.github.mimai114514.chemeilai.ui.city

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.model.City
import io.github.mimai114514.chemeilai.data.model.CityOption
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CityPickerUiState(
    val loading: Boolean = true,
    val query: String = "",
    val results: List<CityOption> = emptyList(),
    val error: String? = null,
    val selected: Boolean = false,
)

class CityPickerViewModel(
    private val repository: CheLaileRepository,
) : ViewModel() {

    private var allCities: List<CityOption> = emptyList()

    private val _state = MutableStateFlow(CityPickerUiState())
    val state: StateFlow<CityPickerUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.allCities() }
                .onSuccess { cities ->
                    allCities = cities.sortedWith(
                        compareByDescending<CityOption> { it.hot }
                            .thenBy { it.pinyin.lowercase() },
                    )
                    _state.update { it.copy(loading = false, results = filter(it.query)) }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(loading = false, error = throwable.message ?: "城市列表加载失败") }
                }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, results = filter(query)) }
    }

    fun select(city: CityOption) {
        viewModelScope.launch {
            repository.selectCity(City(city.cityId, city.name))
            _state.update { it.copy(selected = true) }
        }
    }

    private fun filter(query: String): List<CityOption> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return allCities
        val lower = trimmed.lowercase()
        return allCities.filter { city ->
            city.name.contains(trimmed, ignoreCase = true) ||
                city.pinyin.lowercase().contains(lower)
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { CityPickerViewModel(container.repository) }
        }
    }
}
