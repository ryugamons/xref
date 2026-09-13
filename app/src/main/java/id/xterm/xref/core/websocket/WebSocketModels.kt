package id.xterm.xref.core.websocket

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** REST API MODELS **/

@JsonClass(generateAdapter = true)
@Serializable
data class LoginApiRequest(
    val username: String,
    val password: String
)

@JsonClass(generateAdapter = true)
@Serializable
data class LoginApiResponse(
    @Json(name = "access_token")
    @SerialName("access_token")
    val accessToken: String,
    @Json(name = "refresh_token")
    @SerialName("refresh_token")
    val refreshToken: String,
    val user: UserData? = null
)

@JsonClass(generateAdapter = true)
@Serializable
data class UserData(
    val wallet: WalletData? = null
)

/** WEBSOCKET REQUEST MODELS **/

@Serializable
data class BaseRequest(
    val id: String,
    val type: String
)

@Serializable
data class JoinRoomRequest(
    val id: String,
    val type: String = "room.join",
    val room: String
)

@Serializable
data class LeaveRoomRequest(
    val id: String,
    val type: String = "room.leave",
    val room: String
)

@Serializable
data class SendMessageRequest(
    val id: String,
    val type: String = "room.message.send",
    val room: String,
    val body: String
)

@Serializable
data class KickUserRequest(
    val id: String,
    val type: String = "room.kick",
    val room: String,
    val target: String
)

@Serializable
data class PingRequest(
    val type: String = "connection.ping"
)

/** WEBSOCKET RESPONSE/EVENT MODELS **/

@JsonClass(generateAdapter = true)
@Serializable
data class WalletData(
    @Json(name = "balance_milli_cr")
    @SerialName("balance_milli_cr")
    val balanceMilliCr: Long
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
