package id.xterm.xref.core.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BaseMessage(
    val type: String
)

@Serializable
data class LoginRequest(
    val type: String = "developer.login",
    val username: String,
    val password: String
)

@Serializable
data class JoinRoomRequest(
    val type: String = "room.join",
    val room: String
)

@Serializable
data class SessionReadyData(
    val wallet: WalletData? = null
)

@Serializable
data class WalletData(
    @SerialName("balance_milli_cr")
    val balanceMilliCr: Long
)

@Serializable
data class WebSocketResponse(
    val type: String,
    val data: kotlinx.serialization.json.JsonObject? = null,
    val status: String? = null,
    val message: String? = null
)

@Serializable
data class KickData(
    val from: String,
    val target: String,
    @SerialName("room_id")
    val roomId: String? = null
)

@Serializable
data class KickEvent(
    val type: String = "room.kick",
    val data: KickData
)
