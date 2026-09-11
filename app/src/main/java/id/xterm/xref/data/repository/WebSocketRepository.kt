package id.xterm.xref.data.repository

import id.xterm.xref.core.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
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
    
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()
    private var webSocketClient: WebSocketClient? = null
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    private var currentUsername: String? = null
    private var currentPassword: String? = null

    fun connect(username: String, password: String) {
        currentUsername = username
        currentPassword = password
        
        _connectionState.value = ConnectionState.Connecting
        
        webSocketClient = WebSocketClient(
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
                            handleAuthRequired()
                        }
                        "session.ready" -> {
                            _connectionState.value = ConnectionState.Connected
                        }
                        "error" -> {
                            val error = jsonObject.get("data")?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                            _connectionState.value = ConnectionState.Error(error)
                        }
                    }
                }

                override fun onError(error: String) {
                    _connectionState.value = ConnectionState.Error(error)
                }

                override fun onClosed(reason: String) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        )
        webSocketClient?.connect()
    }

    private fun handleAuthRequired() {
        val username = currentUsername ?: return
        val password = currentPassword ?: return
        val loginReq = LoginRequest(username = username, password = password)
        webSocketClient?.send(json.encodeToString(loginReq))
    }

    fun joinRoom(room: String) {
        val req = JoinRoomRequest(room = room)
        webSocketClient?.send(json.encodeToString(req))
    }

    fun ping() {
        webSocketClient?.send("{\"type\":\"ping\"}")
    }

    fun disconnect() {
        webSocketClient?.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }
}
