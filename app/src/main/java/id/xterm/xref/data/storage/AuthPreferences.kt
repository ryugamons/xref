package id.xterm.xref.data.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import id.xterm.xref.XrefApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

object AuthPreferences {
    private val dataStore = XrefApplication.getContext().dataStore

    private val KEY_REFEREE_ID = stringPreferencesKey("referee_id")
    private val KEY_REFEREE_PASSWORD = stringPreferencesKey("referee_password")
    private val KEY_BROADCAST_ID = stringPreferencesKey("broadcast_id")
    private val KEY_BROADCAST_PASSWORD = stringPreferencesKey("broadcast_password")

    suspend fun saveRefereeAuth(id: String, password: String) {
        dataStore.edit { prefs ->
            prefs[KEY_REFEREE_ID] = id
            prefs[KEY_REFEREE_PASSWORD] = password
        }
    }

    suspend fun saveBroadcastAuth(id: String, password: String) {
        dataStore.edit { prefs ->
            prefs[KEY_BROADCAST_ID] = id
            prefs[KEY_BROADCAST_PASSWORD] = password
        }
    }

    suspend fun getRefereeId(): String = dataStore.data.map { it[KEY_REFEREE_ID] ?: "" }.first()
    suspend fun getRefereePassword(): String = dataStore.data.map { it[KEY_REFEREE_PASSWORD] ?: "" }.first()
    suspend fun getBroadcastId(): String = dataStore.data.map { it[KEY_BROADCAST_ID] ?: "" }.first()
    suspend fun getBroadcastPassword(): String = dataStore.data.map { it[KEY_BROADCAST_PASSWORD] ?: "" }.first()
}
