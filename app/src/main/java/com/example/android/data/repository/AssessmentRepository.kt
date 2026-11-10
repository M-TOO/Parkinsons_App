package com.example.android.data.repository

import com.example.android.data.api.ApiClient
import com.example.android.data.model.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class AssessmentRepository {
    private val api = ApiClient.service

    suspend fun register(fullName: String, email: String, password: String, yearOfBirth: Int, role: String = "patient"): Result<TokenResponse> {
        return try {
            val response = api.register(RegisterRequest(fullName, email, password, yearOfBirth, role))
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

    // ── Clinician Portal Methods (M-TOO) ──────────────────────────────────
    suspend fun getAdminStats(): Result<AdminDashboardStats> {
        return try {
            val response = api.getAdminStats()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to load dashboard metrics"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAdminPatients(): Result<List<PatientSummary>> {
        return try {
            val response = api.getAdminPatients()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to load patient roster"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFlaggedSessions(): Result<List<FlaggedSession>> {
        return try {
            val response = api.getFlaggedSessions()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to load triage queue"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitClinicalReview(sessionId: String, notes: String, status: String, overrideRiskLabel: String? = null): Result<SessionOut> {
        return try {
            val response = api.submitClinicalReview(sessionId, ClinicalReviewRequest(notes, status, overrideRiskLabel))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to submit clinical review"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
