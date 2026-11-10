package com.example.android.data.repository

import com.example.android.data.api.ApiClient
import com.example.android.data.model.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class AssessmentRepository {
    private val api = ApiClient.service

    suspend fun register(fullName: String, email: String, password: String, yearOfBirth: Int): Result<TokenResponse> {
        return try {
            val response = api.register(RegisterRequest(fullName, email, password, yearOfBirth))
            if (response.isSuccessful && response.body() != null) {
                val tokenResp = response.body()!!
                ApiClient.setToken(tokenResp.access_token)
                Result.success(tokenResp)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Registration failed (${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<TokenResponse> {
        return try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val tokenResp = response.body()!!
                ApiClient.setToken(tokenResp.access_token)
                Result.success(tokenResp)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Login failed (${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitDrawing(sessionId: String, task: String, imageBytes: ByteArray): Result<SessionOut> {
        return try {
            val requestFile = imageBytes.toRequestBody("image/png".toMediaTypeOrNull(), 0, imageBytes.size)
            val body = MultipartBody.Part.createFormData("image", "$task.png", requestFile)
            val response = api.submitDrawing(sessionId, task, body)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Drawing upload failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitTyping(sessionId: String, excerpts: List<List<List<Long>>>): Result<SessionOut> {
        return try {
            val response = api.submitTyping(sessionId, TypingExcerptRequest(excerpts))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Typing submission failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessions(): Result<List<SessionOut>> {
        return try {
            val response = api.getSessions()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to fetch sessions"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
