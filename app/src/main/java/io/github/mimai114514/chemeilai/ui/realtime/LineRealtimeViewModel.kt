package io.github.mimai114514.chemeilai.ui.realtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.model.CityLine
import io.github.mimai114514.chemeilai.data.model.Favorite
import io.github.mimai114514.chemeilai.data.model.FavoriteType
import io.github.mimai114514.chemeilai.data.model.Realtime
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import io.github.mimai114514.chemeilai.location.LocationResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LineRealtimeUiState(
    val loading: Boolean = true,
    val lineName: String = "",
    val directions: List<CityLine> = emptyList(),
    val selectedDirection: Int = 0,
    val realtime: Realtime? = null,
    val error: String? = null,
    val isFavorite: Boolean = false,
) {
    val selected: CityLine? get() = directions.firstOrNull { it.direction == selectedDirection }
}

/** 无站点上下文的线路实时页：换向、首末班、站序。 */
class LineRealtimeViewModel(
    private val repository: CheLaileRepository,
    private val locationResolver: LocationResolver,
    private val cityId: String?,
    private val lineName: String,
    private val initialDirection: Int?,
) : ViewModel() {

    private val _state = MutableStateFlow(LineRealtimeUiState(lineName = lineName))
    val state: StateFlow<LineRealtimeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val location = locationResolver.lastKnown()
            val directions = runCatching { repository.lineDirections(lineName) }.getOrNull().orEmpty()
            val selected = directions.firstOrNull { it.direction == initialDirection }
                ?: directions.firstOrNull()
            val direction = selected?.direction ?: initialDirection ?: 0
            val resolvedName = selected?.displayName ?: lineName
            val resolvedCityId = cityId ?: repository.currentCity()?.cityId
            val favorite = repository.isFavorite(Favorite.lineKey(resolvedCityId, resolvedName))
            _state.update {
                it.copy(
                    directions = directions,
                    selectedDirection = direction,
                    lineName = resolvedName,
                    isFavorite = favorite,
                )
            }
            fetch(direction, location?.latitude, location?.longitude)
        }
    }

    fun selectDirection(direction: Int) {
        if (direction == _state.value.selectedDirection) return
        viewModelScope.launch {
            _state.update { it.copy(selectedDirection = direction, loading = true, error = null) }
            val location = locationResolver.lastKnown()
            fetch(direction, location?.latitude, location?.longitude)
        }
    }

    fun toggleFavorite() {
        val current = _state.value
        if (current.lineName.isBlank()) return
        viewModelScope.launch {
            val resolvedCityId = cityId ?: repository.currentCity()?.cityId
            val key = Favorite.lineKey(resolvedCityId, current.lineName)
            if (current.isFavorite) {
                repository.removeFavorite(key)
                _state.update { it.copy(isFavorite = false) }
            } else {
                repository.addFavorite(
                    Favorite(
                        id = key,
                        type = FavoriteType.LINE,
                        lineName = current.lineName,
                        direction = current.selectedDirection,
                        startName = current.selected?.startName,
                        endName = current.selected?.endName,
                        cityId = resolvedCityId,
                    ),
                )
                _state.update { it.copy(isFavorite = true) }
            }
        }
    }

    private suspend fun fetch(direction: Int, lat: Double?, lng: Double?) {
        runCatching { repository.lineRealtime(_state.value.lineName, direction, lat, lng) }
            .onSuccess { realtime ->
                _state.update {
                    it.copy(
                        loading = false,
                        realtime = realtime,
                        error = if (realtime == null) "暂无数据" else null,
                    )
                }
            }
            .onFailure { throwable ->
                _state.update { it.copy(loading = false, error = throwable.message ?: "加载失败") }
            }
    }

    companion object {
        fun factory(
            container: AppContainer,
            cityId: String?,
            lineName: String,
            direction: Int?,
        ) = viewModelFactory {
            initializer {
                LineRealtimeViewModel(
                    repository = container.repository,
                    locationResolver = container.locationResolver,
                    cityId = cityId,
                    lineName = lineName,
                    initialDirection = direction,
                )
            }
        }
    }
}
