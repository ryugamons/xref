package id.xterm.xref.data.repository

import android.util.Log
import id.xterm.xref.core.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    data object Disconnected : ConnectionState()
}

@Singleton
class WebSocketRepository @Inject constructor() {
    companion object {
        private var instance: WebSocketRepository? = null
        fun getInstance(): WebSocketRepository {
            if (instance == null) instance = WebSocketRepository()
            return instance!!
        }
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    private val client = OkHttpClient()
    
    // Multi-session handling map for separate accounts
    private val webSocketClients = ConcurrentHashMap<String, WebSocketClient>()
    private val pingJobs = ConcurrentHashMap<String, Job>()
    
    // Per-connection type state flows
    private val _refereeConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val refereeConnectionState = _refereeConnectionState.asStateFlow()

    private val _starterConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val starterConnectionState = _starterConnectionState.asStateFlow()

    // Per-connection type wallet balance flows
    private val _refereeWalletBalance = MutableStateFlow<String>("0.00 CR")
    val refereeWalletBalance = _refereeWalletBalance.asStateFlow()

    private val _starterWalletBalance = MutableStateFlow<String>("0.00 CR")
    val starterWalletBalance = _starterWalletBalance.asStateFlow()

    private val _events = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun connect(username: String, password: String, connectionType: String) {
        val stateFlow = if (connectionType == "REFEREE") _refereeConnectionState else _starterConnectionState
        stateFlow.value = ConnectionState.Connecting
        
        // Disconnect existing if any
        disconnectSession(connectionType)
        
        val webSocketClient = WebSocketClient(
            url = "wss://developer.mig33.id/developer/ws",
            json = json,
            client = client,
            listener = object : WebSocketClient.WebSocketListener {
                override fun onOpen() {
                    // Waiting for auth.required
                }

                override fun onMessage(raw: String, jsonObject: JsonObject?) {
                    if (jsonObject != null) {
                        _events.tryEmit(jsonObject)
                    }
                    val type = jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
                    when (type) {
                        "auth.required" -> {
                            val loginReq = LoginRequest(username = username, password = password)
                            webSocketClients[connectionType]?.send(json.encodeToString(loginReq))
                        }
                        "session.ready" -> {
                            val dataObj = jsonObject.get("data")?.jsonObject
                            val walletObj = dataObj?.get("wallet")?.jsonObject
                            val balanceMilliCr = walletObj?.get("balance_milli_cr")?.jsonPrimitive?.longOrNull ?: 0L
                            
                            val balanceText = "${balanceMilliCr / 1000} CR"
                            if (connectionType == "REFEREE") _refereeWalletBalance.value = balanceText
                            else _starterWalletBalance.value = balanceText
                            
                            stateFlow.value = ConnectionState.Connected
                            startPingScheduler(connectionType)
                        }
                        "wallet.balance.result", "wallet.update", "wallet.transfer.result" -> {
                            val dataObj = jsonObject.get("data")?.jsonObject
                            val walletObj = dataObj?.get("wallet")?.jsonObject ?: dataObj
                            val balanceMilliCr = walletObj?.get("balance_milli_cr")?.jsonPrimitive?.longOrNull ?: 0L
                            if (balanceMilliCr > 0) {
                                val balanceText = "${balanceMilliCr / 1000} CR"
                                if (connectionType == "REFEREE") _refereeWalletBalance.value = balanceText
                                else _starterWalletBalance.value = balanceText
                            }
                        }
                        "error" -> {
                            stopPingScheduler(connectionType)
                            val dataObj = jsonObject.get("data")?.jsonObject
                            val errorMsg = dataObj?.get("message")?.jsonPrimitive?.contentOrNull 
                                ?: jsonObject.get("message")?.jsonPrimitive?.contentOrNull 
                                ?: "Unknown error"
                            stateFlow.value = ConnectionState.Error(errorMsg)
                        }
                    }
                }

                override fun onError(error: String) {
                    stopPingScheduler(connectionType)
                    stateFlow.value = ConnectionState.Error(error)
                }

                override fun onClosed(reason: String) {
                    stopPingScheduler(connectionType)
                    stateFlow.value = ConnectionState.Disconnected
                }
            }
        )
        
        webSocketClients[connectionType] = webSocketClient
        webSocketClient.connect()
    }

    private fun startPingScheduler(connectionType: String) {
        stopPingScheduler(connectionType)
        val job = repositoryScope.launch {
            while (isActive) {
                delay(30_000)
                webSocketClients[connectionType]?.send("{\"type\":\"ping\"}")
            }
        }
        pingJobs[connectionType] = job
    }

    private fun stopPingScheduler(connectionType: String) {
        pingJobs[connectionType]?.cancel()
        pingJobs.remove(connectionType)
    }

    fun joinRoom(room: String, connectionType: String) {
        val req = JoinRoomRequest(room = room)
        webSocketClients[connectionType]?.send(json.encodeToString(req))
    }

    fun leaveRoom(room: String, connectionType: String) {
        val req = LeaveRoomRequest(room = room)
        webSocketClients[connectionType]?.send(json.encodeToString(req))
    }

    fun disconnectSession(connectionType: String) {
        stopPingScheduler(connectionType)
        webSocketClients[connectionType]?.disconnect()
        webSocketClients.remove(connectionType)
        
        val stateFlow = if (connectionType == "REFEREE") _refereeConnectionState else _starterConnectionState
        stateFlow.value = ConnectionState.Disconnected
    }
}
