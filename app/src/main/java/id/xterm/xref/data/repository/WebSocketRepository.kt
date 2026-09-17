package id.xterm.xref.data.repository

import android.util.Log
import id.xterm.xref.core.websocket.*
import id.xterm.xref.data.remote.AuthService
import id.xterm.xref.data.storage.AuthPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
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
    private val tokenRefreshJobs = ConcurrentHashMap<String, Job>()
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

    private val incomingChannel = Channel<Pair<String, JsonObject>>(Channel.UNLIMITED)

    private val eventSubscribers = java.util.concurrent.CopyOnWriteArrayList<Channel<JsonObject>>()
    private val messageSubscribers = java.util.concurrent.CopyOnWriteArrayList<Channel<Pair<String, ChatMessage>>>()

    fun subscribeEvents(): Flow<JsonObject> {
        val channel = Channel<JsonObject>(Channel.UNLIMITED)
        eventSubscribers.add(channel)
        return channel.receiveAsFlow()
    }

    fun subscribeRoomMessages(): Flow<Pair<String, ChatMessage>> {
        val channel = Channel<Pair<String, ChatMessage>>(Channel.UNLIMITED)
        messageSubscribers.add(channel)
        return channel.receiveAsFlow()
    }

    private val _activeRooms = MutableStateFlow<Set<String>>(emptySet())
    val activeRooms = _activeRooms.asStateFlow()

    private val _roomParticipants = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val roomParticipants = _roomParticipants.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private data class OutgoingMessage(val connectionType: String, val rawMessage: String)
    private val outgoingChannel = Channel<OutgoingMessage>(Channel.UNLIMITED)

    init {
        repositoryScope.launch {
            for ((connectionType, jsonObject) in incomingChannel) {
                processIncomingMessage(connectionType, jsonObject)
            }
        }

        repositoryScope.launch {
            for (msg in outgoingChannel) {
                webSocketClients[msg.connectionType]?.send(msg.rawMessage)
                delay(400) // Rate limit for normal text as per production server rules
            }
        }
    }

    private suspend fun processIncomingMessage(connectionType: String, jsonObject: JsonObject) {
        eventSubscribers.forEach { it.trySend(jsonObject) }
        
        val type = jsonObject.get("type")?.jsonPrimitive?.contentOrNull
        when (type) {
            "room.message.received" -> {
                val roomName = jsonObject.get("room")?.jsonPrimitive?.contentOrNull ?: ""
                val username = jsonObject.get("username")?.jsonPrimitive?.contentOrNull ?: "system"
                val body = jsonObject.get("body")?.jsonPrimitive?.contentOrNull ?: ""
                val time = jsonObject.get("time")?.jsonPrimitive?.contentOrNull ?: ""
                val kind = jsonObject.get("message_kind")?.jsonPrimitive?.contentOrNull ?: "text"
                
                val isAction = kind == "action" || (body.startsWith("**") && body.endsWith("**"))
                val msgType = if (isAction) MessageType.ACTION else MessageType.TEXT
                
                if (connectionType == "REFEREE") {
                    val chatMsg = ChatMessage(
                        room = roomName.uppercase(),
                        username = username,
                        text = body,
                        time = time,
                        type = msgType,
                        eventType = type
                    )
                    messageSubscribers.forEach { it.trySend(roomName.lowercase() to chatMsg) }
                }
            }
            "room.joined" -> {
                val roomName = jsonObject.get("room")?.jsonPrimitive?.contentOrNull
                val usernameInPacket = jsonObject.get("username")?.jsonPrimitive?.contentOrNull
                val time = jsonObject.get("time")?.jsonPrimitive?.contentOrNull ?: ""
                
                if (roomName != null && usernameInPacket != null && connectionType == "REFEREE") {
                    val normalizedRoomKey = roomName.lowercase()
                    val displayRoom = roomName.uppercase()
                    
                    _activeRooms.update { it + normalizedRoomKey }
                    
                    if (usernameInPacket == sessionUsernames[connectionType]) {
                        val participantsArray = jsonObject.get("participants")?.jsonArray
                        val initialUsernames = participantsArray?.mapNotNull { 
                            it.jsonObject["username"]?.jsonPrimitive?.contentOrNull 
                        } ?: emptyList()
                        
                        _roomParticipants.update { it.toMutableMap().apply { this[normalizedRoomKey] = initialUsernames } }

                        val description = jsonObject.get("room_description")?.jsonPrimitive?.contentOrNull ?: ""
                        val owner = jsonObject.get("room_owner_username")?.jsonPrimitive?.contentOrNull ?: ""
                        val announcement = jsonObject.get("room_announcement")?.jsonPrimitive?.contentOrNull ?: ""
                        
                        val presenceMsgs = mutableListOf<ChatMessage>()
                        presenceMsgs.add(ChatMessage(room = displayRoom, username = "", text = description, time = time, type = MessageType.PRESENCE, eventType = type))
                        presenceMsgs.add(ChatMessage(room = displayRoom, username = "managed by", text = owner, time = time, type = MessageType.PRESENCE, eventType = type))
                        if (announcement.isNotEmpty()) {
                            presenceMsgs.add(ChatMessage(room = "", username = "", text = "<< $announcement >>", time = time, type = MessageType.PRESENCE, eventType = type))
                        }
                        presenceMsgs.forEach { msg -> messageSubscribers.forEach { it.trySend(normalizedRoomKey to msg) } }
                    } else {
                        // Also update participant list for others joining via room.joined
                        _roomParticipants.update { currentMap ->
                            currentMap.toMutableMap().apply {
                                val list = this[normalizedRoomKey]?.toMutableList() ?: mutableListOf()
                                if (!list.contains(usernameInPacket)) {
                                    list.add(usernameInPacket)
                                    this[normalizedRoomKey] = list
                                }
                            }
                        }
                        val enterMsg = ChatMessage(room = displayRoom, username = usernameInPacket, text = "has entered", time = time, type = MessageType.PRESENCE, eventType = type)
                        messageSubscribers.forEach { it.trySend(normalizedRoomKey to enterMsg) }
                    }
                }
            }
            "room.participant.added" -> {
                val roomName = jsonObject.get("room")?.jsonPrimitive?.contentOrNull
                val username = jsonObject.get("username")?.jsonPrimitive?.contentOrNull
                if (roomName != null && username != null && connectionType == "REFEREE") {
                    val normalizedRoomKey = roomName.lowercase()
                    _roomParticipants.update { currentMap ->
                        currentMap.toMutableMap().apply {
                            val list = this[normalizedRoomKey]?.toMutableList() ?: mutableListOf()
                            if (!list.contains(username)) {
                                list.add(username)
                                this[normalizedRoomKey] = list
                            }
                        }
                    }
                }
            }
            "room.participant.removed" -> {
                val roomName = jsonObject.get("room")?.jsonPrimitive?.contentOrNull
                val username = jsonObject.get("username")?.jsonPrimitive?.contentOrNull
                if (roomName != null && username != null && connectionType == "REFEREE") {
                    val normalizedRoomKey = roomName.lowercase()
                    _roomParticipants.update { currentMap ->
                        currentMap.toMutableMap().apply {
                            val list = this[normalizedRoomKey]?.toMutableList() ?: mutableListOf()
                            if (list.remove(username)) {
                                this[normalizedRoomKey] = list
                            }
                        }
                    }
                }
            }
            "room.left" -> {
                val roomName = jsonObject.get("room")?.jsonPrimitive?.contentOrNull
                val usernameInPacket = jsonObject.get("username")?.jsonPrimitive?.contentOrNull
                val time = jsonObject.get("time")?.jsonPrimitive?.contentOrNull ?: ""

                if (roomName != null && usernameInPacket != null && connectionType == "REFEREE") {
                    val normalizedRoomKey = roomName.lowercase()
                    if (usernameInPacket == sessionUsernames[connectionType]) {
                        _activeRooms.update { it - normalizedRoomKey }
                        _roomParticipants.update { it.toMutableMap().apply { remove(normalizedRoomKey) } }
                    } else {
                        val leftMsg = ChatMessage(room = roomName.uppercase(), username = usernameInPacket, text = "has left", time = time, type = MessageType.PRESENCE, eventType = type)
                        messageSubscribers.forEach { it.trySend(normalizedRoomKey to leftMsg) }
                    }
                }
            }
            "wallet.updated" -> {
                val balance = jsonObject.get("wallet_balance_milli_cr")?.jsonPrimitive?.longOrNull ?: 0L
                updateWalletState(balance, connectionType)
            }
            "error" -> {
                val code = jsonObject.get("code")?.jsonPrimitive?.contentOrNull
                if (code == "auth_required" || code == "session_expired") {
                    stopPingScheduler(connectionType)
                    val stateFlow = if (connectionType == "REFEREE") _refereeConnectionState else _starterConnectionState
                    stateFlow.value = ConnectionState.Disconnected
                }
            }
        }
    }

    private fun enqueueMessage(connectionType: String, rawMessage: String) {
        repositoryScope.launch {
            outgoingChannel.send(OutgoingMessage(connectionType, rawMessage))
        }
    }

    private fun nextId(connectionType: String): String {
        val counter = packetIds.getOrPut(connectionType) { AtomicInteger(0) }
        return counter.incrementAndGet().toString()
    }

    private fun updateWalletState(balanceMilliCr: Long, connectionType: String) {
        val balanceText = String.format(java.util.Locale.US, "%.2f CR", balanceMilliCr / 1000.0)
        if (connectionType == "REFEREE") _refereeWalletBalance.value = balanceText
        else _starterWalletBalance.value = balanceText
    }

    suspend fun loginAndConnect(username: String, password: String, connectionType: String) {
        val stateFlow = if (connectionType == "REFEREE") _refereeConnectionState else _starterConnectionState
        stateFlow.value = ConnectionState.Connecting
        try {
            val response = authService.login(LoginApiRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val authData = response.body()!!
                sessionUsernames[connectionType] = username
                AuthPreferences.saveTokens(authData.accessToken, authData.refreshToken, connectionType)
                authData.user?.wallet?.balanceMilliCr?.let { balance -> updateWalletState(balance, connectionType) }
                openWebSocket(authData.accessToken, connectionType)
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = try {
                    val errorObj = json.decodeFromString<JsonObject>(errorBody ?: "{}")
                    errorObj["message"]?.jsonPrimitive?.content ?: "Login failed"
                } catch (e: Exception) { "Login failed" }
                stateFlow.value = ConnectionState.Error(errorMsg)
            }
        } catch (e: Exception) { stateFlow.value = ConnectionState.Error(e.message ?: "Network error") }
    }

    suspend fun getWalletHistory(connectionType: String): WalletHistoryResponse? {
        val token = AuthPreferences.getAccessToken(connectionType)
        if (token.isEmpty()) return null
        try {
            val response = authService.getWalletHistory("Bearer $token")
            if (response.isSuccessful) return response.body()
            if (response.code() == 401 && refreshTokens(connectionType)) {
                val newToken = AuthPreferences.getAccessToken(connectionType)
                val retryResponse = authService.getWalletHistory("Bearer $newToken")
                if (retryResponse.isSuccessful) return retryResponse.body()
            }
        } catch (e: Exception) { Log.e("XREF_AUTH", "Failed to fetch wallet history: ${e.message}") }
        return null
    }

    suspend fun sendTransfer(target: String, milliCr: Long, pin: String, connectionType: String): Boolean {
        val token = AuthPreferences.getAccessToken(connectionType)
        if (token.isEmpty()) return false
        val request = TransferRequest(toUsername = target, amountMilliCr = milliCr, pin = pin, idempotencyKey = "wallet-transfer-${java.util.UUID.randomUUID()}")
        try {
            val response = authService.transfer("Bearer $token", request)
            if (response.isSuccessful) return true
            if (response.code() == 401 && refreshTokens(connectionType)) {
                val newToken = AuthPreferences.getAccessToken(connectionType)
                val retryResponse = authService.transfer("Bearer $newToken", request)
                return retryResponse.isSuccessful
            }
        } catch (e: Exception) { Log.e("XREF_AUTH", "Transfer failed: ${e.message}") }
        return false
    }

    suspend fun uploadPhoto(filename: String, base64Data: String, contextId: String, connectionType: String): PhotoUploadResponse? {
        val token = AuthPreferences.getAccessToken(connectionType)
        if (token.isEmpty()) return null
        val request = PhotoUploadRequest(
            filename = filename,
            mimeType = "image/jpeg",
            dataBase64 = base64Data,
            contextId = contextId
        )
        try {
            val ua = "mig33-reborn-native-android"
            val response = authService.uploadPhoto("Bearer $token", ua, request)
            if (response.isSuccessful) return response.body()
            if (response.code() == 401 && refreshTokens(connectionType)) {
                val newToken = AuthPreferences.getAccessToken(connectionType)
                val retryResponse = authService.uploadPhoto("Bearer $newToken", ua, request)
                if (retryResponse.isSuccessful) return retryResponse.body()
            }
        } catch (e: Exception) { Log.e("XREF_AUTH", "Photo upload failed: ${e.message}") }
        return null
    }

    private suspend fun refreshTokens(connectionType: String): Boolean {
        val refreshToken = AuthPreferences.getRefreshToken(connectionType)
        if (refreshToken.isEmpty()) return false
        try {
            val response = authService.refresh(mapOf("refresh_token" to refreshToken))
            if (response.isSuccessful && response.body() != null) {
                val authData = response.body()!!
                AuthPreferences.saveTokens(authData.accessToken, authData.refreshToken, connectionType)
                return true
            }
        } catch (e: Exception) { Log.e("XREF_AUTH", "Token refresh failed: ${e.message}") }
        return false
    }

    private fun openWebSocket(token: String, connectionType: String) {
        val stateFlow = if (connectionType == "REFEREE") _refereeConnectionState else _starterConnectionState
        val url = "wss://api.mig33.id/ws?token=$token"
        val webSocketClient = WebSocketClient(url = url, json = json, client = okHttpClient, listener = object : WebSocketClient.WebSocketListener {
            override fun onOpen() {
                stateFlow.value = ConnectionState.Connected
                startPingScheduler(connectionType)
                startTokenRefreshScheduler(connectionType)
            }
            override fun onMessage(raw: String, jsonObject: JsonObject?) {
                if (jsonObject != null) { incomingChannel.trySend(connectionType to jsonObject) }
            }
            override fun onError(error: String) {
                stopPingScheduler(connectionType)
                stopTokenRefreshScheduler(connectionType)
                webSocketClients.remove(connectionType)
                packetIds.remove(connectionType)
                sessionUsernames.remove(connectionType)
                stateFlow.value = ConnectionState.Error(error)
            }
            override fun onClosed(reason: String) {
                stopPingScheduler(connectionType)
                stopTokenRefreshScheduler(connectionType)
                webSocketClients.remove(connectionType)
                packetIds.remove(connectionType)
                sessionUsernames.remove(connectionType)
                stateFlow.value = ConnectionState.Disconnected
            }
        })
        webSocketClients[connectionType] = webSocketClient
        webSocketClient.connect()
    }

    private fun startPingScheduler(connectionType: String) {
        stopPingScheduler(connectionType)
        val job = repositoryScope.launch {
            while (isActive) {
                delay(60_000) 
                enqueueMessage(connectionType, json.encodeToString(PingRequest()))
            }
        }
        pingJobs[connectionType] = job
    }

    private fun stopPingScheduler(connectionType: String) {
        pingJobs[connectionType]?.cancel()
        pingJobs.remove(connectionType)
    }

    private fun startTokenRefreshScheduler(connectionType: String) {
        stopTokenRefreshScheduler(connectionType)
        val job = repositoryScope.launch {
            while (isActive) {
                delay(800_000) // Refresh every 800 seconds
                Log.d("XREF_WS", "Scheduled token refresh for $connectionType")
                refreshTokens(connectionType)
            }
        }
        tokenRefreshJobs[connectionType] = job
    }

    private fun stopTokenRefreshScheduler(connectionType: String) {
        tokenRefreshJobs[connectionType]?.cancel()
        tokenRefreshJobs.remove(connectionType)
    }

    fun joinRoom(room: String, connectionType: String) {
        val req = JoinRoomRequest(id = nextId(connectionType), room = room.lowercase())
        enqueueMessage(connectionType, json.encodeToString(req))
    }

    fun leaveRoom(room: String, connectionType: String) {
        val req = LeaveRoomRequest(id = nextId(connectionType), room = room.lowercase())
        enqueueMessage(connectionType, json.encodeToString(req))
    }

    fun sendMessage(room: String, message: String, connectionType: String) {
        message.chunked(255).forEach { chunk ->
            val req = SendMessageRequest(id = nextId(connectionType), room = room.lowercase(), body = chunk)
            enqueueMessage(connectionType, json.encodeToString(req))
        }
    }

    fun sendImageMessage(room: String, mediaUrl: String, mimeType: String, sizeBytes: Long, connectionType: String) {
        val req = SendMessageRequest(
            id = nextId(connectionType),
            room = room.lowercase(),
            mediaUrl = mediaUrl,
            mediaMimeType = mimeType,
            mediaSizeBytes = sizeBytes,
            clientMessageId = "client-image-upload-${System.currentTimeMillis()}-${(1000..9999).random()}"
        )
        enqueueMessage(connectionType, json.encodeToString(req))
    }

    fun sendDirect(room: String, message: String, connectionType: String = "REFEREE") {
        val req = SendMessageRequest(id = nextId(connectionType), room = room.lowercase(), body = message)
        val raw = json.encodeToString(req)
        webSocketClients[connectionType]?.send(raw)
    }

    fun disconnectSession(connectionType: String) {
        stopPingScheduler(connectionType)
        stopTokenRefreshScheduler(connectionType)
        webSocketClients[connectionType]?.disconnect()
        webSocketClients.remove(connectionType)
        packetIds.remove(connectionType)
        sessionUsernames.remove(connectionType)
        
        if (connectionType == "REFEREE") _refereeWalletBalance.value = "0.00 CR"
        else _starterWalletBalance.value = "0.00 CR"

        val stateFlow = if (connectionType == "REFEREE") _refereeConnectionState else _starterConnectionState
        stateFlow.value = ConnectionState.Disconnected
    }
}
