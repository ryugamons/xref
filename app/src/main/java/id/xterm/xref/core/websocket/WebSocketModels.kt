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

@JsonClass(generateAdapter = true)
@Serializable
data class WalletHistoryResponse(
    @Json(name = "has_more") val hasMore: Boolean,
    val limit: Int,
    @Json(name = "next_offset") val nextOffset: Int,
    val offset: Int,
    val status: String,
    val transactions: List<TransactionData>,
    val wallet: WalletData? = null
)

@JsonClass(generateAdapter = true)
@Serializable
data class TransactionData(
    val id: Long,
    @Json(name = "user_id") val userId: String,
    @Json(name = "counterparty_user_id") val counterpartyUserId: String? = null,
    val type: String,
    @Json(name = "amount_milli_cr") val amountMilliCr: Long,
    @Json(name = "balance_after_milli_cr") val balanceAfterMilliCr: Long,
    @Json(name = "reference_id") val referenceId: String,
    val note: String,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
@Serializable
data class TransferRequest(
    @Json(name = "to_username") val toUsername: String,
    @Json(name = "amount_milli_cr") val amountMilliCr: Long,
    val pin: String,
    @Json(name = "idempotency_key") val idempotencyKey: String? = null
)

@JsonClass(generateAdapter = true)
@Serializable
data class TransferResponse(
    val status: String,
    val transaction: TransactionData? = null
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
