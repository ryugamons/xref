package id.xterm.xref.data.remote

import id.xterm.xref.core.websocket.LoginApiRequest
import id.xterm.xref.core.websocket.LoginApiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginApiRequest): Response<LoginApiResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: Map<String, String>): Response<LoginApiResponse>

    @retrofit2.http.GET("me/wallet")
    suspend fun getWalletHistory(
        @retrofit2.http.Header("Authorization") token: String,
        @retrofit2.http.Query("limit") limit: Int = 30,
        @retrofit2.http.Query("offset") offset: Int = 0
    ): Response<id.xterm.xref.core.websocket.WalletHistoryResponse>

    @POST("me/wallet/transfer")
    suspend fun transfer(
        @retrofit2.http.Header("Authorization") token: String,
        @Body request: id.xterm.xref.core.websocket.TransferRequest
    ): Response<id.xterm.xref.core.websocket.TransferResponse>

    @POST("media/uploads")
    suspend fun uploadPhoto(
        @retrofit2.http.Header("Authorization") token: String,
        @retrofit2.http.Header("User-Agent") userAgent: String,
        @Body request: id.xterm.xref.core.websocket.PhotoUploadRequest
    ): Response<id.xterm.xref.core.websocket.PhotoUploadResponse>
}
