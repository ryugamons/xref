package id.xterm.xref.core.websocket

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*

class WebSocketClient(
    private val url: String,
    private val json: Json,
    private val client: OkHttpClient,
    private val listener: WebSocketListener
) {
    private var webSocket: WebSocket? = null
    private val clientScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun connect() {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                clientScope.launch {
                    val jsonObject = try {
                        json.decodeFromString<JsonObject>(text)
                    } catch (e: Exception) {
                        null
                    }
                    listener.onMessage(text, jsonObject)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onError(t.message ?: "Unknown error")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed(reason)
            }
        })
    }

    fun send(plaintext: String): Boolean {
        return webSocket?.send(plaintext) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "User logout")
        clientScope.cancel()
    }

    interface WebSocketListener {
        fun onOpen()
        fun onMessage(raw: String, json: JsonObject?)
        fun onError(error: String)
        fun onClosed(reason: String)
    }
}
