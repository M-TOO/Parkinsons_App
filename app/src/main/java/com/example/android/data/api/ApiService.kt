package com.example.android.data.api

import com.example.android.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("register")
    suspend fun register(@Body body: RegisterRequest): Response<TokenResponse>

    @POST("login")
    suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @POST("sessions/{session_id}/typing")
    suspend fun submitTyping(
        @Path("session_id") sessionId: String,
        @Body body: TypingExcerptRequest
    ): Response<SessionOut>

    @Multipart
    @POST("sessions/{session_id}/drawing/{task}")
    suspend fun submitDrawing(
        @Path("session_id") sessionId: String,
        @Path("task") task: String,
        @Part image: MultipartBody.Part
    ): Response<SessionOut>

    @GET("sessions")
    suspend fun getSessions(): Response<List<SessionOut>>
}
