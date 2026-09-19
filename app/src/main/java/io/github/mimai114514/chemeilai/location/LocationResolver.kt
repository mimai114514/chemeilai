package io.github.mimai114514.chemeilai.location

import android.location.Location
import io.github.mimai114514.chemeilai.data.local.LocationStore
import io.github.mimai114514.chemeilai.data.model.MockLocation
import java.util.Calendar

/**
 * 定位来源决策：定时规则（今天已触发的最晚一条）优先于手动开关，两者都不阻止时用真实定位。
 */
class LocationResolver(
    private val provider: LocationProvider,
    private val store: LocationStore,
) {

    fun hasPermission(): Boolean = provider.hasPermission()

    fun isLocationEnabled(): Boolean = provider.isLocationEnabled()

    suspend fun useMockNow(): Boolean {
        val rules = store.rules().filter { it.enabled }
        val calendar = Calendar.getInstance()
        val isoDay = calendar.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 }
        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val fired = rules
            .filter { rule -> rule.matchesDay(isoDay) && rule.minutesOfDay <= nowMinutes }
            .maxByOrNull { rule -> rule.minutesOfDay }
        return fired?.useMock ?: store.manualUseMock()
    }

    suspend fun mockLocation(): MockLocation? = store.mockLocation()

    suspend fun lastKnown(): Location? {
        if (useMockNow()) {
            mockAsLocation()?.let { return it }
        }
        return provider.lastKnown()
    }

    suspend fun currentLocation(timeoutMillis: Long = 25_000): LocationResult {
        if (useMockNow()) {
            mockAsLocation()?.let { return LocationResult.Success(it) }
        }
        return provider.currentLocation(timeoutMillis)
    }

    /** 保存模拟点用：强制取真实定位，不受模拟开关影响。 */
    suspend fun realCurrentLocation(timeoutMillis: Long = 25_000): LocationResult =
        provider.currentLocation(timeoutMillis)

    private suspend fun mockAsLocation(): Location? = store.mockLocation()?.let { mock ->
        Location(MOCK_PROVIDER).apply {
            latitude = mock.lat
            longitude = mock.lng
            time = System.currentTimeMillis()
            accuracy = 5f
        }
    }

    companion object {
        const val MOCK_PROVIDER = "chemeilai_mock"
    }
}
