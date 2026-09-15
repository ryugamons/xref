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
    private val KEY_WALLET_PIN = stringPreferencesKey("wallet_pin")
    private val KEY_BROADCAST_INTERVAL = stringPreferencesKey("broadcast_interval")
    private val KEY_TURNEY_TITLE = stringPreferencesKey("turney_title")
    private val KEY_MULTI_LOGIN_TEMPLATE = stringPreferencesKey("multi_login_template")
    private val KEY_MATCH_CALL_TEMPLATE = stringPreferencesKey("match_call_template")
    private val KEY_READY_CHECK_TEMPLATE = stringPreferencesKey("ready_check_template")
    private val KEY_KICKOFF_WARNING_TEMPLATE = stringPreferencesKey("kickoff_warning_template")
    private val KEY_LICENSE_SIGNATURE = stringPreferencesKey("license_signature")
    private val KEY_LICENSE_CANARY = stringPreferencesKey("license_canary")

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

    suspend fun saveWalletPin(pin: String) {
        dataStore.edit { prefs -> prefs[KEY_WALLET_PIN] = pin }
    }

    suspend fun saveBroadcastInterval(seconds: Int) {
        dataStore.edit { prefs -> prefs[KEY_BROADCAST_INTERVAL] = seconds.toString() }
    }

    suspend fun saveTurneyTitle(title: String) {
        dataStore.edit { prefs -> prefs[KEY_TURNEY_TITLE] = title }
    }

    suspend fun saveMultiLoginTemplate(template: String) {
        dataStore.edit { prefs -> prefs[KEY_MULTI_LOGIN_TEMPLATE] = template }
    }

    suspend fun saveMatchCallTemplate(template: String) {
        dataStore.edit { prefs -> prefs[KEY_MATCH_CALL_TEMPLATE] = template }
    }

    suspend fun saveReadyCheckTemplate(template: String) {
        dataStore.edit { prefs -> prefs[KEY_READY_CHECK_TEMPLATE] = template }
    }

    suspend fun saveKickoffWarningTemplate(template: String) {
        dataStore.edit { prefs -> prefs[KEY_KICKOFF_WARNING_TEMPLATE] = template }
    }

    suspend fun saveLicense(signature: String, canary: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LICENSE_SIGNATURE] = signature
            prefs[KEY_LICENSE_CANARY] = canary
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
    suspend fun getWalletPin(): String = dataStore.data.map { it[KEY_WALLET_PIN] ?: "123456" }.first()
    suspend fun getBroadcastInterval(): Int = dataStore.data.map { it[KEY_BROADCAST_INTERVAL]?.toIntOrNull() ?: 60 }.first()
    suspend fun getTurneyTitle(): String = dataStore.data.map { it[KEY_TURNEY_TITLE] ?: "XREF" }.first()
    suspend fun getMultiLoginTemplate(): String = dataStore.data.map { it[KEY_MULTI_LOGIN_TEMPLATE] ?: "BRING YOUR 10 MULTI-IDS INTO ROOM {room} NOW!" }.first()
    suspend fun getMatchCallTemplate(): String = dataStore.data.map { it[KEY_MATCH_CALL_TEMPLATE] ?: "[BROADCAST] Match starting: {teamA} vs {teamB}. Enter room: {room}" }.first()
    suspend fun getReadyCheckTemplate(): String = dataStore.data.map { it[KEY_READY_CHECK_TEMPLATE] ?: "[REFEREE] Are you ready? Reply 'rd' to confirm!" }.first()
    suspend fun getKickoffWarningTemplate(): String = dataStore.data.map { it[KEY_KICKOFF_WARNING_TEMPLATE] ?: "[REFEREE] KICKOFF STARTED! Do not vote/kick for 60 seconds!" }.first()
    
    suspend fun getLicenseSignature(): String? = dataStore.data.map { it[KEY_LICENSE_SIGNATURE] }.first()
    suspend fun getLicenseCanary(): String? = dataStore.data.map { it[KEY_LICENSE_CANARY] }.first()
}
