package io.github.mimai114514.chemeilai.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.model.NearbyStop
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import io.github.mimai114514.chemeilai.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NearbyUiState(
    val loading: Boolean = true,
    val cityName: String? = null,
    val stops: List<NearbyStop> = emptyList(),
    val error: String? = null,
    val permissionRequired: Boolean = false,
)

class NearbyViewModel(
    private val repository: CheLaileRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, permissionRequired = false) }

            if (!locationProvider.hasPermission()) {
                _state.update { it.copy(loading = false, permissionRequired = true) }
                return@launch
            }

            val location = locationProvider.currentLocation()
            if (location == null) {
                _state.update { it.copy(loading = false, error = "无法获取当前位置，请检查定位是否开启") }
                return@launch
            }

            runCatching { repository.nearby(location.latitude, location.longitude) }
                .onSuccess { nearby ->
                    _state.update {
                        it.copy(
                            loading = false,
                            cityName = nearby.city.cityName,
                            stops = nearby.stops,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(loading = false, error = throwable.message ?: "加载失败")
                    }
                }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { NearbyViewModel(container.repository, container.locationProvider) }
        }
    }
}
