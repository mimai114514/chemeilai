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

    private fun randomBrowserId(): String {
        val random = (0..0xFFFFFF).random().toString(36).padStart(5, '0')
        return "browser_${System.currentTimeMillis().toString(36)}_$random"
    }

    private companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
    }
}
