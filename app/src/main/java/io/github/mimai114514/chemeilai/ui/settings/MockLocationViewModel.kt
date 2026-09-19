package io.github.mimai114514.chemeilai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mimai114514.chemeilai.AppContainer
import io.github.mimai114514.chemeilai.data.local.LocationStore
import io.github.mimai114514.chemeilai.data.model.MockLocation
import io.github.mimai114514.chemeilai.data.model.MockSwitchRule
import io.github.mimai114514.chemeilai.location.LocationResolver
import io.github.mimai114514.chemeilai.location.LocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MockLocationUiState(
    val useMock: Boolean = false,
    val effectiveMock: Boolean = false,
    val mockLocation: MockLocation? = null,
    val rules: List<MockSwitchRule> = emptyList(),
    val message: String? = null,
)

class MockLocationViewModel(
    private val resolver: LocationResolver,
    private val store: LocationStore,
) : ViewModel() {

    private val _state = MutableStateFlow(MockLocationUiState())
    val state: StateFlow<MockLocationUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        _state.update {
            it.copy(
                useMock = store.manualUseMock(),
                effectiveMock = resolver.useMockNow(),
                mockLocation = store.mockLocation(),
                rules = store.rules(),
            )
        }
    }

    fun setUseMock(value: Boolean) {
        viewModelScope.launch {
            store.setManualUseMock(value)
            reload()
        }
    }

    fun saveCurrentLocation() {
        viewModelScope.launch {
            when (val result = resolver.realCurrentLocation()) {
                is LocationResult.Success -> {
                    store.saveMockLocation(
                        MockLocation(
                            lat = result.location.latitude,
                            lng = result.location.longitude,
                            savedAt = System.currentTimeMillis(),
                        ),
                    )
                    reload()
                    showMessage("已保存当前定位")
                }

                LocationResult.PermissionMissing -> showMessage("缺少定位权限")
                LocationResult.ServicesDisabled -> showMessage("系统定位已关闭")
                LocationResult.Unavailable -> showMessage("无法获取当前定位")
            }
        }
    }

    fun importFromText(text: String) {
        val parsed = parseLocation(text)
        if (parsed == null) {
            showMessage("剪贴板内容无法解析，应为 纬度,经度")
            return
        }
        viewModelScope.launch {
            store.saveMockLocation(parsed.copy(savedAt = System.currentTimeMillis()))
            reload()
            showMessage("已从剪贴板导入")
        }
    }

    fun clearMockLocation() {
        viewModelScope.launch {
            store.clearMockLocation()
            reload()
            showMessage("已清除模拟坐标")
        }
    }

    fun addRule(hour: Int, minute: Int, days: List<Int>, useMock: Boolean) {
        viewModelScope.launch {
            val rules = store.rules() + MockSwitchRule(
                id = UUID.randomUUID().toString(),
                hour = hour,
                minute = minute,
                days = days.sorted(),
                useMock = useMock,
            )
            store.saveRules(rules)
            reload()
        }
    }

    fun toggleRule(id: String, enabled: Boolean) {
        viewModelScope.launch {
            store.saveRules(store.rules().map { if (it.id == id) it.copy(enabled = enabled) else it })
            reload()
        }
    }

    fun deleteRule(id: String) {
        viewModelScope.launch {
            store.saveRules(store.rules().filterNot { it.id == id })
            reload()
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun notifyMessage(text: String) {
        showMessage(text)
    }

    private fun showMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    /** 支持 "23.1234,111.5678" 或 "备注,23.1234,111.5678"（中英文逗号均可）。 */
    private fun parseLocation(text: String): MockLocation? {
        val parts = text.trim().replace('，', ',').split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val lat = parts[parts.size - 2].toDoubleOrNull() ?: return null
        val lng = parts[parts.size - 1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        val label = if (parts.size > 2) parts.dropLast(2).joinToString(",").takeIf { it.isNotBlank() } else null
        return MockLocation(lat = lat, lng = lng, label = label)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { MockLocationViewModel(container.locationResolver, container.locationStore) }
        }
    }
}
