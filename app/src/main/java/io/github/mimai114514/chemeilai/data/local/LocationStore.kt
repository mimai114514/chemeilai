package io.github.mimai114514.chemeilai.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.mimai114514.chemeilai.data.model.MockLocation
import io.github.mimai114514.chemeilai.data.model.MockSwitchRule
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.locationDataStore by preferencesDataStore(name = "chemeilai_location")

class LocationStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun mockLocation(): MockLocation? {
        val prefs = context.locationDataStore.data.first()
        val lat = prefs[KEY_MOCK_LAT] ?: return null
        val lng = prefs[KEY_MOCK_LNG] ?: return null
        return MockLocation(
            lat = lat,
            lng = lng,
            savedAt = prefs[KEY_MOCK_SAVED_AT] ?: 0L,
            label = prefs[KEY_MOCK_LABEL],
        )
    }

    suspend fun saveMockLocation(location: MockLocation) {
        context.locationDataStore.edit {
            it[KEY_MOCK_LAT] = location.lat
            it[KEY_MOCK_LNG] = location.lng
            it[KEY_MOCK_SAVED_AT] = location.savedAt
            if (location.label.isNullOrBlank()) it.remove(KEY_MOCK_LABEL) else it[KEY_MOCK_LABEL] = location.label
        }
    }

    suspend fun clearMockLocation() {
        context.locationDataStore.edit {
            it.remove(KEY_MOCK_LAT)
            it.remove(KEY_MOCK_LNG)
            it.remove(KEY_MOCK_SAVED_AT)
            it.remove(KEY_MOCK_LABEL)
        }
    }

    suspend fun manualUseMock(): Boolean =
        context.locationDataStore.data.first()[KEY_USE_MOCK] ?: false

    suspend fun setManualUseMock(value: Boolean) {
        context.locationDataStore.edit { it[KEY_USE_MOCK] = value }
    }

    suspend fun rules(): List<MockSwitchRule> {
        val raw = context.locationDataStore.data.first()[KEY_RULES] ?: return emptyList()
        return runCatching { json.decodeFromString<List<MockSwitchRule>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun saveRules(rules: List<MockSwitchRule>) {
        context.locationDataStore.edit {
            it[KEY_RULES] = json.encodeToString(rules.sortedBy { rule -> rule.minutesOfDay })
        }
    }

    private companion object {
        val KEY_MOCK_LAT = doublePreferencesKey("mock_lat")
        val KEY_MOCK_LNG = doublePreferencesKey("mock_lng")
        val KEY_MOCK_SAVED_AT = androidx.datastore.preferences.core.longPreferencesKey("mock_saved_at")
        val KEY_MOCK_LABEL = stringPreferencesKey("mock_label")
        val KEY_USE_MOCK = booleanPreferencesKey("use_mock")
        val KEY_RULES = stringPreferencesKey("switch_rules")
    }
}
