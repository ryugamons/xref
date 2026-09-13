package id.xterm.xref.data.repository

import android.util.Log
import id.xterm.xref.core.websocket.*
import id.xterm.xref.data.remote.AuthService
import id.xterm.xref.data.storage.AuthPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
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
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.mig33.id/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val authService = retrofit.create(AuthService::class.java)
    
    private val webSocketClients = ConcurrentHashMap<String, WebSocketClient>()
    private val pingJobs = ConcurrentHashMap<String, Job>()
    private val packetIds = ConcurrentHashMap<String, AtomicInteger>()
    private val sessionUsernames = ConcurrentHashMap<String, String>()
    
    private val _refereeConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val refereeConnectionState = _refereeConnectionState.asStateFlow()

    private val _starterConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val starterConnectionState = _starterConnectionState.asStateFlow()

    private val _refereeWalletBalance = MutableStateFlow<String>("0.00 CR")
    val refereeWalletBalance = _refereeWalletBalance.asStateFlow()

    private val _starterWalletBalance = MutableStateFlow<String>("0.00 CR")
    val starterWalletBalance = _starterWalletBalance.asStateFlow()

    private val _events = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()
    
    private val _roomMessages = MutableSharedFlow<Pair<String, ChatMessage>>(extraBufferCapacity = 128)
    val roomMessages = _roomMessages.asSharedFlow()

    private val _activeRooms = MutableStateFlow<Set<String>>(emptySet())
    val activeRooms = _activeRooms.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private fun nextId(connectionType: String): String {
        val counter = packetIds.getOrPut(connectionType) { AtomicInteger(0) }
        return counter.incrementAndGet().toString()
    }

    suspend fun loginAndConnect(username: String, password: String, connectionType: String) {
        val stateFlow = if (connectionType == "REFEREE") _refereeConnectionState else _starterConnectionState
        stateFlow.value = ConnectionState.Connecting
        
        try {
            val response = authService.login(LoginApiRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val authData = response.body()!!
                
                // Store username for this session
                sessionUsernames[connectionType] = username
                
                // Save tokens for future refresh
                AuthPreferences.saveTokens(authData.accessToken, authData.refreshToken, connectionType)
                
                // Update wallet if available in login response
                authData.user?.wallet?.balanceMilliCr?.let { balance ->
                    val balanceText = "${balance / 1000} CR"
                    if (connectionType == "REFEREE") _refereeWalletBalance.value = balanceText
                    else _starterWalletBalance.value = balanceText
                }
                
                openWebSocket(authData.accessToken, connectionType)
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = try {
                    val errorObj = json.decodeFromString<JsonObject>(errorBody ?: "{}")
                    errorObj["message"]?.jsonPrimitive?.content ?: "Login failed"
                } catch (e: Exception) {
                    "Login failed"
                }
                stateFlow.value = ConnectionState.Error(errorMsg)
            }
        } catch (e: Exception) {
            stateFlow.value = ConnectionState.Error(e.message ?: "Network error")
        }
    }

    private fun updateWalletState(balanceMilliCr: Long, connectionType: String) {
        val balanceText = "${balanceMilliCr / 1000} CR"
        if (connectionType == "REFEREE") _refereeWalletBalance.value = balanceText
        else _starterWalletBalance.value = balanceText
    }

    private fun openWebSocket(token: String, connectionType: String) {
        val stateFlow = if (connectionType == "REFEREE") _refereeConnectionState else _starterConnectionState
        
        val url = "wss://api.mig33.id/ws?token=$token"
        val webSocketClient = WebSocketClient(
            url = url,
            json = json,
            client = okHttpClient,
            listener = object : WebSocketClient.WebSocketListener {
                override fun onOpen() {
                    stateFlow.value = ConnectionState.Connected
                    startPingScheduler(connectionType)
                }

                override fun onMessage(raw: String, jsonObject: JsonObject?) {
                    if (jsonObject != null) {
                        _events.tryEmit(jsonObject)
                    }
                    val type = jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
                    when (type) {
                        "room.message.received" -> {
                            val roomName = jsonObject?.get("room")?.jsonPrimitive?.contentOrNull ?: ""
                            val username = jsonObject?.get("username")?.jsonPrimitive?.contentOrNull ?: "system"
                            val body = jsonObject?.get("body")?.jsonPrimitive?.contentOrNull ?: ""
                            val time = jsonObject?.get("time")?.jsonPrimitive?.contentOrNull ?: ""
                            val kind = jsonObject?.get("message_kind")?.jsonPrimitive?.contentOrNull ?: "text"
                            
                            val isAction = kind == "action" || (body.startsWith("**") && body.endsWith("**"))
                            val msgType = if (isAction) MessageType.ACTION else MessageType.TEXT
                            
                            repositoryScope.launch {
                                _roomMessages.emit(roomName.lowercase() to ChatMessage(
                                    room = roomName.lowercase(),
                                    username = username,
                                    text = body,
                                    time = time,
                                    type = msgType,
                                    eventType = "room.message.received"
                                ))
                            }
                        }
                        "room.joined" -> {
                            val roomName = jsonObject.get("room")?.jsonPrimitive?.contentOrNull
                            if (roomName != null) {
                                val normalizedRoom = roomName.lowercase()
                                if (!_activeRooms.value.contains(normalizedRoom)) {
                                    _activeRooms.value = _activeRooms.value + normalizedRoom
                                }
                                
                                val description = jsonObject.get("room_description")?.jsonPrimitive?.contentOrNull ?: ""
                                val owner = jsonObject.get("room_owner_username")?.jsonPrimitive?.contentOrNull ?: ""
                                val announcement = jsonObject.get("room_announcement")?.jsonPrimitive?.contentOrNull ?: ""
                                val participantsArray = jsonObject.get("participants")?.jsonArray
                                val participantsList = participantsArray?.mapNotNull { 
                                    it.jsonObject["username"]?.jsonPrimitive?.contentOrNull 
                                }?.joinToString(", ") ?: ""
                                
                                val time = jsonObject.get("time")?.jsonPrimitive?.contentOrNull ?: ""
                                
                                repositoryScope.launch {
                                    // 1. Room Description
                                    _roomMessages.emit(normalizedRoom to ChatMessage(
                                        room = roomName.uppercase(),
                                        username = "",
                                        text = description,
                                        time = time,
                                        type = MessageType.PRESENCE,
                                        eventType = "room.joined"
                                    ))
                                    // 2. Managed by
                                    _roomMessages.emit(normalizedRoom to ChatMessage(
                                        room = roomName.uppercase(),
                                        username = "managed by",
                                        text = owner,
                                        time = time,
                                        type = MessageType.PRESENCE,
                                        eventType = "room.joined"
                                    ))
                                    // 3. Currently in room
                                    _roomMessages.emit(normalizedRoom to ChatMessage(
                                        room = roomName.uppercase(),
                                        username = "Currently in the room:",
                                        text = participantsList,
                                        time = time,
                                        type = MessageType.PRESENCE,
                                        eventType = "room.joined"
                                    ))
                                    // 4. Announcement
                                    if (announcement.isNotEmpty()) {
                                        _roomMessages.emit(normalizedRoom to ChatMessage(
                                            room = roomName.uppercase(),
                                            username = "",
                                            text = "<< $announcement >>",
                                            time = time,
                                            type = MessageType.PRESENCE,
                                            eventType = "room.joined"
                                        ))
                                    }
                                }
                            }
                        }
                        "room.participant.added" -> {
                            val roomName = jsonObject?.get("room")?.jsonPrimitive?.contentOrNull ?: ""
                            val username = jsonObject?.get("username")?.jsonPrimitive?.contentOrNull ?: ""
                            repositoryScope.launch {
                                _roomMessages.emit(roomName.lowercase() to ChatMessage(
                                    room = roomName.lowercase(),
                                    username = username,
                                    text = "has entered",
                                    time = "",
                                    type = MessageType.PRESENCE,
                                    eventType = "room.joined"
                                ))
                            }
                        }
                        "room.participant.removed" -> {
                            val roomName = jsonObject?.get("room")?.jsonPrimitive?.contentOrNull ?: ""
                            val username = jsonObject?.get("username")?.jsonPrimitive?.contentOrNull ?: ""
                            repositoryScope.launch {
                                _roomMessages.emit(roomName.lowercase() to ChatMessage(
                                    room = roomName.lowercase(),
                                    username = username,
                                    text = "has left",
                                    time = "",
                                    type = MessageType.PRESENCE,
                                    eventType = "room.left"
                                ))
                            }
                        }
                        "wallet.updated" -> {
                            val username = jsonObject?.get("username")?.jsonPrimitive?.contentOrNull
                            val balance = jsonObject?.get("wallet_balance_milli_cr")?.jsonPrimitive?.longOrNull ?: 0L
                            
                            // Only update if it matches the session's username
                            if (username != null && username == sessionUsernames[connectionType]) {
                                updateWalletState(balance, connectionType)
                            }
                        }
                        "room.left" -> {
                            val roomName = jsonObject.get("room")?.jsonPrimitive?.contentOrNull
                            val username = jsonObject.get("username")?.jsonPrimitive?.contentOrNull
                            
                            if (roomName != null) {
                                val normalizedRoom = roomName.lowercase()
                                // If the one who left is the session user, remove room from active list
                                if (username != null && username == sessionUsernames[connectionType]) {
                                    _activeRooms.value = _activeRooms.value - normalizedRoom
                                } else {
                                    // Someone else left, show presence message
                                    repositoryScope.launch {
                                        _roomMessages.emit(normalizedRoom to ChatMessage(
                                            room = roomName.uppercase(),
                                            username = username ?: "",
                                            text = "has left",
                                            time = jsonObject.get("time")?.jsonPrimitive?.contentOrNull ?: "",
                                            type = MessageType.PRESENCE,
                                            eventType = "room.left"
                                        ))
                                    }
                                }
                            }
                        }
                        "error" -> {
                            val code = jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
                            if (code == "auth_required" || code == "session_expired") {
                                stopPingScheduler(connectionType)
                                stateFlow.value = ConnectionState.Disconnected
                            }
                        }
                    }
                }

                override fun onError(error: String) {
                    stopPingScheduler(connectionType)
                    webSocketClients.remove(connectionType)
                    packetIds.remove(connectionType)
                    sessionUsernames.remove(connectionType)
                    stateFlow.value = ConnectionState.Error(error)
                }

                override fun onClosed(reason: String) {
                    stopPingScheduler(connectionType)
                    webSocketClients.remove(connectionType)
                    packetIds.remove(connectionType)
                    sessionUsernames.remove(connectionType)
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
                delay(60_000) // 60 seconds ping interval
                webSocketClients[connectionType]?.send(json.encodeToString(PingRequest()))
            }
        }
        pingJobs[connectionType] = job
    }

    private fun stopPingScheduler(connectionType: String) {
        pingJobs[connectionType]?.cancel()
        pingJobs.remove(connectionType)
    }

    fun joinRoom(room: String, connectionType: String) {
        val req = JoinRoomRequest(id = nextId(connectionType), room = room.lowercase())
        webSocketClients[connectionType]?.send(json.encodeToString(req))
    }

    fun leaveRoom(room: String, connectionType: String) {
        val req = LeaveRoomRequest(id = nextId(connectionType), room = room.lowercase())
        webSocketClients[connectionType]?.send(json.encodeToString(req))
    }

    fun sendMessage(room: String, message: String, connectionType: String) {
        message.chunked(255).forEach { chunk ->
            val req = SendMessageRequest(id = nextId(connectionType), room = room.lowercase(), body = chunk)
            webSocketClients[connectionType]?.send(json.encodeToString(req))
        }
    }

    fun disconnectSession(connectionType: String) {
        stopPingScheduler(connectionType)
        webSocketClients[connectionType]?.disconnect()
        webSocketClients.remove(connectionType)
        packetIds.remove(connectionType)
        sessionUsernames.remove(connectionType)
        
        val stateFlow = if (connectionType == "REFEREE") _refereeConnectionState else _starterConnectionState
        stateFlow.value = ConnectionState.Disconnected
    }
}
