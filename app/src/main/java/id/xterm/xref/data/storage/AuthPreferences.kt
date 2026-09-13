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
    private val KEY_STARTER_ID = stringPreferencesKey("starter_id")
    private val KEY_STARTER_PASSWORD = stringPreferencesKey("starter_password")
    private val KEY_BROADCAST_ROOM = stringPreferencesKey("broadcast_room")
    private val KEY_BATTLE_ROOMS = stringPreferencesKey("battle_rooms")

    suspend fun saveRefereeAuth(id: String, password: String) {
        dataStore.edit { prefs ->
            prefs[KEY_REFEREE_ID] = id
            prefs[KEY_REFEREE_PASSWORD] = password
        }
    }

    suspend fun saveStarterAuth(id: String, password: String) {
        dataStore.edit { prefs ->
            prefs[KEY_STARTER_ID] = id
            prefs[KEY_STARTER_PASSWORD] = password
        }
    }

    suspend fun saveRooms(broadcastRoom: String, battleRooms: List<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_BROADCAST_ROOM] = broadcastRoom
            prefs[KEY_BATTLE_ROOMS] = battleRooms.joinToString(",")
        }
    }

    suspend fun getRefereeId(): String = dataStore.data.map { it[KEY_REFEREE_ID] ?: "" }.first()
    suspend fun getRefereePassword(): String = dataStore.data.map { it[KEY_REFEREE_PASSWORD] ?: "" }.first()
    suspend fun getStarterId(): String = dataStore.data.map { it[KEY_STARTER_ID] ?: "" }.first()
    suspend fun getStarterPassword(): String = dataStore.data.map { it[KEY_STARTER_PASSWORD] ?: "" }.first()
    suspend fun getBroadcastRoom(): String = dataStore.data.map { it[KEY_BROADCAST_ROOM] ?: "" }.first()
    suspend fun getBattleRooms(): List<String> = dataStore.data.map { 
        val rooms = it[KEY_BATTLE_ROOMS] ?: ""
        if (rooms.isEmpty()) emptyList() else rooms.split(",")
    }.first()
}
