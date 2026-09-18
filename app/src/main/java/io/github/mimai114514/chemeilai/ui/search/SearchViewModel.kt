package io.github.mimai114514.chemeilai.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.model.SearchResult
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import io.github.mimai114514.chemeilai.location.LocationProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val result: SearchResult? = null,
    val error: String? = null,
    val cityId: String? = null,
    val cityName: String? = null,
    val cityResolved: Boolean = false,
)

class SearchViewModel(
    private val repository: CheLaileRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        refreshCity()
    }

    fun refreshCity() {
        viewModelScope.launch {
            val city = repository.currentCity()
            _state.update { it.copy(cityId = city?.cityId, cityName = city?.cityName, cityResolved = true) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, error = null) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _state.update { it.copy(loading = false, result = null) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            _state.update { it.copy(loading = true) }
            val location = locationProvider.lastKnown()
            runCatching {
                repository.search(query, location?.latitude, location?.longitude)
            }
                .onSuccess { result ->
                    _state.update { it.copy(loading = false, result = result) }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(loading = false, error = throwable.message ?: "搜索失败") }
                }
        }
    }

    companion object {
        private const val DEBOUNCE_MILLIS = 350L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SearchViewModel(container.repository, container.locationProvider) }
        }
    }
}
