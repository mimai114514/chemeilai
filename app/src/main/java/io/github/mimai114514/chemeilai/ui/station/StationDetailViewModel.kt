package io.github.mimai114514.chemeilai.ui.station

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.model.Favorite
import io.github.mimai114514.chemeilai.data.model.FavoriteType
import io.github.mimai114514.chemeilai.data.model.StationLine
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import io.github.mimai114514.chemeilai.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StationDetailUiState(
    val loading: Boolean = true,
    val sId: String = "",
    val name: String = "",
    val distanceMeters: Int? = null,
    val lines: List<StationLine> = emptyList(),
    val error: String? = null,
    val isFavorite: Boolean = false,
)

class StationDetailViewModel(
    private val repository: CheLaileRepository,
    private val locationProvider: LocationProvider,
    private val stationId: String,
    initialName: String,
) : ViewModel() {

    private val _state = MutableStateFlow(StationDetailUiState(name = initialName))
    val state: StateFlow<StationDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val location = locationProvider.lastKnown()
            runCatching {
                repository.stationDetail(
                    stationId = stationId,
                    lat = location?.latitude,
                    lng = location?.longitude,
                )
            }
                .onSuccess { detail ->
                    val favorite = repository.isFavorite(Favorite.stationKey(detail.sId))
                    _state.update {
                        it.copy(
                            loading = false,
                            sId = detail.sId,
                            name = detail.name,
                            distanceMeters = detail.distanceMeters,
                            lines = detail.lines,
                            isFavorite = favorite,
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

    fun toggleFavorite() {
        val current = _state.value
        val resolvedId = current.sId.ifBlank { stationId }
        if (resolvedId.isBlank()) return
        viewModelScope.launch {
            val key = Favorite.stationKey(resolvedId)
            if (current.isFavorite) {
                repository.removeFavorite(key)
                _state.update { it.copy(isFavorite = false) }
            } else {
                repository.addFavorite(
                    Favorite(
                        id = key,
                        type = FavoriteType.STATION,
                        stationId = resolvedId,
                        stationName = current.name,
                    ),
                )
                _state.update { it.copy(isFavorite = true) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, stationId: String, stationName: String) = viewModelFactory {
            initializer {
                StationDetailViewModel(
                    repository = container.repository,
                    locationProvider = container.locationProvider,
                    stationId = stationId,
                    initialName = stationName,
                )
            }
        }
    }
}
