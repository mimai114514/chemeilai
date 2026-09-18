package io.github.mimai114514.chemeilai.ui.favorites

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

data class FavoriteEntry(
    val favorite: Favorite,
    val etaText: String? = null,
)

data class FavoritesUiState(
    val loading: Boolean = true,
    val entries: List<FavoriteEntry> = emptyList(),
    val error: String? = null,
)

class FavoritesViewModel(
    private val repository: CheLaileRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeFavorites().collectLatest { favorites ->
                loadEntries(favorites)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadEntries(repository.favorites())
        }
    }

    private suspend fun loadEntries(favorites: List<Favorite>) {
        _state.update { it.copy(loading = true, error = null) }
        val location = locationProvider.lastKnown()
        val entries = coroutineScope {
            favorites.map { favorite ->
                async { FavoriteEntry(favorite, etaFor(favorite, location?.latitude, location?.longitude)) }
            }.awaitAll()
        }
        _state.update { it.copy(loading = false, entries = entries) }
    }

    fun remove(favorite: Favorite) {
        viewModelScope.launch {
            repository.removeFavorite(favorite.id)
        }
    }

    private suspend fun etaFor(favorite: Favorite, lat: Double?, lng: Double?): String? {
        if (favorite.type != FavoriteType.ROUTE) return null
        val stationId = favorite.stationId ?: return null
        val lineNo = favorite.lineNo ?: return null
        val direction = favorite.direction ?: return null
        val realtime = runCatching {
            repository.realtime(stationId, lineNo, direction, lat, lng)
        }.getOrNull() ?: return null
        val minutes = realtime.buses.firstOrNull()?.etaMinutes
        return if (minutes != null) "$minutes 分钟" else realtime.tip
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { FavoritesViewModel(container.repository, container.locationProvider) }
        }
    }
}
