package io.github.mimai114514.chemeilai.ui.favorites

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.model.Favorite
import io.github.mimai114514.chemeilai.data.model.FavoriteType
import io.github.mimai114514.chemeilai.data.model.NearestStation
import io.github.mimai114514.chemeilai.data.model.StationLineGroup
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import io.github.mimai114514.chemeilai.location.LocationResolver
import io.github.mimai114514.chemeilai.location.LocationResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoriteLineStatus(
    val favorite: Favorite,
    val direction: Int? = null,
    val directionLabel: String? = null,
    val endName: String? = null,
    val stationName: String? = null,
    val distanceMeters: Int? = null,
    val etaText: String? = null,
    val ready: Boolean = false,
    val stationId: String? = null,
    val lineNo: String? = null,
)

data class FavoriteStationStatus(
    val favorite: Favorite,
    val distanceMeters: Int? = null,
    val lines: List<StationLineGroup> = emptyList(),
) {
    val id: String get() = favorite.id
}

data class FavoritesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val reversed: Boolean = false,
    val stations: List<FavoriteStationStatus> = emptyList(),
    val lines: List<FavoriteLineStatus> = emptyList(),
) {
    val isEmpty: Boolean get() = stations.isEmpty() && lines.isEmpty()
}

class FavoritesViewModel(
    private val repository: CheLaileRepository,
    private val locationResolver: LocationResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    private var favorites: List<Favorite> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeFavorites().collectLatest { list ->
                favorites = list
                resolveAll(showLoading = _state.value.stations.isEmpty() && _state.value.lines.isEmpty())
            }
        }
    }

    fun toggleDirection() {
        _state.update { it.copy(reversed = !it.reversed) }
        viewModelScope.launch { resolveAll() }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            resolveAll()
        }
    }

    fun remove(favorite: Favorite) {
        viewModelScope.launch {
            repository.removeFavorite(favorite.id)
        }
    }

    private suspend fun resolveAll(showLoading: Boolean = false) {
        if (showLoading) _state.update { it.copy(loading = true) }
        val stationFavorites = favorites.filter { it.type == FavoriteType.STATION }
        val lineFavorites = favorites.filter { it.type == FavoriteType.LINE }
        // 先把收藏条目本身渲染出来，定位与实时数据后到再补，避免整页等定位。
        // 复用上一轮已解析到的内容（按 id 匹配），刷新时不会退回只剩站点名的空卡片。
        _state.update { current ->
            val previousStations = current.stations.associateBy { it.id }
            val previousLines = current.lines.associateBy { it.favorite.id }
            current.copy(
                loading = false,
                stations = stationFavorites.map { favorite ->
                    previousStations[favorite.id]?.copy(favorite = favorite)
                        ?: FavoriteStationStatus(favorite)
                },
                lines = lineFavorites.map { favorite ->
                    previousLines[favorite.id]?.copy(favorite = favorite)
                        ?: FavoriteLineStatus(favorite)
                },
            )
        }
        val cached = locationResolver.lastKnown()
        val reversed = _state.value.reversed

        // 站点线路、实时数据都不依赖用户定位，先用（可能为空的）缓存定位立刻开跑，
        // 每张卡片各自完成就上屏，避免最慢的一条拖住整页
        coroutineScope {
            stationFavorites.forEach { favorite -> launch { loadStation(favorite, cached) } }
            lineFavorites.forEach { favorite -> launch { loadLine(favorite, cached, reversed) } }
        }
        _state.update { it.copy(loading = false, refreshing = false) }

        // 没有缓存定位时主动要一次定位，拿到后补齐距离与依赖定位的线路实时
        if (cached != null) return
        val fresh = locationResolver.currentLocation()
        if (fresh !is LocationResult.Success) return
        val location = fresh.location
        val pendingStations = _state.value.stations.filter { it.distanceMeters == null }
        val pendingLines = _state.value.lines.filter { !it.ready }
        coroutineScope {
            pendingStations.forEach { status -> launch { loadStation(status.favorite, location) } }
            pendingLines.forEach { status -> launch { loadLine(status.favorite, location, reversed) } }
        }
    }

    private suspend fun loadStation(favorite: Favorite, location: Location?) {
        val status = resolveStation(favorite, location)
        _state.update { current ->
            current.copy(stations = current.stations.map { if (it.id == favorite.id) status else it })
        }
    }

    private suspend fun loadLine(favorite: Favorite, location: Location?, reversed: Boolean) {
        val status = resolveLine(favorite, location, reversed)
        _state.update { current ->
            current.copy(lines = current.lines.map { if (it.favorite.id == favorite.id) status else it })
        }
    }

    private suspend fun resolveStation(favorite: Favorite, location: Location?): FavoriteStationStatus {
        val stationId = favorite.stationId ?: return FavoriteStationStatus(favorite)
        val detail = runCatching {
            repository.stationDetail(stationId, location?.latitude, location?.longitude)
        }.getOrNull() ?: return FavoriteStationStatus(favorite)
        val distance = location?.let {
            runCatching {
                repository.stationDistanceMeters(stationId, it.latitude, it.longitude)
            }.getOrNull()
        }
        return FavoriteStationStatus(
            favorite = favorite,
            distanceMeters = distance,
            lines = detail.lines,
        )
    }

    private suspend fun resolveLine(
        favorite: Favorite,
        location: Location?,
        reversed: Boolean,
    ): FavoriteLineStatus {
        val name = favorite.lineName.orEmpty()
        if (name.isBlank() || location == null) return FavoriteLineStatus(favorite)
        val nearestStations = runCatching {
            repository.lineNearestStations(name, location.latitude, location.longitude)
        }.getOrNull().orEmpty()
        if (nearestStations.isEmpty()) return FavoriteLineStatus(favorite)

        val auto = nearestStations.minByOrNull { it.distanceMeters } ?: return FavoriteLineStatus(favorite)
        val selected: NearestStation = if (!reversed) {
            auto
        } else {
            nearestStations.firstOrNull { it.direction != auto.direction } ?: auto
        }

        val arrival = runCatching {
            repository.arrivalAt(selected, name, location.latitude, location.longitude)
        }.getOrNull()
        return FavoriteLineStatus(
            favorite = favorite,
            direction = selected.direction,
            directionLabel = selected.directionLabel,
            endName = selected.endName,
            stationName = arrival?.stationName ?: selected.stationName,
            distanceMeters = selected.distanceMeters,
            etaText = arrival?.etaMinutes?.let { "$it 分钟" },
            ready = arrival != null,
            stationId = selected.stationId,
            lineNo = arrival?.lineNo ?: selected.lineNo,
        )
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { FavoritesViewModel(container.repository, container.locationResolver) }
        }
    }
}
