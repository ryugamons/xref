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
    private val KEY_REFEREE_TOKEN = stringPreferencesKey("referee_token")
    private val KEY_REFEREE_REFRESH_TOKEN = stringPreferencesKey("referee_refresh_token")
    private val KEY_STARTER_TOKEN = stringPreferencesKey("starter_token")
    private val KEY_STARTER_REFRESH_TOKEN = stringPreferencesKey("starter_refresh_token")
    private val KEY_MATCH_TEAM_COUNT = stringPreferencesKey("match_team_count")
    private val KEY_MATCH_FEE_ENABLED = stringPreferencesKey("match_fee_enabled")
    private val KEY_MATCH_FEE_NOMINAL = stringPreferencesKey("match_fee_nominal")

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

    suspend fun saveTokens(accessToken: String, refreshToken: String, connectionType: String) {
        dataStore.edit { prefs ->
            if (connectionType == "REFEREE") {
                prefs[KEY_REFEREE_TOKEN] = accessToken
                prefs[KEY_REFEREE_REFRESH_TOKEN] = refreshToken
            } else {
                prefs[KEY_STARTER_TOKEN] = accessToken
                prefs[KEY_STARTER_REFRESH_TOKEN] = refreshToken
            }
        }
    }

    suspend fun saveRooms(broadcastRoom: String, battleRooms: List<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_BROADCAST_ROOM] = broadcastRoom
            prefs[KEY_BATTLE_ROOMS] = battleRooms.joinToString(",")
        }
    }

    suspend fun saveMatchSettings(teamCount: Int, feeEnabled: Boolean, nominal: String) {
        dataStore.edit { prefs ->
            prefs[KEY_MATCH_TEAM_COUNT] = teamCount.toString()
            prefs[KEY_MATCH_FEE_ENABLED] = feeEnabled.toString()
            prefs[KEY_MATCH_FEE_NOMINAL] = nominal
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
    
    suspend fun getAccessToken(connectionType: String): String = dataStore.data.map { 
        it[if (connectionType == "REFEREE") KEY_REFEREE_TOKEN else KEY_STARTER_TOKEN] ?: "" 
    }.first()
    
    suspend fun getRefreshToken(connectionType: String): String = dataStore.data.map { 
        it[if (connectionType == "REFEREE") KEY_REFEREE_REFRESH_TOKEN else KEY_STARTER_REFRESH_TOKEN] ?: "" 
    }.first()

    suspend fun getMatchTeamCount(): Int = dataStore.data.map { it[KEY_MATCH_TEAM_COUNT]?.toIntOrNull() ?: 8 }.first()
    suspend fun getMatchFeeEnabled(): Boolean = dataStore.data.map { it[KEY_MATCH_FEE_ENABLED]?.toBoolean() ?: false }.first()
    suspend fun getMatchFeeNominal(): String = dataStore.data.map { it[KEY_MATCH_FEE_NOMINAL] ?: "0" }.first()
}
