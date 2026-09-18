package io.github.mimai114514.chemeilai.ui.favorites

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.model.Favorite
import io.github.mimai114514.chemeilai.data.model.FavoriteType
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import io.github.mimai114514.chemeilai.location.LocationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoriteLineStatus(
    val favorite: Favorite,
    val stationName: String? = null,
    val distanceMeters: Int? = null,
    val etaText: String? = null,
    val ready: Boolean = false,
    val stationId: String? = null,
    val lineNo: String? = null,
    val direction: Int? = null,
)

data class FavoritesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val stationFavorites: List<Favorite> = emptyList(),
    val lineFavorites: List<FavoriteLineStatus> = emptyList(),
) {
    val isEmpty: Boolean get() = stationFavorites.isEmpty() && lineFavorites.isEmpty()
}

class FavoritesViewModel(
    private val repository: CheLaileRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeFavorites().collectLatest { favorites ->
                val stations = favorites.filter { it.type == FavoriteType.STATION }
                val lines = favorites.filter { it.type == FavoriteType.LINE }
                _state.update { it.copy(stationFavorites = stations) }
                resolveLines(lines)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            resolveLines(_state.value.lineFavorites.map { it.favorite })
        }
    }

    fun remove(favorite: Favorite) {
        viewModelScope.launch {
            repository.removeFavorite(favorite.id)
        }
    }

    private suspend fun resolveLines(favorites: List<Favorite>) {
        val location = locationProvider.lastKnown()
        val statuses = coroutineScope {
            favorites.map { favorite -> async { resolveLine(favorite, location) } }.awaitAll()
        }
        _state.update { it.copy(loading = false, refreshing = false, lineFavorites = statuses) }
    }

    private suspend fun resolveLine(favorite: Favorite, location: Location?): FavoriteLineStatus {
        val name = favorite.lineName.orEmpty()
        if (name.isBlank() || location == null) return FavoriteLineStatus(favorite)
        val arrival = runCatching {
            repository.lineArrivalAtNearestStation(name, location.latitude, location.longitude)
        }.getOrNull() ?: return FavoriteLineStatus(favorite)
        return FavoriteLineStatus(
            favorite = favorite,
            stationName = arrival.stationName,
            distanceMeters = arrival.distanceMeters,
            etaText = arrival.etaMinutes?.let { "$it 分钟" },
            ready = true,
            stationId = arrival.stationId,
            lineNo = arrival.lineNo,
            direction = arrival.direction,
        )
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { FavoritesViewModel(container.repository, container.locationProvider) }
        }
    }
}
