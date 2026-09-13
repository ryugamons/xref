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
data class LeaveRoomRequest(
    val type: String = "room.leave",
    val room: String
)

@Serializable
data class SendMessageRequest(
    val type: String = "room.send_message",
    val room: String,
    val message: String
)

@Serializable
data class SessionReadyData(
    val developer: DeveloperData? = null,
    val wallet: WalletData? = null
)

@Serializable
data class DeveloperData(
    @SerialName("developer_id")
    val developerId: String,
    val username: String,
    val wallet: WalletData? = null
)

@Serializable
data class WalletData(
    @SerialName("balance_milli_cr")
    val balanceMilliCr: Long,
    @SerialName("balance_cr")
    val balanceCr: String? = null
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

@Serializable
data class RoomTextData(
    val room: String,
    @SerialName("event_type")
    val eventType: String? = null,
    val username: String? = null,
    val text: String? = null,
    @SerialName("message_kind")
    val messageKind: String? = null,
    val time: String? = null
)

@Serializable
data class RoomTextEvent(
    val type: String,
    val data: RoomTextData
)

data class ChatMessage(
    val room: String,
    val username: String,
    val text: String,
    val time: String,
    val type: MessageType,
    val eventType: String? = null
)

enum class MessageType {
    TEXT, ACTION, PRESENCE, SYSTEM
}
