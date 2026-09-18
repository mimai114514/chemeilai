package io.github.mimai114514.chemeilai.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(name = "chemeilai_session")

class SessionStore(private val context: Context) {

    @Volatile
    private var cachedUserId: String? = null

    suspend fun userId(): String {
        cachedUserId?.let { return it }
        val prefs = context.sessionDataStore.data.first()
        prefs[KEY_USER_ID]?.takeIf { it.isNotBlank() }?.let {
            cachedUserId = it
            return it
        }
        val generated = randomBrowserId()
        context.sessionDataStore.edit { it[KEY_USER_ID] = generated }
        cachedUserId = generated
        return generated
    }

    suspend fun manualCity(): Pair<String, String>? {
        val prefs = context.sessionDataStore.data.first()
        val id = prefs[KEY_MANUAL_CITY_ID]?.takeIf { it.isNotBlank() } ?: return null
        return id to prefs[KEY_MANUAL_CITY_NAME].orEmpty()
    }

    suspend fun setManualCity(cityId: String, cityName: String) {
        context.sessionDataStore.edit {
            it[KEY_MANUAL_CITY_ID] = cityId
            it[KEY_MANUAL_CITY_NAME] = cityName
        }
    }

    suspend fun clearManualCity() {
        context.sessionDataStore.edit {
            it.remove(KEY_MANUAL_CITY_ID)
            it.remove(KEY_MANUAL_CITY_NAME)
        }
    }

    private fun randomBrowserId(): String {
        val random = (0..0xFFFFFF).random().toString(36).padStart(5, '0')
        return "browser_${System.currentTimeMillis().toString(36)}_$random"
    }

    private companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_MANUAL_CITY_ID = stringPreferencesKey("manual_city_id")
        val KEY_MANUAL_CITY_NAME = stringPreferencesKey("manual_city_name")
    }
}
