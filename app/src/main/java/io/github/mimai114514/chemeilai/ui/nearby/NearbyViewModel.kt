package io.github.mimai114514.chemeilai.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.model.NearbyStop
import io.github.mimai114514.chemeilai.data.model.StationLineGroup
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import io.github.mimai114514.chemeilai.location.LocationProvider
import io.github.mimai114514.chemeilai.location.LocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NearbyUiState(
    val loading: Boolean = true,
    val cityName: String? = null,
    val manualCity: Boolean = false,
    val stops: List<NearbyStop> = emptyList(),
    val error: String? = null,
    val permissionRequired: Boolean = false,
    val servicesDisabled: Boolean = false,
    val selections: Map<String, Int> = emptyMap(),
)

class NearbyViewModel(
    private val repository: CheLaileRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    permissionRequired = false,
                    servicesDisabled = false,
                )
            }

            val manual = repository.manualCity()
            if (manual != null) {
                _state.update {
                    it.copy(
                        loading = false,
                        cityName = manual.cityName,
                        manualCity = true,
                        stops = emptyList(),
                    )
                }
                return@launch
            }

            if (!locationProvider.hasPermission()) {
                _state.update { it.copy(loading = false, manualCity = false, permissionRequired = true) }
                return@launch
            }

            when (val result = locationProvider.currentLocation()) {
                LocationResult.PermissionMissing ->
                    _state.update { it.copy(loading = false, permissionRequired = true) }

                LocationResult.ServicesDisabled ->
                    _state.update {
                        it.copy(
                            loading = false,
                            servicesDisabled = true,
                            error = "系统定位已关闭，请在系统设置中开启定位",
                        )
                    }

                LocationResult.Unavailable ->
                    _state.update { it.copy(loading = false, error = "无法获取当前位置") }

                is LocationResult.Success ->
                    runCatching {
                        repository.nearby(result.location.latitude, result.location.longitude)
                    }
                        .onSuccess { nearby ->
                            _state.update {
                                it.copy(
                                    loading = false,
                                    manualCity = false,
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
    }

    fun useAutoLocation() {
        viewModelScope.launch {
            repository.clearManualCity()
            refresh()
        }
    }

    fun cycleDirection(stopId: String, group: StationLineGroup) {
        if (group.directions.size < 2) return
        val key = selectionKey(stopId, group.key)
        val current = _state.value.selections[key] ?: group.defaultDirection()?.direction
        val index = group.directions.indexOfFirst { it.direction == current }.coerceAtLeast(0)
        val next = group.directions[(index + 1) % group.directions.size].direction
        _state.update { it.copy(selections = it.selections + (key to next)) }
    }

    companion object {
        fun selectionKey(stopId: String, groupKey: String): String = "$stopId|$groupKey"

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { NearbyViewModel(container.repository, container.locationProvider) }
        }
    }
}
