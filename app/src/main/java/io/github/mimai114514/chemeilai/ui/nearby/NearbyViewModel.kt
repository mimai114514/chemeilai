package io.github.mimai114514.chemeilai.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.model.NearbyStop
import io.github.mimai114514.chemeilai.data.model.applyFavorites
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import io.github.mimai114514.chemeilai.location.LocationResolver
import io.github.mimai114514.chemeilai.location.LocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NearbyUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val cityName: String? = null,
    val manualCity: Boolean = false,
    val stops: List<NearbyStop> = emptyList(),
    val error: String? = null,
    val permissionRequired: Boolean = false,
    val servicesDisabled: Boolean = false,
    val reversed: Boolean = false,
)

class NearbyViewModel(
    private val repository: CheLaileRepository,
    private val locationResolver: LocationResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = _state.asStateFlow()

    init {
        // 收藏变化时立即同步卡片上的收藏标记与排序，无需重新请求接口
        viewModelScope.launch {
            repository.observeFavoriteLineNames().collect { names ->
                _state.update { current ->
                    if (current.stops.isEmpty()) {
                        current
                    } else {
                        current.copy(
                            stops = current.stops.map { stop ->
                                stop.copy(lines = stop.lines.applyFavorites(names))
                            },
                        )
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val hasContent = _state.value.stops.isNotEmpty() || _state.value.manualCity
            _state.update {
                it.copy(
                    loading = !hasContent && !it.manualCity,
                    refreshing = hasContent,
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
                        refreshing = false,
                        cityName = manual.cityName,
                        manualCity = true,
                        stops = emptyList(),
                    )
                }
                return@launch
            }

            if (!locationResolver.hasPermission()) {
                _state.update {
                    it.copy(loading = false, refreshing = false, manualCity = false, permissionRequired = true)
                }
                return@launch
            }

            when (val result = locationResolver.currentLocation()) {
                LocationResult.PermissionMissing ->
                    _state.update {
                        it.copy(loading = false, refreshing = false, permissionRequired = true)
                    }

                LocationResult.ServicesDisabled ->
                    fail("系统定位已关闭，请在系统设置中开启定位", servicesDisabled = true)

                LocationResult.Unavailable -> fail("无法获取当前位置")

                is LocationResult.Success ->
                    runCatching {
                        repository.nearby(result.location.latitude, result.location.longitude)
                    }
                        .onSuccess { nearby ->
                            _state.update {
                                it.copy(
                                    loading = false,
                                    refreshing = false,
                                    manualCity = false,
                                    cityName = nearby.city.cityName,
                                    stops = nearby.stops,
                                )
                            }
                        }
                        .onFailure { throwable -> fail(throwable.message ?: "加载失败") }
            }
        }
    }

    fun toggleDirection() {
        _state.update { it.copy(reversed = !it.reversed) }
    }

    fun useAutoLocation() {
        viewModelScope.launch {
            repository.clearManualCity()
            refresh()
        }
    }

    /** 有内容时后台刷新失败就保留旧数据，不打扰用户。 */
    private fun fail(message: String, servicesDisabled: Boolean = false) {
        val hasContent = _state.value.stops.isNotEmpty()
        _state.update {
            if (hasContent) {
                it.copy(refreshing = false)
            } else {
                it.copy(loading = false, refreshing = false, servicesDisabled = servicesDisabled, error = message)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { NearbyViewModel(container.repository, container.locationResolver) }
        }
    }
}
