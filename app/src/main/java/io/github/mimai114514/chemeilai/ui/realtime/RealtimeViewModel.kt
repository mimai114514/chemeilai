package io.github.mimai114514.chemeilai.ui.realtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.model.Favorite
import io.github.mimai114514.chemeilai.data.model.FavoriteType
import io.github.mimai114514.chemeilai.data.model.Realtime
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import io.github.mimai114514.chemeilai.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RealtimeUiState(
    val loading: Boolean = true,
    val realtime: Realtime? = null,
    val error: String? = null,
    val isFavorite: Boolean = false,
)

class RealtimeViewModel(
    private val repository: CheLaileRepository,
    private val locationProvider: LocationProvider,
    private val stationId: String,
    private val lineNo: String,
    private val direction: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(RealtimeUiState())
    val state: StateFlow<RealtimeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val location = locationProvider.lastKnown()
            runCatching {
                repository.realtime(
                    stationId = stationId,
                    lineNo = lineNo,
                    direction = direction,
                    lat = location?.latitude,
                    lng = location?.longitude,
                )
            }
                .onSuccess { realtime ->
                    val favorite = repository.isFavorite(
                        Favorite.lineKey(repository.currentCity()?.cityId, realtime.lineDisplayName),
                    )
                    _state.update { it.copy(loading = false, realtime = realtime, isFavorite = favorite) }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(loading = false, error = throwable.message ?: "加载失败") }
                }
        }
    }

    fun toggleFavorite() {
        val current = _state.value
        val realtime = current.realtime ?: return
        if (realtime.lineDisplayName.isBlank()) return
        viewModelScope.launch {
            val cityId = repository.currentCity()?.cityId
            val key = Favorite.lineKey(cityId, realtime.lineDisplayName)
            if (current.isFavorite) {
                repository.removeFavorite(key)
                _state.update { it.copy(isFavorite = false) }
            } else {
                repository.addFavorite(
                    Favorite(
                        id = key,
                        type = FavoriteType.LINE,
                        lineName = realtime.lineDisplayName,
                        direction = realtime.direction,
                        startName = realtime.startName,
                        endName = realtime.endName,
                        cityId = cityId,
                    ),
                )
                _state.update { it.copy(isFavorite = true) }
            }
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            stationId: String,
            lineNo: String,
            direction: Int,
        ) = viewModelFactory {
            initializer {
                RealtimeViewModel(
                    repository = container.repository,
                    locationProvider = container.locationProvider,
                    stationId = stationId,
                    lineNo = lineNo,
                    direction = direction,
                )
            }
        }
    }
}
