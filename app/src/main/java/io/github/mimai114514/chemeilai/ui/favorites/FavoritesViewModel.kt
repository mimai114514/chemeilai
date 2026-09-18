package io.github.mimai114514.chemeilai.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.model.Favorite
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val loading: Boolean = true,
    val favorites: List<Favorite> = emptyList(),
)

class FavoritesViewModel(
    private val repository: CheLaileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeFavorites().collectLatest { favorites ->
                _state.update { it.copy(loading = false, favorites = favorites) }
            }
        }
    }

    fun remove(favorite: Favorite) {
        viewModelScope.launch {
            repository.removeFavorite(favorite.id)
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { FavoritesViewModel(container.repository) }
        }
    }
}
