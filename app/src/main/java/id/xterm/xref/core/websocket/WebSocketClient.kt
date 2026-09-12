package id.xterm.xref.core.websocket

import android.util.Log
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

    companion object {
        private const val TAG = "XREF_WS"
    }

    fun connect() {
        Log.d(TAG, "Connecting to URL: $url")
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connection successfully opened")
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "RECEIVE >>> $text")
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
                Log.e(TAG, "Connection failure error: ${t.message}", t)
                listener.onError(t.message ?: "Unknown error")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Connection closing code: $code, reason: $reason")
                listener.onClosed(reason)
            }
        })
    }

    fun send(plaintext: String): Boolean {
        Log.d(TAG, "SEND    <<< $plaintext")
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
