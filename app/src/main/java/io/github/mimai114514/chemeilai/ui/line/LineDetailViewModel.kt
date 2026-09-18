package io.github.mimai114514.chemeilai.ui.line

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
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
    val detail: LineDetail? = null,
    val error: String? = null,
    val isFavorite: Boolean = false,
)

class LineDetailViewModel(
    private val repository: CheLaileRepository,
    private val locationProvider: LocationProvider,
    private val lineId: String,
    private val displayName: String,
    private val direction: Int,
    private val startName: String?,
    private val endName: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(LineDetailUiState())
    val state: StateFlow<LineDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val location = locationProvider.lastKnown()
            runCatching {
                repository.lineRoute(
                    lineId = lineId,
                    displayName = displayName,
                    direction = direction,
                    startName = startName,
                    endName = endName,
                    lat = location?.latitude,
                    lng = location?.longitude,
                )
            }
                .onSuccess { detail ->
                    val favorite = repository.isFavorite(Favorite.lineKey(lineId, direction))
                    _state.update { it.copy(loading = false, detail = detail, isFavorite = favorite) }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(loading = false, error = throwable.message ?: "加载失败") }
                }
        }
    }

    fun toggleFavorite() {
        val current = _state.value
        val detail = current.detail ?: return
        val key = Favorite.lineKey(lineId, direction)
        viewModelScope.launch {
            if (current.isFavorite) {
                repository.removeFavorite(key)
                _state.update { it.copy(isFavorite = false) }
            } else {
                repository.addFavorite(
                    Favorite(
                        id = key,
                        type = FavoriteType.LINE,
                        lineId = lineId,
                        lineName = detail.displayName,
                        direction = direction,
                        startName = detail.startName,
                        endName = detail.endName,
                    ),
                )
                _state.update { it.copy(isFavorite = true) }
            }
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            lineId: String,
            displayName: String,
            direction: Int,
            startName: String?,
            endName: String?,
        ) = viewModelFactory {
            initializer {
                LineDetailViewModel(
                    repository = container.repository,
                    locationProvider = container.locationProvider,
                    lineId = lineId,
                    displayName = displayName,
                    direction = direction,
                    startName = startName,
                    endName = endName,
                )
            }
        }
    }
}
