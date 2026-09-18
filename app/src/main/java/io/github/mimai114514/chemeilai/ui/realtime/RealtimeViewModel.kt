package io.github.mimai114514.chemeilai.ui.realtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
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
                    _state.update { it.copy(loading = false, realtime = realtime) }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(loading = false, error = throwable.message ?: "加载失败") }
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
