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
}
