package com.example.pdscreen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

@Serializable data class RegisterBody(
    @SerialName("full_name") val fullName: String, val email: String,
    val password: String, @SerialName("year_of_birth") val yearOfBirth: Int)
@Serializable data class LoginBody(val email: String, val password: String)
@Serializable data class UserDto(val id: Int, @SerialName("full_name") val fullName: String, val email: String)
@Serializable data class TokenDto(@SerialName("access_token") val accessToken: String, val user: UserDto)

/** excerpts[i] = list of [pressMs, releaseMs] pairs; use KeyEventRecorder.finishExcerpt() */
@Serializable data class TypingBody(val excerpts: List<List<List<Long>>>)
@Serializable data class MotorBody(
    @SerialName("reaction_speed_pct") val reactionSpeedPct: Double? = null,
    @SerialName("spatial_precision_pct") val spatialPrecisionPct: Double? = null,
    @SerialName("coordination_status") val coordinationStatus: String? = null,
    @SerialName("modules_completed") val modulesCompleted: Int? = null)

@Serializable data class SessionDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("overall_stability_score") val overallStabilityScore: Double? = null,
    @SerialName("risk_label") val riskLabel: String? = null,
    @SerialName("tapping_stability_match") val tappingStabilityMatch: Double? = null,
    @SerialName("tapping_status") val tappingStatus: String? = null,
    @SerialName("drawing_tremor_level") val drawingTremorLevel: String? = null,
    @SerialName("drawing_status") val drawingStatus: String? = null,
    @SerialName("reaction_speed_pct") val reactionSpeedPct: Double? = null,
    @SerialName("spatial_precision_pct") val spatialPrecisionPct: Double? = null,
    @SerialName("coordination_status") val coordinationStatus: String? = null,
    @SerialName("created_at") val createdAt: String)

interface AssessmentApi {
    @POST("register") suspend fun register(@Body b: RegisterBody): TokenDto
    @POST("login") suspend fun login(@Body b: LoginBody): TokenDto

    // id = UUID.randomUUID().toString(), one per assessment session; reuse it for every call in that session
    @POST("sessions/{id}/typing") suspend fun typing(@Path("id") id: String, @Body b: TypingBody): SessionDto
    @Multipart @POST("sessions/{id}/drawing/{task}")  // task = "spiral" | "wave"
    suspend fun drawing(@Path("id") id: String, @Path("task") task: String, @Part image: MultipartBody.Part): SessionDto
    @PATCH("sessions/{id}/motor") suspend fun motor(@Path("id") id: String, @Body b: MotorBody): SessionDto
    @GET("sessions") suspend fun history(): List<SessionDto>
}

/** baseUrl must end with "/" (Android emulator -> your PC: http://10.0.2.2:8000/). token() returns the saved JWT or null. */
fun createApi(baseUrl: String, token: () -> String?): AssessmentApi {
    val auth = Interceptor { chain ->
        val req = chain.request().newBuilder().apply { token()?.let { header("Authorization", "Bearer $it") } }.build()
        chain.proceed(req)
    }
    val http = OkHttpClient.Builder().addInterceptor(auth).callTimeout(60, TimeUnit.SECONDS).build() // spiral scoring is slow
    return Retrofit.Builder().baseUrl(baseUrl).client(http)
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build().create(AssessmentApi::class.java)
}

fun MultipartBody.Companion.png(bytes: ByteArray) =
    MultipartBody.Part.createFormData("image", "drawing.png", bytes.toRequestBody("image/png".toMediaType()))
