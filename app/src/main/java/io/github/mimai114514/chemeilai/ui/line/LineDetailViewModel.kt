package io.github.mimai114514.chemeilai.ui.line

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.model.CityLine
import io.github.mimai114514.chemeilai.data.model.Favorite
import io.github.mimai114514.chemeilai.data.model.FavoriteType
import io.github.mimai114514.chemeilai.data.model.LineDetail
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import io.github.mimai114514.chemeilai.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LineDetailUiState(
    val loading: Boolean = true,
    val lineName: String = "",
    val directions: List<CityLine> = emptyList(),
    val selectedDirection: Int = 0,
    val detail: LineDetail? = null,
    val error: String? = null,
    val isFavorite: Boolean = false,
)

class LineDetailViewModel(
    private val repository: CheLaileRepository,
    private val locationProvider: LocationProvider,
    private val cityId: String?,
    private val lineName: String,
    private val initialDirection: Int?,
    private val initialLineId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(LineDetailUiState(lineName = lineName))
    val state: StateFlow<LineDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val location = locationProvider.lastKnown()
            runCatching {
                val resolvedCityId = cityId ?: repository.currentCity()?.cityId
                val directions = repository.lineDirections(lineName)
                val resolvedName = directions.firstOrNull()?.displayName ?: lineName
                val favorite = repository.isFavorite(Favorite.lineKey(resolvedCityId, resolvedName))
                Loaded(directions, resolvedName, favorite)
            }
                .onSuccess { loaded ->
                    if (loaded.directions.isEmpty()) {
                        if (initialLineId == null) {
                            _state.update { it.copy(loading = false, error = "未找到该线路") }
                        } else {
                            _state.update {
                                it.copy(
                                    lineName = loaded.name,
                                    selectedDirection = initialDirection ?: 0,
                                    isFavorite = loaded.favorite,
                                )
                            }
                            loadRoute(
                                lineId = initialLineId,
                                direction = initialDirection ?: 0,
                                startName = null,
                                endName = null,
                                firstTime = null,
                                lastTime = null,
                                price = null,
                                lat = location?.latitude,
                                lng = location?.longitude,
                            )
                        }
                        return@onSuccess
                    }
                    val selected = loaded.directions.firstOrNull { it.direction == initialDirection }
                        ?: loaded.directions.first()
                    _state.update {
                        it.copy(
                            lineName = loaded.name,
                            directions = loaded.directions,
                            selectedDirection = selected.direction,
                            isFavorite = loaded.favorite,
                        )
                    }
                    loadRoute(selected, location?.latitude, location?.longitude)
                }
                .onFailure { throwable ->
                    _state.update { it.copy(loading = false, error = throwable.message ?: "加载失败") }
                }
        }
    }

    fun selectDirection(direction: Int) {
        val state = _state.value
        if (direction == state.selectedDirection) return
        val target = state.directions.firstOrNull { it.direction == direction } ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, selectedDirection = direction, error = null) }
            val location = locationProvider.lastKnown()
            runCatching { loadRoute(target, location?.latitude, location?.longitude) }
                .onFailure { throwable ->
                    _state.update { it.copy(loading = false, error = throwable.message ?: "加载失败") }
                }
        }
    }

    private suspend fun loadRoute(cityLine: CityLine, lat: Double?, lng: Double?) = loadRoute(
        lineId = cityLine.lineId,
        direction = cityLine.direction,
        startName = cityLine.startName,
        endName = cityLine.endName,
        firstTime = cityLine.firstTime,
        lastTime = cityLine.lastTime,
        price = cityLine.price,
        lat = lat,
        lng = lng,
    )

    private suspend fun loadRoute(
        lineId: String,
        direction: Int,
        startName: String?,
        endName: String?,
        firstTime: String?,
        lastTime: String?,
        price: String?,
        lat: Double?,
        lng: Double?,
    ) {
        val stations = repository.lineRouteStations(lineId, lat, lng)
        val name = _state.value.lineName
        _state.update {
            it.copy(
                loading = false,
                detail = LineDetail(
                    lineId = lineId,
                    displayName = name,
                    direction = direction,
                    startName = startName,
                    endName = endName,
                    firstTime = firstTime,
                    lastTime = lastTime,
                    price = price,
                    stations = stations,
                ),
            )
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
                val detail = current.detail
                val direction = current.directions.firstOrNull { it.direction == current.selectedDirection }
                repository.addFavorite(
                    Favorite(
                        id = key,
                        type = FavoriteType.LINE,
                        lineId = direction?.lineId ?: detail?.lineId,
                        lineNo = direction?.lineNo,
                        lineName = current.lineName,
                        direction = current.selectedDirection,
                        startName = direction?.startName ?: detail?.startName,
                        endName = direction?.endName ?: detail?.endName,
                        cityId = resolvedCityId,
                    ),
                )
                _state.update { it.copy(isFavorite = true) }
            }
        }
    }

    private data class Loaded(
        val directions: List<CityLine>,
        val name: String,
        val favorite: Boolean,
    )

    companion object {
        fun factory(
            container: AppContainer,
            cityId: String?,
            lineName: String,
            direction: Int?,
            lineId: String?,
        ) = viewModelFactory {
            initializer {
                LineDetailViewModel(
                    repository = container.repository,
                    locationProvider = container.locationProvider,
                    cityId = cityId,
                    lineName = lineName,
                    initialDirection = direction,
                    initialLineId = lineId,
                )
            }
        }
    }
}
