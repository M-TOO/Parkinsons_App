package com.example.android.data.model

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val full_name: String,
    val email: String,
    val password: String,
    val year_of_birth: Int
)

data class UserResponse(
    val id: Int,
    val full_name: String,
    val email: String,
    val year_of_birth: Int
)

data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val user: UserResponse
)

data class SessionOut(
    val session_id: String,
    val overall_stability_score: Double?,
    val session_status: String?,
    val risk_label: String?,
    val tapping_stability_match: Double?,
    val tapping_status: String?,
    val drawing_tremor_level: String?,
    val drawing_status: String?,
    val created_at: String
)

data class TypingExcerptRequest(
    val excerpts: List<List<List<Long>>>   // excerpts[i] = list of [press_ms, release_ms]
)

object SessionState {
    var currentSessionId: String = java.util.UUID.randomUUID().toString()
    fun startNewSession(): String {
        currentSessionId = java.util.UUID.randomUUID().toString()
        return currentSessionId
    }
}
